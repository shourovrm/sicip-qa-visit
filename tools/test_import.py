# tools/test_import.py — parse-level checks, no network
from import_visits import rows

def test_rows():
    data = rows()
    assert len(data) == 730, f"want 730 got {len(data)}"
    r = data[0]
    for key in ("officer_name", "institute", "association", "district",
                "purpose", "start_date", "end_date", "category", "created_at"):
        assert key in r, key
    assert all(x["category"] in ("A**","A++","A+","A","B","C","D","E","N/A") for x in data)
    assert all(x["start_date"] <= x["end_date"] for x in data)

if __name__ == "__main__":
    test_rows(); print("parse ok")
