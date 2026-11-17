"""
READ-ONLY helper: which view ID names does an app show on the phone RIGHT NOW?
Used to find / re-check the markers in app/src/main/java/com/detox/app/domain/ShortsRules.kt
(for example after Instagram or YouTube renamed something in an update).

How to use:
  1. Phone on USB, unlocked. Open the screen you want (e.g. the Instagram Reels tab).
     Tip: pause the video first (YouTube: tap it; Instagram: hold your finger on it),
     because uiautomator waits for the screen to be still.
  2. From the project folder:
        python scripts/find_view_ids.py com.instagram.android
        python scripts/find_view_ids.py com.google.android.youtube

What it does:
  - `adb exec-out uiautomator dump /dev/tty` asks Android for the current screen layout as XML
    and streams it straight to this script. Nothing is saved on the phone or on the PC.
  - The XML also contains on-screen texts (captions, names). We IGNORE them on purpose and
    print only two things per view: its ID name and whether it is selected.
  - Side effect: while uiautomator runs, Android pauses accessibility services for a moment
    (Detox reconnects by itself right after).
"""
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

ADB = os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe")

# The markers Detox uses today (keep in sync with ShortsRules.markers), highlighted in the output.
KNOWN = {"reel_progress_bar", "clips_tab"}


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    package = sys.argv[1]

    raw = subprocess.run([ADB, "exec-out", "uiautomator", "dump", "/dev/tty"], capture_output=True).stdout
    text = raw.decode("utf-8", errors="replace")
    start, end = text.find("<?xml"), text.rfind("</hierarchy>")
    if start < 0 or end < 0:
        print("No layout came back. Is the phone connected and unlocked? If a video is playing, pause it and retry.")
        print("adb said:", text.strip()[:200])
        sys.exit(2)

    root = ET.fromstring(text[start:end + len("</hierarchy>")])
    found = {}  # id name -> selected anywhere?
    for node in root.iter("node"):
        if node.get("package") != package:
            continue
        rid = node.get("resource-id") or ""
        if ":id/" not in rid:
            continue
        name = rid.split(":id/", 1)[1]
        found[name] = found.get(name, False) or node.get("selected") == "true"

    if not found:
        print(f"No view IDs from {package} on screen. Is that app in front?")
        return
    print(f"{len(found)} view ID names from {package} (texts are NOT shown):\n")
    for name in sorted(found):
        tags = []
        if found[name]:
            tags.append("SELECTED")
        if name in KNOWN:
            tags.append("<- Detox marker")
        print(f"  {name:45} {' '.join(tags)}")


if __name__ == "__main__":
    main()
