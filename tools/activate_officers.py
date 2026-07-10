# flip placeholder accounts to real emails + fresh temp passwords
# mapping lives in gitignored tools/activate_map.json: {"Officer Name": "email", ...}
import json, secrets, string, pathlib
import sb

HERE = pathlib.Path(__file__).parent

ACTIVATE = json.loads((HERE / "activate_map.json").read_text())

def temp_password():
    return "".join(secrets.choice(string.ascii_letters + string.digits) for _ in range(12))

def main():
    ids = {r["name"]: r["id"] for r in sb.sql("select id, name from public.officers")}
    missing = set(ACTIVATE) - set(ids)
    assert not missing, f"names not in DB: {missing}"
    lines = []
    for name, email in ACTIVATE.items():
        pw = temp_password()
        sb.admin_auth("PUT", f"/admin/users/{ids[name]}", {
            "email": email, "password": pw, "email_confirm": True})
        # officers.email only auto-fills on INSERT; keep it in step by hand
        sb.sql(f"update public.officers set email = '{email}' where id = '{ids[name]}'")
        lines.append(f"{email}\t{pw}")
        print("activated:", name)
    (HERE / "passwords.txt").write_text(
        "# temp passwords, hand over in person; users change in app. Riad manages his own.\n"
        + "\n".join(lines) + "\n")
    print(f"wrote {len(lines)} fresh passwords to tools/passwords.txt")

if __name__ == "__main__":
    main()
