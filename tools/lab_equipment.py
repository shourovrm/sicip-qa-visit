"""Build shared/lab-standards/lab-equipment.json from the SICIP-standards repo.

The standards site (sicip-standards.pages.dev) keeps one lab standard per association course in
data/data.json. Reports only need the equipment names, so this keeps org + course + names.
Run again whenever the standards change: python3 tools/lab_equipment.py [path/to/data.json]
"""
import json
import sys
from pathlib import Path

DEFAULT_SOURCE = Path.home() / "repos/SICIP-standards/data/data.json"
TARGET = Path(__file__).resolve().parent.parent / "shared/lab-standards/lab-equipment.json"
# a few standards put the whole specification in the name cell; a report line needs the item only
LONG_NAME = 60


def short_name(name):
    first_line = name.strip().splitlines()[0].strip()
    if len(first_line) > LONG_NAME:
        cut_points = [first_line.find(mark) for mark in (":", " (", ";")]
        cut_points = [point for point in cut_points if point > 0]
        if cut_points:
            first_line = first_line[: min(cut_points)]
    return first_line.strip(" -:,;")


def unique_names(rows):
    seen = set()
    names = []
    for row in rows:
        name = short_name(str(row.get("name") or ""))
        if name and name.lower() not in seen:
            seen.add(name.lower())
            names.append(name)
    return names


def main():
    source = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SOURCE
    standards = json.loads(source.read_text())
    courses = [
        {"org": entry["org"], "course": entry["course_name"].strip(), "equipment": unique_names(entry["equipment"])}
        for entry in standards
    ]
    TARGET.write_text(json.dumps(courses, ensure_ascii=False, separators=(",", ":")) + "\n")
    print(f"{len(courses)} courses, {sum(len(course['equipment']) for course in courses)} equipment names -> {TARGET}")


if __name__ == "__main__":
    main()
