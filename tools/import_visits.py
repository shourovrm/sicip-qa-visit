# tools/import_visits.py — one-shot import of historical sheet rows into visits
# idempotent: deletes source='sheet' rows first, re-inserts in batches
import pathlib
import openpyxl
import sb

XLSX = pathlib.Path(__file__).parent.parent / "assets" / "visit-scores-export.xlsx"

def rows():
    ws = openpyxl.load_workbook(XLSX, read_only=True)["Form Responses 4"]
    data = []
    it = ws.iter_rows(values_only=True)
    next(it)  # header
    for r in it:
        if not r or not r[0]:
            continue
        start, end = r[6].date(), r[7].date()
        if start > end:
            start, end = end, start  # 2 sheet rows have swapped start/end (data entry error)
        data.append({
            "created_at":   r[0].isoformat(),
            "officer_name": str(r[1]).strip(),
            "institute":    str(r[2]).strip(),
            "association":  str(r[3]).strip(),
            "district":     str(r[4]).strip(),
            "purpose":      str(r[5]).strip(),
            "start_date":   start.isoformat(),
            "end_date":     end.isoformat(),
            "category":     str(r[8]).strip() if r[8] else "N/A",
            "remarks":      str(r[9]).strip() if len(r) > 9 and r[9] else None,
        })
    return data

def main():
    ids = {r["name"]: r["id"] for r in sb.sql("select id, name from public.officers")}
    data = rows()
    missing = {d["officer_name"] for d in data} - set(ids)
    assert not missing, f"officers not in DB: {missing}"  # run create_officers first
    sb.sql("delete from public.visits where source = 'sheet'")  # idempotent re-run
    payload = [{
        "officer_id": ids[d["officer_name"]],
        "institute": d["institute"], "association": d["association"],
        "district": d["district"], "purpose": d["purpose"],
        "ref_no": None, "start_date": d["start_date"], "end_date": d["end_date"],
        "category": d["category"], "category_override": True,
        "is_additional": d["category"] == "N/A",
        "status": "done", "remarks": d["remarks"],
        "source": "sheet", "created_at": d["created_at"],
    } for d in data]
    for i in range(0, len(payload), 200):
        sb.rest("POST", "/rest/v1/visits", body=payload[i:i+200])
        print(f"inserted {min(i+200, len(payload))}/{len(payload)}")

if __name__ == "__main__":
    main()
