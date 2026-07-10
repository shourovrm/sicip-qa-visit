# tools/sb.py — thin Supabase access: service key via Management API, then REST/GoTrue/SQL
import json, subprocess, urllib.request, urllib.parse, pathlib

REF = "twpehiqfdjfprtwsiwqp"
URL = f"https://{REF}.supabase.co"
_TOKEN = (pathlib.Path(__file__).parent.parent / "supabase" / ".token").read_text().strip()
_key_cache = {}

def _mgmt(method, path, body=None):
    # curl not urllib: Cloudflare blocks python UA on api.supabase.com
    cmd = ["curl", "-s", "-X", method, "-H", f"Authorization: Bearer {_TOKEN}",
           "-H", "Content-Type: application/json",
           f"https://api.supabase.com{path}"]
    if body is not None:
        cmd += ["-d", json.dumps(body)]
    out = subprocess.run(cmd, capture_output=True, text=True, check=True).stdout
    return json.loads(out) if out else None

def service_key():
    if "k" not in _key_cache:
        keys = _mgmt("GET", f"/v1/projects/{REF}/api-keys?reveal=true")
        _key_cache["k"] = next(k["api_key"] for k in keys if k["name"] == "service_role")
    return _key_cache["k"]

def sql(query):
    return _mgmt("POST", f"/v1/projects/{REF}/database/query", {"query": query})

def _http(method, url, body, headers):
    req = urllib.request.Request(url, method=method,
        data=json.dumps(body).encode() if body is not None else None, headers=headers)
    with urllib.request.urlopen(req) as r:
        raw = r.read().decode()
    return json.loads(raw) if raw else None

def rest(method, path, body=None, params=None):
    k = service_key()
    q = "?" + urllib.parse.urlencode(params) if params else ""
    return _http(method, f"{URL}{path}{q}", body,
        {"apikey": k, "Authorization": f"Bearer {k}",
         "Content-Type": "application/json", "Prefer": "return=representation"})

def admin_auth(method, path, body=None):
    k = service_key()
    return _http(method, f"{URL}/auth/v1{path}", body,
        {"apikey": k, "Authorization": f"Bearer {k}", "Content-Type": "application/json"})
