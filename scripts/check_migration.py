"""
Check a Room migration on the PC, before it runs on the phone.

Why: when Detox updates, Room runs our MIGRATION_X_Y on the real database, then compares
the result with the new schema. If they differ, the database refuses to open and the app
crashes at start. Detox is the home screen in strict mode, so that would break the Home button.

How it works (all in memory, nothing touches the phone):
  1. Build an empty database exactly like version FROM (CREATE TABLE lines from app/schemas/FROM.json)
  2. Run the migration SQL (copy it from DetoxDatabase.kt)
  3. Compare every table's columns (name, type, NOT NULL, DEFAULT, primary key) with version TO's json

Usage (from the project folder):
  python scripts/check_migration.py 5 6 "ALTER TABLE settings ADD COLUMN reels_guard_wanted INTEGER NOT NULL DEFAULT 0"
Several statements: separate them with ';'.
"""
import json
import sqlite3
import sys
from pathlib import Path

SCHEMAS = Path(__file__).resolve().parent.parent / "app" / "schemas" / "com.detox.app.data.db.DetoxDatabase"


def load(version: int) -> dict:
    return json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]


def columns(db: sqlite3.Connection, table: str) -> dict:
    # PRAGMA table_info -> (cid, name, type, notnull, dflt_value, pk): the same facts Room checks.
    return {r[1]: (r[2].upper(), r[3], r[4], r[5]) for r in db.execute(f"PRAGMA table_info(`{table}`)")}


def indexes(db: sqlite3.Connection, table: str) -> dict:
    # Room checks indexes too: name -> (unique?, [columns]). Skip SQLite's own automatic ones.
    result = {}
    for _, name, unique, origin, _ in db.execute(f"PRAGMA index_list(`{table}`)"):
        if origin != "c":          # "c" = made by CREATE INDEX; "pk"/"u" = automatic
            continue
        cols = [r[2] for r in db.execute(f"PRAGMA index_info(`{name}`)")]
        result[name] = (unique, cols)
    return result


def main() -> None:
    if len(sys.argv) != 4:
        print(__doc__)
        sys.exit(1)
    old_v, new_v, migration_sql = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]

    # 1) the database as it is on the phone today
    phone = sqlite3.connect(":memory:")
    for entity in load(old_v)["entities"]:
        phone.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

    # 2) the migration, statement by statement
    for statement in filter(None, (s.strip() for s in migration_sql.split(";"))):
        phone.execute(statement)

    # 3) what Room expects after the update: build it from the NEW json and compare
    expected = sqlite3.connect(":memory:")
    new_tables = [e["tableName"] for e in load(new_v)["entities"]]
    for entity in load(new_v)["entities"]:
        expected.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            expected.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))

    ok = True
    for table in new_tables:
        got, want = columns(phone, table), columns(expected, table)
        got_ix, want_ix = indexes(phone, table), indexes(expected, table)
        if got != want or got_ix != want_ix:
            ok = False
            print(f"MISMATCH in {table}:")
            for name in sorted(set(got) | set(want)):
                if got.get(name) != want.get(name):
                    print(f"  {name}: after migration={got.get(name)}  room expects={want.get(name)}")
            for name in sorted(set(got_ix) | set(want_ix)):
                if got_ix.get(name) != want_ix.get(name):
                    print(f"  index {name}: after migration={got_ix.get(name)}  room expects={want_ix.get(name)}")
        else:
            extra = f", {len(got_ix)} index" if got_ix else ""
            print(f"ok  {table} ({len(got)} columns{extra})")

    print("\nMigration matches the new schema." if ok else "\nFIX the migration before installing!")
    sys.exit(0 if ok else 3)


if __name__ == "__main__":
    main()
