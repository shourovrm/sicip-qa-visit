# reference implementation of the report rules (progress + card links + criteria remarks).
# regenerates the fixtures that the android and web unit tests must match exactly:
#   python3 shared/report-templates/fixtures/reference.py
import json
import pathlib
import re

HERE = pathlib.Path(__file__).parent
TEMPLATE = json.loads((HERE.parent / "surprise-v1.json").read_text())
QA_TEMPLATE = json.loads((HERE.parent / "qa-v1.json").read_text())


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


# ---- criteria blocks (QA report, shared/report-templates/qa-v1.json) ----

def ensure_stop(s):
    if s == "":
        return ""
    return s if s[-1] in ".!?" else s + "."


BRACE_RE = re.compile(r"\{([^}]*)\}")


# one criteria item ({id, text, options:[...]}) + its data.criteria[item_id] entry
# ({opts:{optId:{v,detail,remark}}, evidence, note, ai}) -> ordered list of Remarks bullets.
# spec section 4: options in template order, then "Evidence seen: ...", then the free note.
def build_remarks(item, entry):
    bullets = []
    opts = entry.get("opts", {})
    for option in item.get("options", []):
        state = opts.get(option["id"], {})
        v = state.get("v") or ""
        remark = ensure_stop(str(state.get("remark") or "").strip())
        if blank(v):
            if remark:
                name = option.get("short") or option["label"]
                bullets.append(f"{name}: {remark}")
            continue
        sentence = option.get(v, "")
        detail = str(state.get("detail") or "").strip()

        def repl(match):
            part = match.group(1)
            return part.replace("$", detail) if detail else ""

        sentence = BRACE_RE.sub(repl, sentence, count=1)
        parts = [p for p in (sentence, remark) if p]
        joined = " ".join(parts)
        if joined:
            bullets.append(joined)
    evidence = str(entry.get("evidence") or "").strip()
    if evidence:
        bullets.append("Evidence seen: " + ensure_stop(evidence))
    note = str(entry.get("note") or "").strip()
    if note:
        bullets.append(ensure_stop(note))
    return bullets


# printedRemarks: the AI rewrite (entry.ai.text) replaces the fixed bullets only while it is
# still fresh -- entry.ai.source must equal the CURRENT bullets joined by "\n" (any edit to an
# option/remark/evidence/note after the AI ran makes ai.source stale, so the fixed bullets show
# again until "AI remarks" is re-run).
def printed_remarks(item, entry):
    bullets = build_remarks(item, entry)
    ai = entry.get("ai") or {}
    ai_text = str(ai.get("text") or "")
    ai_source = str(ai.get("source") or "")
    if ai_text.strip() and ai_source == "\n".join(bullets):
        lines = [line.strip() for line in ai_text.split("\n")]
        return [line for line in lines if line]
    return bullets


def criteria_items(block):
    return [item for item in block["items"] if not item.get("heading")]


# spec section 5: a section with any `criteria` block is counted purely by its options (every
# other block-type rule is ignored for that section) -- optionsTotal/optionsMarked/notSeen.
def criteria_section_progress(section, data):
    answered = total = not_seen = 0
    criteria_data = data.get("criteria", {})
    for block in section["blocks"]:
        if block["type"] != "criteria":
            continue
        for item in criteria_items(block):
            entry = criteria_data.get(item["id"], {})
            opts = entry.get("opts", {})
            for option in item["options"]:
                total += 1
                v = (opts.get(option["id"]) or {}).get("v") or ""
                if not blank(v):
                    answered += 1
                if v == "not":
                    not_seen += 1
    progress = {"answered": answered, "total": total, "done": total > 0 and answered == total, "flagged": not_seen > 0}
    if not_seen:
        progress["notSeen"] = not_seen
    return progress, []


def section_progress(section, data):
    if any(b["type"] == "criteria" for b in section["blocks"]):
        return criteria_section_progress(section, data)
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
        if kind == "criteria":
            criteria_data = data.get("criteria", {})
            for item in criteria_items(block):
                entry = criteria_data.get(item["id"])
                if not entry:
                    continue
                opts = entry.get("opts", {})
                if any(not blank((o or {}).get("v")) or not blank((o or {}).get("remark")) or not blank((o or {}).get("detail")) for o in opts.values()):
                    return True
                if not blank(entry.get("evidence")) or not blank(entry.get("note")):
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
    answer_counts = {a["id"]: 0 for a in template.get("answers", [])}
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


def qa_item(item_id):
    for _, block in all_blocks(QA_TEMPLATE):
        if block["type"] != "criteria":
            continue
        for item in block["items"]:
            if item["id"] == item_id:
                return item
    raise KeyError(item_id)


# every remarks.py rule, each case computed through build_remarks/printed_remarks (not hand-typed)
# so the fixture can never drift from the reference implementation itself.
def fixture_remarks_1():
    cases = []

    def add(name, item_id, entry):
        item = qa_item(item_id)
        cases.append({
            "case": name, "item": item, "entry": entry,
            "bullets": build_remarks(item, entry), "printed": printed_remarks(item, entry),
        })

    # marked-seen with a filled detail group, marked-seen with no group at all, marked-not-seen
    # (no remark), unmarked-with-remark (short-name fallback), marked-not-seen + remark on the
    # same bullet, evidence-seen bullet, other-remarks (note) bullet -- one item, every rule at
    # once, matches the approved mockup's own worked example.
    add("marked seen + detail, plain seen, unmarked remark (short fallback), evidence + note", "s8_1c", {
        "opts": {
            "extinguisher": {"v": "seen", "detail": "12/02/2026", "remark": ""},
            "first_aid": {"v": "seen", "detail": "", "remark": ""},
            "ppe_list": {"v": "not", "detail": "", "remark": "Office says the list is kept at head office"},
            "ppe_symbols": {"v": "", "detail": "", "remark": "Workshop 2 was locked"},
            "ppe_use": {"v": "not", "detail": "", "remark": ""},
        },
        "evidence": "Extinguisher inspection tag, first aid kit in workshop 2",
        "note": "Principal promised a PPE list by 30/09/2026",
        "ai": {"source": "", "text": ""},
    })

    # fresh ai: ai.source matches the bullets exactly built above (from the s8_1c case, minus the
    # note this time) -> printedRemarks returns the ai lines, not the fixed bullets.
    base_entry = {
        "opts": {
            "extinguisher": {"v": "seen", "detail": "12/02/2026", "remark": ""},
            "first_aid": {"v": "seen", "detail": "", "remark": ""},
            "ppe_list": {"v": "not", "detail": "", "remark": ""},
            "ppe_symbols": {"v": "", "detail": "", "remark": "Workshop 2 was locked"},
            "ppe_use": {"v": "not", "detail": "", "remark": ""},
        },
        "evidence": "Extinguisher inspection tag, first aid kit in workshop 2", "note": "",
    }
    fresh_bullets = build_remarks(qa_item("s8_1c"), base_entry)
    add("fresh ai -- source matches current bullets, printed uses ai.text", "s8_1c", {
        **base_entry,
        "ai": {"source": "\n".join(fresh_bullets),
               "text": "Fire extinguishers work; last examined 12/02/2026 (tag seen).\n"
                       "First aid kit kept in workshop 2.\n"
                       "No PPE list on site.\n"
                       "Safety and PPE symbols not checked: workshop 2 was locked."},
    })

    # stale ai: an option changed since the AI text was written (ai.source no longer equals the
    # current bullets) -> printedRemarks falls back to the fixed bullets, the ai text is ignored.
    add("stale ai -- source no longer matches, printed falls back to bullets", "s8_1c", {
        **base_entry,
        "ai": {"source": "something that no longer matches", "text": "An old AI rewrite that must not print."},
    })

    # na with a meaningful sentence (waiting_list) vs na with "" (applicants_list) -- the blank na
    # prints nothing, the meaningful one still becomes a bullet.
    add("na \"\" prints nothing, na with a real sentence still prints", "s4_2", {
        "opts": {
            "applicants_list": {"v": "na", "detail": "", "remark": ""},
            "waiting_list": {"v": "na", "detail": "", "remark": ""},
        },
        "evidence": "", "note": "",
    })

    # detail group fully dropped (including its surrounding punctuation) when the detail box is
    # blank, vs the same group filled in -- both on applicants_list's "{ ($ applicants)}" group.
    add("detail group dropped when the detail box is blank", "s4_2", {
        "opts": {"applicants_list": {"v": "seen", "detail": "", "remark": ""}}, "evidence": "", "note": "",
    })
    add("detail group filled in", "s4_2", {
        "opts": {"applicants_list": {"v": "seen", "detail": "184", "remark": ""}}, "evidence": "", "note": "",
    })

    # unmarked option + remark, no `short` on the option -- falls back to the option's own label.
    add("unmarked-option remark falls back to the option's label (no short)", "s8_1c", {
        "opts": {"first_aid": {"v": "", "detail": "", "remark": "Kit expired"}}, "evidence": "", "note": "",
    })

    # a remark that already ends with "?" -- ensureStop must not double-punctuate it.
    add("ensureStop leaves an existing ? alone", "s8_1c", {
        "opts": {"ppe_symbols": {"v": "", "detail": "", "remark": "Is this correct?"}}, "evidence": "", "note": "",
    })

    # nothing entered anywhere on the item -- both bullets and printed are empty lists.
    add("nothing entered -- empty bullets", "s8_1c", {"opts": {}, "evidence": "", "note": ""})

    return cases


# one QA-template progress case: a handful of sections with some options marked/not-seen, to
# exercise criteria_section_progress's optionsTotal/optionsMarked/notSeen + the "done when every
# option is marked" rule, section 8 in particular (two criteria blocks, s8 + s81, in one section).
def fixture_qa_progress_1():
    data = {"fields": {"ti_name": "Dhaka Skills Institute"}, "cards": {}, "criteria": {
        # section 2 (s2): all 6 options of its one item marked -> done
        "s2_1": {"opts": {
            "qms_process": {"v": "seen"}, "qms_assessment": {"v": "seen"}, "self_assessment_team": {"v": "seen"},
            "self_appraisal": {"v": "seen"}, "feedback_plan": {"v": "seen"}, "visit_log": {"v": "seen"},
        }},
        # section 4 (s4): s4_2 has 2 of 8 options marked, one of them "not" -> partial + notSeen
        "s4_2": {"opts": {"applicants_list": {"v": "seen"}, "admission_forms": {"v": "not"}}},
        # section 8 (s8): only s8_1c (5 options) touched of s8's own items; s81 (4 options)
        # entirely untouched -- both blocks belong to section s8, so its total spans both.
        "s8_1c": {"opts": {
            "extinguisher": {"v": "seen"}, "first_aid": {"v": "seen"}, "ppe_list": {"v": "not"},
            "ppe_symbols": {"v": "na"}, "ppe_use": {"v": "not"},
        }},
    }}
    return {"template": "qa-v1.json", "data": data, "expected": report_progress(QA_TEMPLATE, data)}


if __name__ == "__main__":
    fixture = fixture_1()
    (HERE / "progress-1.json").write_text(json.dumps(fixture, indent=1, ensure_ascii=False) + "\n")
    print(json.dumps(fixture["expected"]["sections"]))
    print(fixture["expected"]["sectionsDone"], fixture["expected"]["sectionsCounted"], fixture["expected"]["customFlags"])

    remarks_fixture = fixture_remarks_1()
    (HERE / "remarks-1.json").write_text(json.dumps(remarks_fixture, indent=1, ensure_ascii=False) + "\n")
    print(f"remarks-1.json: {len(remarks_fixture)} cases")

    qa_progress_fixture = fixture_qa_progress_1()
    (HERE / "progress-qa-1.json").write_text(json.dumps(qa_progress_fixture, indent=1, ensure_ascii=False) + "\n")
    print(json.dumps(qa_progress_fixture["expected"]["sections"]))
