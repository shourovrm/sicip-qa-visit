# tools/test_sb.py — hits live project; cheap read-only checks
import sb

def test_service_key():
    k = sb.service_key()
    assert k and (k.startswith("eyJ") or k.startswith("sb_secret_")), "service key shape"

def test_sql():
    rows = sb.sql("select count(*) as n from public.officers")
    assert rows[0]["n"] >= 0

def test_rest():
    rows = sb.rest("GET", "/rest/v1/officers", params={"select": "id", "limit": "1"})
    assert isinstance(rows, list)

if __name__ == "__main__":
    test_service_key(); test_sql(); test_rest(); print("sb ok")
