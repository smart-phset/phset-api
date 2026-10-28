#!/usr/bin/env bash
# Only disposable local resources; never loads a production env file or broker.
set -euo pipefail
image=${1:?Usage: smoke-image.sh local-image:version}
docker image inspect "$image" >/dev/null
name="smartphset-release-check-$(date -u +%Y%m%d%H%M%S)-$$"
private=$(mktemp -d /tmp/smartphset-release-check.XXXXXX)
chmod 700 "$private"
cleanup() {
  docker rm -fv "$name-api" "$name-db" >/dev/null 2>&1 || true
  docker network rm "$name-net" >/dev/null 2>&1 || true
  # Contains only random test credentials, not production values.
  rm -f "$private/runtime.env" "$private/headers" "$private/state.json"
  rmdir "$private" 2>/dev/null || true
}
trap cleanup EXIT
password=$(openssl rand -hex 24)
key=$(openssl rand -hex 32)
printf 'DB_URL=jdbc:postgresql://%s-db:5432/release_check\nDB_USERNAME=release_check\nDB_PASSWORD=%s\nMQTT_ENABLED=false\nPORT=9090\nIOT_API_KEY=%s\nAI_INGEST_KEY=%s\nSMARTPHSET_CAMERA_API_KEY=%s\nSMARTPHSET_CAMERA_REGISTRY=esp32-cam=Release check\n' "$name" "$password" "$key" "$key" "$key" > "$private/runtime.env"
chmod 600 "$private/runtime.env"
docker network create "$name-net" >/dev/null
docker run -d --name "$name-db" --network "$name-net" \
  -e POSTGRES_DB=release_check -e POSTGRES_USER=release_check -e POSTGRES_PASSWORD="$password" postgres:18 >/dev/null
ready=false
for _ in {1..60}; do
  if docker exec "$name-db" pg_isready -U release_check -d release_check >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[[ "$ready" == true ]] || { echo 'Disposable DB did not become ready.' >&2; exit 1; }
docker run -d --name "$name-api" --network "$name-net" \
  --env-file "$private/runtime.env" -p 127.0.0.1::9090 "$image" >/dev/null
port=$(docker inspect --format '{{(index (index .NetworkSettings.Ports "9090/tcp") 0).HostPort}}' "$name-api")
base="http://127.0.0.1:$port"
curl --fail --silent --show-error --retry 40 --retry-delay 2 --retry-all-errors "$base/actuator/health"
[[ $(curl -s -o /dev/null -w '%{http_code}' "$base/api/cameras") == 401 ]]
printf 'header = "X-SmartPhset-Camera-Key: %s"\n' "$key" > "$private/headers"
curl --fail --silent --show-error --config "$private/headers" "$base/api/cameras" > "$private/state.json"
python3 - "$private/state.json" <<'PY'
import json,sys
assert json.load(open(sys.argv[1])) == [{'id':'esp32-cam','label':'Release check','availability':'unknown'}]
PY
curl --fail --silent --show-error --config "$private/headers" "$base/api/cameras/esp32-cam/availability" >/dev/null
[[ $(curl -s -o /dev/null -w '%{http_code}' --config "$private/headers" "$base/api/cameras/absent/availability") == 404 ]]
printf 'header = "X-SmartPhset-IoT-Key: %s"\n' "$key" > "$private/headers"
[[ $(curl -s -o /dev/null -w '%{http_code}' "$base/api/iot/devices/grow-room-1/state") == 401 ]]
# No report has been ingested into this fresh DB.
[[ $(curl -s -o /dev/null -w '%{http_code}' --config "$private/headers" "$base/api/iot/devices/grow-room-1/state") == 404 ]]
# MQTT is disabled and no command can reach a broker.
[[ $(curl -s -o /dev/null -w '%{http_code}' --config "$private/headers" \
  -H 'Content-Type: application/json' -d '{"on":false}' "$base/api/iot/devices/grow-room-1/actuators/pump") == 503 ]]
[[ $(curl -s -o /dev/null -w '%{http_code}' "$base/actuator/env") == 403 ]]
[[ $(curl -s -o /dev/null -w '%{http_code}' "$base/v3/api-docs") == 404 ]]
docker inspect --format 'OOM={{.State.OOMKilled}} restarts={{.RestartCount}} image={{.Image}}' "$name-api"
echo 'Isolated image smoke passed; no production DB/MQTT was used.'
