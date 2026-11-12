"""
Print every table of the Detox database with plain SQL.

1) From the project root, copy the database from the phone/emulator to the PC
   (debug builds only, Git Bash):
     export MSYS_NO_PATHCONV=1
     mkdir -p scripts/db
     for f in detox.db detox.db-wal detox.db-shm; do
       adb exec-out run-as com.detox.app cat databases/$f > scripts/db/$f
     done
   (-wal = "write-ahead log": the newest changes live there until SQLite merges them
    into detox.db, so we copy it too.)

2) Run:  python scripts/show_db.py scripts/db/detox.db

You can also open the same detox.db file in DBeaver (SQLite connection) and write your own SELECTs.
"""
import sqlite3
import sys


def hhmm(minutes: int) -> str:
    return f"{minutes // 60:02d}:{minutes % 60:02d}"


def main(path: str) -> None:
    con = sqlite3.connect(path)
    cur = con.cursor()

    # PRAGMA user_version = the schema version number Room stores inside the file.
    print("schema version:", cur.execute("PRAGMA user_version").fetchone()[0])

    print("\n== quiet_window ==")
    rows = cur.execute(
        """
        SELECT id, name, kind,
               printf('%02d:%02d', start_min / 60, start_min % 60) AS start,
               printf('%02d:%02d', end_min / 60, end_min % 60)     AS "end",
               days_mask, enabled
        FROM quiet_window
        ORDER BY id
        """
    ).fetchall()
    print(f"{'id':>3} {'name':<12} {'kind':<8} {'start':<6} {'end':<6} {'days_mask':>9} {'enabled':>7}")
    for r in rows:
        print(f"{r[0]:>3} {r[1]:<12} {r[2]:<8} {r[3]:<6} {r[4]:<6} {r[5]:>9} {r[6]:>7}")

    # Which columns does paused_app have? (version 2 added daily_limit_min)
    columns = [c[1] for c in cur.execute("PRAGMA table_info(paused_app)")]
    has_limit = "daily_limit_min" in columns
    print("\n== paused_app ==   columns:", ", ".join(columns))
    query = "SELECT label, package_name" + (", daily_limit_min" if has_limit else "") + \
            " FROM paused_app ORDER BY label COLLATE NOCASE"
    for row in cur.execute(query):
        limit = row[2] if has_limit else None
        limit_text = "no own limit" if limit is None else f"own limit {limit} min"
        print(f"  {row[0]:<12} {row[1]:<30} {limit_text}")

    tables = {t[0] for t in cur.execute("SELECT name FROM sqlite_master WHERE type = 'table'")}
    if "settings" in tables:
        print("\n== settings ==")
        for row in cur.execute("SELECT id, daily_limit_on, daily_limit_min, day_start_min FROM settings"):
            print(f"  id={row[0]}  daily_limit_on={row[1]}  daily_limit_min={row[2]}  day_start={hhmm(row[3])}")
        settings_columns = [c[1] for c in cur.execute("PRAGMA table_info(settings)")]
        if "strict_mode_on" in settings_columns:   # version 3
            for row in cur.execute("SELECT strict_mode_on, countdown_sec FROM settings"):
                print(f"  strict_mode_on={row[0]}  countdown_sec={row[1]}")
        if "protection_wanted" in settings_columns:   # version 4
            for row in cur.execute("SELECT protection_wanted FROM settings"):
                # 1 = you want protection on (Detox may switch it back on); 0 = you switched it off in Detox
                print(f"  protection_wanted={row[0]}")
    else:
        print("\n(no settings table yet - this is a version 1 database)")

    # ---- version 5: strict home screen ----
    if "home_prefs" in tables:
        print("\n== home_prefs ==   (looks of the strict home screen)")
        cur.execute("SELECT * FROM home_prefs")
        names = [d[0] for d in cur.description]   # column names, in table order
        for row in cur.fetchall():
            print("  " + "  ".join(f"{n}={v}" for n, v in zip(names, row)))

    if "app_pref" in tables:
        print("\n== app_pref ==   (favorite_pos NULL = not on page 1)")
        rows = cur.execute(
            """
            SELECT package_name, favorite_pos, hidden, mindful
            FROM app_pref
            ORDER BY favorite_pos IS NULL, favorite_pos, package_name   -- favorites first, in order
            """
        ).fetchall()
        if not rows:
            print("  (empty: no favorites, hidden or mindful apps yet)")
        for pkg, pos, hidden, mindful in rows:
            fav = "-" if pos is None else str(pos)
            print(f"  {pkg:<34} favorite_pos={fav:<2} hidden={hidden} mindful={mindful}")

    con.close()


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "detox.db")
