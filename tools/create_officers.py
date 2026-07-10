# tools/create_officers.py — create auth users from officers.json; trigger fills officers table
# idempotent: skips emails that already exist
import json, secrets, string, pathlib
import sb

HERE = pathlib.Path(__file__).parent

def temp_password():
    alpha = string.ascii_letters + string.digits
    return "".join(secrets.choice(alpha) for _ in range(12))

def main():
    officers = json.loads((HERE / "officers.json").read_text())
    assert len(officers) == 9, f"expected 9 officers, got {len(officers)}"
    existing = {r["email"] for r in sb.sql("select email from public.officers")}
    lines = []
    for o in officers:
        if o["email"] in existing:
            print("skip (exists):", o["email"]); continue
        pw = temp_password()
        sb.admin_auth("POST", "/admin/users", {
            "email": o["email"], "password": pw, "email_confirm": True,
            "user_metadata": {"name": o["name"]}})
        lines.append(f'{o["email"]}\t{pw}')
        print("created:", o["name"])
    # trigger set name from metadata; enforce exact sheet spelling + admin role anyway
    for o in officers:
        name = o["name"].replace("'", "''")
        role = "admin" if o.get("admin") else "officer"
        sb.sql(f"update public.officers set name = '{name}', role = '{role}' "
               f"where email = '{o['email']}'")
    if lines:
        (HERE / "passwords.txt").write_text("\n".join(lines) + "\n")
        print(f"wrote {len(lines)} temp passwords to tools/passwords.txt")

if __name__ == "__main__":
    main()
