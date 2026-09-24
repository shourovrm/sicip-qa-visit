# reference implementation of the report rules (progress + card links). regenerates the
# fixtures that the android and web unit tests must match exactly:
#   python3 shared/report-templates/fixtures/reference.py
import json
import pathlib

HERE = pathlib.Path(__file__).parent
TEMPLATE = json.loads((HERE.parent / "surprise-v1.json").read_text())


def blank(value):
    return value is None or str(value).strip() == ""


def all_blocks(template):
    for section in template["sections"]:
        for block in section["blocks"]:
            yield section, block


# linked cards: one target card per source card that has any linked field filled, in source
# order. target _id is derived from the source _id so every platform produces the same data.
def sync_links(template, data):
    cards = data.setdefault("cards", {})
    for _, block in all_blocks(template):
        link = block.get("linkFrom")
        if block["type"] != "cards" or not link:
            continue
        sources = [s for s in cards.get(link["cards"], []) if any(not blank(s.get(k)) for k in link["fields"])]
        existing = {c.get("_link"): c for c in cards.get(block["key"], [])}
        synced = []
        for source in sources:
            target = dict(existing.get(source["_id"], {}))
            target["_id"] = block["key"] + ":" + source["_id"]
            target["_link"] = source["_id"]
            for key in link["fields"]:
                target[key] = source.get(key, "")
            synced.append(target)
        cards[block["key"]] = synced
    return data


# ids of today's courses (section A) that per-course items split by
def course_ids(data):
    return [c["_id"] for c in data.get("cards", {}).get("courses", []) if not blank(c.get("course"))]


def derive_answer(values):
    if any(blank(v) for v in values):
        return ""
    real = [v for v in values if v != "na"]
    if not real:
        return "na"
    return real[0] if all(v == real[0] for v in real) else "partial"


# per-course items (template "perCourse": true) with 2+ courses: keep only current course ids
# in checks[item].courses and store the derived overall answer in checks[item].answer
# ("" until every course is answered). with 0-1 courses the item is a plain single answer.
def sync_per_course(template, data):
    ids = course_ids(data)
    if len(ids) < 2:
        return data
    checks = data.setdefault("checks", {})
    for _, block in all_blocks(template):
        if block["type"] != "checklist":
            continue
        for item in block["items"]:
            if not item.get("perCourse"):
                continue
            check = checks.setdefault(item["id"], {"answer": "", "remarks": ""})
            per = check.get("courses", {})
            # item answered before a 2nd course existed: that answer belonged to the first
            # course, so it moves there instead of vanishing (new courses start blank)
            if not per and not blank(check.get("answer")):
                per = {ids[0]: check["answer"]}
            check["courses"] = {cid: per[cid] for cid in ids if cid in per}
            check["answer"] = derive_answer([check["courses"].get(cid, "") for cid in ids])
            check.setdefault("remarks", "")
    return data


# the one normalisation step every client runs after each edit and on open
def normalize(template, data):
    sync_links(template, data)
    sync_per_course(template, data)
    return data


def compare_mismatch(compare, card):
    values = [int(card[k]) for k in compare["fields"] if not blank(card.get(k))]
    return len(values) >= 2 and len(set(values)) > 1


def tone_of(field, value):
    for option in field.get("options", []):
        if isinstance(option, dict) and option["id"] == value:
            return option["tone"]
    return None


def counts(field):
    return field.get("required") or field["kind"] == "choice"


def section_progress(section, data):
    answered = total = 0
    flagged = False
    custom_flags = []
    for block in section["blocks"]:
        kind = block["type"]
        if kind == "checklist":
            for item in block["items"]:
                total += 1
                check = data.get("checks", {}).get(item["id"]) or {}
                answer = check.get("answer")
                if not blank(answer):
                    answered += 1
                per_course = check.get("courses", {}) if item.get("perCourse") and len(course_ids(data)) >= 2 else {}
                if answer == "no" or "no" in per_course.values():
                    flagged = True
        elif kind == "fields":
            for field in block["fields"]:
                if not counts(field):
                    continue
                total += 1
                value = data.get("fields", {}).get(field["key"])
                if not blank(value):
                    answered += 1
                if field["kind"] == "choice" and tone_of(field, value) == "no":
                    flagged = True
        elif kind == "cards":
            entries = data.get("cards", {}).get(block["key"], [])
            if block.get("countsAsFlags"):
                for card in entries:
                    text = card.get(block["titleField"])
                    if not blank(text):
                        custom_flags.append(text.strip())
                        flagged = True
                continue
            for card in entries:
                for field in block["fields"]:
                    if not counts(field):
                        continue
                    total += 1
                    value = card.get(field["key"])
                    if not blank(value):
                        answered += 1
                    if field["kind"] == "choice" and tone_of(field, value) == "no":
                        flagged = True
                if "compare" in block and compare_mismatch(block["compare"], card):
                    flagged = True
        elif kind == "flags":
            ticked = set(data.get("flags", []))
            if any(item["id"] in ticked for item in block["items"]):
                flagged = True
    return {"answered": answered, "total": total, "done": total > 0 and answered == total, "flagged": flagged}, custom_flags


# optional sections print only when the officer filled something in them
def section_has_content(section, data):
    for block in section["blocks"]:
        kind = block["type"]
        if kind == "fields" and any(not blank(data.get("fields", {}).get(f["key"])) for f in block["fields"]):
            return True
        if kind == "checklist":
            for item in block["items"]:
                check = data.get("checks", {}).get(item["id"]) or {}
                if not blank(check.get("answer")) or not blank(check.get("remarks")) or check.get("courses"):
                    return True
        if kind == "cards":
            for card in data.get("cards", {}).get(block["key"], []):
                if any(not blank(v) for k, v in card.items() if not k.startswith("_")):
                    return True
        if kind == "flags" and any(i["id"] in data.get("flags", []) for i in block["items"]):
            return True
    return False


def report_progress(template, data):
    sections = {}
    custom_flags = []
    for section in template["sections"]:
        sections[section["key"]], found = section_progress(section, data)
        custom_flags += found
    counted = [
        sections[s["key"]] for s in template["sections"]
        if not s.get("optional") and sections[s["key"]]["total"] > 0
    ]
    answer_counts = {a["id"]: 0 for a in template["answers"]}
    unanswered = []
    for _, block in all_blocks(template):
        if block["type"] != "checklist":
            continue
        for item in block["items"]:
            answer = (data.get("checks", {}).get(item["id"]) or {}).get("answer")
            if blank(answer):
                unanswered.append(item["id"])
            else:
                answer_counts[answer] += 1
    return {
        "sections": sections,
        "sectionsDone": sum(s["done"] for s in counted),
        "sectionsCounted": len(counted),
        "answerCounts": answer_counts,
        "unansweredCount": len(unanswered),
        "firstUnanswered": unanswered[0] if unanswered else None,
        "flagsTicked": data.get("flags", []),
        "customFlags": custom_flags,
        "sectionsWithContent": [s["key"] for s in template["sections"] if section_has_content(s, data)],
    }


def fixture_1():
    data = {
        "fields": {"ti_name": "Bangladesh-Korea TTC, Mirpur", "visit_date": "2026-09-24", "arrival_time": "10:05",
                   "overall_rating": "", "key_findings": "", "compliance": "none"},
        "checks": {
            "arrival_1": {"answer": "yes", "remarks": ""}, "arrival_2": {"answer": "yes", "remarks": ""},
            "arrival_3": {"answer": "partial", "remarks": "Theory instead of practical"},
            "arrival_4": {"answer": "yes", "remarks": ""},
            "materials_1": {"answer": "yes", "remarks": ""}, "materials_2": {"answer": "no", "remarks": "Not printed yet"},
            "attendance_register_1": {"answer": "yes", "remarks": ""}, "attendance_register_2": {"answer": "yes", "remarks": ""},
            "attendance_register_3": {"answer": "na", "remarks": ""},
        },
        "cards": {
            "persons": [{"_id": "p1", "name": "Md. Karim", "designation": "Principal"}],
            "courses": [
                {"_id": "c1", "course": "Welding (SMAW)", "batch": "07"},
                {"_id": "c2", "course": "Electrical Installation", "batch": "03"},
                {"_id": "c3", "course": "", "batch": ""},
            ],
            # c2's card already exists (kept + re-linked), c1's is created, the stale x9 card is dropped
            "attendance": [
                {"_id": "attendance:c2", "_link": "c2", "course": "Old name", "batch": "03",
                 "present_total": "22", "register": "22", "tms": "", "trainers_present": "2"},
                {"_id": "attendance:x9", "_link": "x9", "course": "Removed course", "present_total": "5"},
            ],
            "identity": [{"_id": "i1", "name": "Rakib", "batch": "Welding (SMAW) · 07", "result": "verified"}],
            "unresolved": [{"_id": "u1", "issue": "Dropout register missing", "remarks": ""}],
            "other_flags": [{"_id": "f1", "flag": "Trainees sharing one ID card"}, {"_id": "f2", "flag": " "}],
        },
        "flags": ["flag_3"],
    }
    # E1 CS: welding yes, electrical no -> partial + flagged. E2: only one course answered -> blank
    data["checks"]["materials_1"] = {"answer": "", "remarks": "Electrical CS not received", "courses": {"c1": "yes", "c2": "no", "gone": "yes"}}
    data["checks"]["materials_2"] = {"answer": "yes", "remarks": "", "courses": {"c1": "yes"}}
    data["checks"]["materials_3"] = {"answer": "", "remarks": "", "courses": {"c1": "na", "c2": "yes"}}
    # answered while only one course existed -> moves to the first course (c1), overall blank
    data["checks"]["materials_4"] = {"answer": "yes", "remarks": ""}
    data["cards"]["interviews"] = [{"_id": "interviews:c1", "_link": "c1", "course": "Welding (SMAW)", "batch": "07",
                                    "trainees_interviewed": "6", "q1": "yes", "q2": "no", "tech_topic": "Electrode angle", "tech_result": "most"}]
    before = json.loads(json.dumps(data))
    normalize(TEMPLATE, data)
    synced = json.loads(json.dumps(data))
    # fill the newly linked c1 card after sync, as an officer would
    for card in data["cards"]["attendance"]:
        if card["_link"] == "c1":
            card.update({"present_total": "17", "register": "23", "tms": "23"})
    # tests: normalize(before_sync) == synced; progress(data) == expected
    return {"template": "surprise-v1.json", "before_sync": before, "synced": synced, "data": data,
            "expected": report_progress(TEMPLATE, data)}


if __name__ == "__main__":
    fixture = fixture_1()
    (HERE / "progress-1.json").write_text(json.dumps(fixture, indent=1, ensure_ascii=False) + "\n")
    print(json.dumps(fixture["expected"]["sections"]))
    print(fixture["expected"]["sectionsDone"], fixture["expected"]["sectionsCounted"], fixture["expected"]["customFlags"])
