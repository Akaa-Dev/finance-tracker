# Phase 0 API

| Method | Path | Contract |
|---|---|---|
| GET | `/actuator/health` | Public status only; database is part of health |
| GET | `/api/v1/auth/csrf` | Public session-bound `{headerName, token}`; creates session |
| POST | `/api/v1/auth/login` | Form-urlencoded `username`, `password`, CSRF header; 204 on success, 401 on invalid credentials |
| GET | `/api/v1/auth/me` | Authenticated `{id, email, currency}`; otherwise 401 |
| POST | `/api/v1/auth/logout` | Requires CSRF header; 204, invalidates session |

No public registration or HTTP bootstrap endpoint. Owner creation happens at startup from private environment configuration, after Liquibase migration. Missing/invalid configuration fails startup. Existing credentials are never overwritten. Only one owner is permitted by the database.

Security failures return `application/problem+json` with `type`, `title`, `status`. Missing/invalid CSRF yields 403. The frontend treats a 401 from `/auth/me` as the sign-in state and other failures as service errors. Unauthenticated financial routes return 401; authenticated unimplemented routes are not financial APIs.

## Manual smoke test

Run with backend available. Requires curl and Python 3. Keep cookie files local, delete after use. Read the password interactively so it does not appear in shell history. The form body is piped to curl rather than placed in its process arguments.

```sh
curl -fsS http://127.0.0.1:8080/actuator/health
curl -i http://127.0.0.1:8080/api/v1/installments # expected 401
curl -sS -c /tmp/finance-cookies http://127.0.0.1:8080/api/v1/auth/csrf > /tmp/finance-csrf.json
TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/finance-csrf.json"))["token"])')
python3 -c 'import getpass,urllib.parse,sys; sys.stderr.write("Email: "); print(urllib.parse.urlencode({"username":input(),"password":getpass.getpass()}))' \
  | curl -i -b /tmp/finance-cookies -c /tmp/finance-cookies \
      -H "X-CSRF-TOKEN: $TOKEN" -H 'Content-Type: application/x-www-form-urlencoded' \
      --data-binary @- http://127.0.0.1:8080/api/v1/auth/login
# Expected 204; token rotates on authentication.
curl -i -b /tmp/finance-cookies http://127.0.0.1:8080/api/v1/auth/me
curl -sS -b /tmp/finance-cookies -c /tmp/finance-cookies http://127.0.0.1:8080/api/v1/auth/csrf > /tmp/finance-csrf.json
TOKEN=$(python3 -c 'import json; print(json.load(open("/tmp/finance-csrf.json"))["token"])')
curl -i -b /tmp/finance-cookies -H "X-CSRF-TOKEN: $TOKEN" -X POST http://127.0.0.1:8080/api/v1/auth/logout
curl -i -b /tmp/finance-cookies http://127.0.0.1:8080/api/v1/auth/me # expected 401
rm /tmp/finance-cookies /tmp/finance-csrf.json
unset TOKEN
```
