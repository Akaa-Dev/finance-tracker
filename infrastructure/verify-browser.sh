#!/usr/bin/env bash
# Runs only against a new disposable database. Never sources the user's .env.
set -euo pipefail
cd "$(dirname "$0")/.."
task_tmp=$(mktemp -d "${TMPDIR:-/tmp}/finance-e2e.XXXXXX")
chmod 700 "$task_tmp"
task_db="finance-e2e-$(date +%s)-$$"
task_backend_pid=''
cleanup() {
  if [[ -n "$task_backend_pid" ]]; then kill "$task_backend_pid" 2>/dev/null || true; wait "$task_backend_pid" 2>/dev/null || true; fi
  docker rm -f "$task_db" >/dev/null 2>&1 || true
  rm -rf "$task_tmp"
}
trap cleanup EXIT INT TERM
python3 - "$task_tmp/environment" <<'PY'
import secrets,sys
from pathlib import Path
password=secrets.token_hex(24)
Path(sys.argv[1]).write_text('POSTGRES_USER=finance_test\nPOSTGRES_DB=finance_test\nPOSTGRES_PASSWORD='+password+'\nDB_USER=finance_test\nDB_PASSWORD='+password+'\nOWNER_EMAIL=browser@example.invalid\nOWNER_PASSWORD='+secrets.token_hex(24)+'\nSERVER_PORT=18080\nVITE_API_PROXY=http://127.0.0.1:18080\n')
PY
set -a
. "$task_tmp/environment"
set +a
# Ignore optional local .env imports when running the disposable test backend.
export SPRING_CONFIG_IMPORT=''
docker run --rm -d --name "$task_db" --env-file "$task_tmp/environment" -p 127.0.0.1::5432 postgres:17 >/dev/null
for task_attempt in $(seq 1 40); do
  if docker exec "$task_db" pg_isready -U finance_test -d finance_test >/dev/null 2>&1; then break; fi
  sleep 1
done
task_port=$(docker inspect --format '{{(index (index .NetworkSettings.Ports "5432/tcp") 0).HostPort}}' "$task_db")
export DB_URL="jdbc:postgresql://127.0.0.1:$task_port/finance_test"
(cd backend && ./gradlew bootJar --no-daemon)
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -jar backend/build/libs/finance-tracker-0.0.1-SNAPSHOT.jar >"$task_tmp/backend.log" 2>&1 &
task_backend_pid=$!
task_ready=false
for task_attempt in $(seq 1 50); do
  if curl --silent --fail http://127.0.0.1:18080/actuator/health >/dev/null; then task_ready=true; break; fi
  if ! kill -0 "$task_backend_pid" 2>/dev/null; then cat "$task_tmp/backend.log"; exit 1; fi
  sleep 1
done
if [[ "$task_ready" != true ]]; then cat "$task_tmp/backend.log"; exit 1; fi
(cd frontend && npx playwright test)
