# SmartPhset API · AWS release runbook

Prepared from source and read-only EC2 inspection on 2026-10-28, completed locally
2026-10-29 (Asia/Phnom_Penh). **No push, production replacement, Nginx reload or
infrastructure change was performed.** Commands below are for a separately
approved release window. Do not run a second backend against production MQTT.

## Verified production baseline

| Item | Observed value |
|---|---|
| SSH alias | `DevOps` in the developer's `~/.ssh/config` |
| SSH host | `ec2-54-66-2-221.ap-southeast-2.compute.amazonaws.com` |
| SSH user/key | `ubuntu`, `~/.ssh/devops.pem` (keep private) |
| API container | `smartphset-api` |
| Current tag | `ratanaksaren/smartphset-api:1.0.0` |
| Current image ID / pull digest | `sha256:380bf86be2cc97cb36919cdef08e3f1a847f6d3209ba60740db5bc275c031928` |
| Docker network | `smartphset-net` |
| Host binding | `127.0.0.1:9090` → container `9090/tcp` |
| Restart | `unless-stopped`, retry count 0 |
| Mounts | None |
| Resource limits | No explicit memory/CPU/pids/ulimit overrides; preserve this baseline |
| Logging | `json-file`, no explicit options |
| Entrypoint | `java -jar /app/app.jar`, working directory `/app` |
| Healthcheck | No Docker HEALTHCHECK; use HTTP readiness |
| Private app env | `/opt/smartphset/api.env`, root-owned mode 600 |
| Active Spring profile | `render` |
| Nginx vhost | `/etc/nginx/conf.d/phset.api.ratanak.me.conf` |
| TLS/domain | Existing Let's Encrypt certificate for `phset.api.ratanak.me` |

No Compose project/service labels are present on the API. Local
`docker-compose.yml` only defines development Postgres/Mosquitto. Do not apply it
to production. Mosquitto, spring-cafe and nexus containers are unrelated and must
remain untouched. Full non-secret Docker HostConfig baseline is in
[`expected-hostconfig.json`](../scripts/release/expected-hostconfig.json).

Current runtime environment includes DB_URL/DB_USERNAME/DB_PASSWORD,
SPRING_PROFILES_ACTIVE/PORT, MQTT_ENABLED/BROKER_URL/CLIENT_ID/DEVICES/USERNAME/PASSWORD,
IOT_API_KEY, AI_INGEST_KEY and JAVA_TOOL_OPTIONS. The new camera variables are
absent. Never print full `docker inspect` or environment values in public logs.

## Source and deployed artifact comparison

A read-only copy of `/app/app.jar` was compared with the locally built bootJar.
Within `BOOT-INF/classes`, only these differ:

- Added: `com/smart/phset/api/feature/camera/CameraController.class`.
- Changed: `com/smart/phset/api/config/SecurityConfig.class`, `application.yaml`.
- Removed: none. IoT/MQTT/auth stub classes and all V1–V4 migrations are identical.

Hashes and paths are recorded in [deployed-jar-comparison.json](deployed-jar-comparison.json).
This is artifact evidence, not proof of the Git commit used for the old image;
there is no deployed revision label. Dependency archives were not compared by
this application-class check. The local Dockerfile/Gradle files have no worktree
diff against local HEAD, but the old Docker build inputs cannot be reconstructed
from image tags alone. The new image uses current Java 25 base tags and must be
smoke-tested independently.

Security change: only camera GET matchers were added to `SecurityConfig`.
The controller checks the dedicated camera key, with 401 for wrong/missing key,
503 if unconfigured, 404 for unknown authorized source. No new CSRF exemption,
JWT login implementation, MQTT behavior or database migration was added.

## Candidate and local verification

Built locally, not pushed:

```text
ratanaksaren/smartphset-api:1.1.0-camera.20261028.9134eca.dirty
local manifest/image ID: sha256:8c4650666ceca505d21866d3d44489d4f0291333b2a7890a659ed9b843c9f514
```

The tag identifies the uncommitted camera work over HEAD `9134eca`; it is a review
candidate, not a committed release. Never overwrite it. A final release from a
reviewed commit should receive a new unique tag, with its own recorded digest.
Registry availability/permissions have not been verified by pushing. A local
RepoDigest is not evidence that the new image exists in Docker Hub.

```bash
cd /home/ratanak/smart-phset/api
./gradlew --no-daemon test build
# Use a NEW version tag if building again:
bash scripts/release/build-candidate.sh ratanaksaren/smartphset-api:YOUR_UNIQUE_VERSION
# The already built candidate can be retested without rebuilding:
bash scripts/release/smoke-image.sh ratanaksaren/smartphset-api:1.1.0-camera.20261028.9134eca.dirty
```

The Dockerfile builds bootJar in-container; host build is a separate gate.
The smoke script starts random-named local PostgreSQL 18/API containers with
random test-only keys, MQTT disabled and an ephemeral loopback port. It checks
health, registry 401/200/404, IoT 401/404, disabled-MQTT command 503 and protected
management endpoints, then removes only its own resources. This passed for the
candidate. Gradle's 42 tests passed and full `build` passed. No production data or
broker commands were used. Physical hardware remains a manual acceptance item.

## Pre-deployment blockers

1. Obtain explicit approval for pushing, production replacement and Nginx change.
2. Review/commit the intended release in a later approved task; current work is dirty.
3. Create a strong registry key privately and matching IDs on Spring/Python/Next.
   The registry accepts IDs/labels only. Python owns the private capture URLs.
4. Current Nginx returns 403 on `/api/cameras`; old application loopback also returns
   403. Camera registry deployment has not occurred. Add only the reviewed routes below.
5. Review any Neon migration history/backups in the approved release process.
   There is no new migration here. Startup still runs Flyway and validates schema.
6. Verify registry push access and record the pushed digest. Pull by digest on EC2.
7. A remote Next container cannot reach the laptop's `127.0.0.1:8002`. Remote AI
   requires a separately deployed worker/private authenticated connection. Do not
   publish port 8002 or raw camera HTTP ports, or disable authentication.
8. Legacy frontend AI flags do not gate new camera routes; production env validation
   does not yet validate new service URL/key settings. Accept/configure that path
   separately before enabling remote inference.
9. Do not open application-public detection reads in Nginx without an authenticated
   read design. Keep the existing detection POST-only restriction.

## Approved push and staging of release files (NOT executed)

Use Docker Hub authentication through an interactive/credential-helper workflow;
do not put a token into a shell command or README.

```bash
cd /home/ratanak/smart-phset/api
candidate=ratanaksaren/smartphset-api:1.1.0-camera.20261028.9134eca.dirty
docker login
docker push "$candidate"
# Record the actual registry digest shown by push and verify it:
docker buildx imagetools inspect "$candidate"
# Only after approval, copy reviewed scripts (no private env files from Git):
scp scripts/release/ec2-replace.sh scripts/release/expected-hostconfig.json DevOps:/home/ubuntu/
ssh DevOps
# Equivalent explicit SSH, if alias is unavailable:
# ssh -i ~/.ssh/devops.pem ubuntu@ec2-54-66-2-221.ap-southeast-2.compute.amazonaws.com
```

On EC2, first recheck current state; abort/review if it changed:

```bash
docker ps --filter name=smartphset-api --format '{{.Names}} {{.Image}} {{.Ports}}'
docker inspect smartphset-api --format '{{.Image}} {{.HostConfig.NetworkMode}} {{.HostConfig.RestartPolicy.Name}}'
curl --fail http://127.0.0.1:9090/actuator/health
sudo test -r /opt/smartphset/api.env
# Review/edit privately, not in a recorded terminal transcript:
sudo install -m 600 /dev/null /opt/smartphset/camera.env
sudoedit /opt/smartphset/camera.env
```

Private `/opt/smartphset/camera.env` must contain exactly these two variables,
with real private values and no shell quotes:

```dotenv
SMARTPHSET_CAMERA_REGISTRY=esp32-cam=Grow room
SMARTPHSET_CAMERA_API_KEY=REPLACE_WITH_AT_LEAST_32_RANDOM_CHARACTERS
```

The key must match Next's server-only `SMARTPHSET_CAMERA_API_KEY`.
The ID must match the laptop worker's allowlisted source. Empty worker sources
remain valid for browser-only inference; do not advertise an absent shared source.

## Approved API replacement (NOT executed)

Use the actual digest confirmed by the approved push, not an assumed remote tag:

```bash
# On EC2; replace only after verifying the pushed digest:
new_image=ratanaksaren/smartphset-api@sha256:REPLACE_WITH_VERIFIED_PUSH_DIGEST
docker pull "$new_image"
sudo APPROVE_PRODUCTION_DEPLOY=yes bash /home/ubuntu/ec2-replace.sh \
  "$new_image" /opt/smartphset/camera.env
```

The script refuses to act without its explicit opt-in, validates current
HostConfig against the captured baseline, and snapshots the sensitive container
JSON, original env file and Nginx file under root-only
`/opt/smartphset/releases/<UTC>-<pid>/`. Its runtime env preserves the **running**
application/JVM variables, including those absent from api.env, replaces only
camera variables, and lets the new image supply platform/JRE defaults.
It does not rewrite `/opt/smartphset/api.env`; the release's `runtime.env` is the
new container's effective private env-file record.

It stops and renames the old API container before starting the new one, retaining
the old container/image/environment as the primary rollback target. This avoids
simultaneous backend MQTT ownership and entails a short outage. It runs the
verified equivalent of:

```bash
docker run -d --name smartphset-api --restart unless-stopped \
  --network smartphset-net --env-file /opt/smartphset/releases/RELEASE/runtime.env \
  -p 127.0.0.1:9090:9090 "$new_image"
```

No mounts/resource overrides are added because none exist in the observed
container. The script does not touch other containers or Nginx. It attempts to
restore the retained container if `docker run` or health readiness fails. It has
passed shell syntax/guard checks, **not a live production replacement rehearsal**;
operator recovery is required for host interruption or unexpected Docker failure.

## Approved Nginx route addition (NOT executed)

Back up the existing vhost before editing. Retain the certificate paths, IoT
routes, detection POST-only route, HTTP-to-HTTPS redirect and default deny.
Add these locations inside the existing HTTPS server block:

```nginx
location = /api/cameras {
    limit_except GET { deny all; }
    proxy_pass http://127.0.0.1:9090;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
location ~ "^/api/cameras/[A-Za-z0-9_-]{1,100}/availability$" {
    limit_except GET { deny all; }
    proxy_pass http://127.0.0.1:9090;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Nginx passes the camera key header to Spring by default. Do not embed its value
in the vhost or a client bundle. Authenticate at Spring; do not remove that check.

```bash
sudoedit /etc/nginx/conf.d/phset.api.ratanak.me.conf
sudo nginx -t
sudo systemctl reload nginx
```

This is a proposed change to the verified vhost, not a copy of a deployed change.

## Post-release verification

```bash
curl --fail https://phset.api.ratanak.me/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' https://phset.api.ratanak.me/api/cameras
# Expected 401 after the new JAR + approved proxy route; 403 means proxy still blocks it.
docker inspect smartphset-api --format '{{.Image}} OOM={{.State.OOMKilled}} restarts={{.RestartCount}}'
```

Use private keys without putting values into shell history (Bash):

```bash
read -rsp 'Camera registry key: ' camera_key; echo
printf 'header = "X-SmartPhset-Camera-Key: %s"\n' "$camera_key" | \
  curl --fail --silent --show-error --config - https://phset.api.ratanak.me/api/cameras
printf 'header = "X-SmartPhset-Camera-Key: %s"\n' "$camera_key" | \
  curl --fail --silent --show-error --config - https://phset.api.ratanak.me/api/cameras/esp32-cam/availability
unset camera_key
read -rsp 'IoT REST key: ' iot_key; echo
printf 'header = "X-SmartPhset-IoT-Key: %s"\n' "$iot_key" | \
  curl --fail --silent --show-error --config - https://phset.api.ratanak.me/api/iot/devices/grow-room-1/state
unset iot_key
```

Expect keyed registry 200/IDs-labels only, authorized unknown camera 404, keyed
IoT 200 if a report exists and unauthorized IoT 401. Check fresh capture/receive
timestamps; stale state is not physical acceptance. Do not send actuator commands
as deployment probes. Gradle + isolated image smoke verify their contracts.
AI ingestion must still enforce its key; retain proxy GET/history denial. A
real ingestion write/physical load test needs its own approved manual acceptance.

## Rollback (NOT executed)

Use the exact retained container name printed by the script, available in the
private release directory's `rollback-container` file. Stop the new container
before starting the old one; never overlap MQTT clients.

```bash
# On EC2; replace RELEASE with the actual printed directory name.
release=/opt/smartphset/releases/RELEASE
old=$(sudo cat "$release/rollback-container")
docker stop smartphset-api
docker rename smartphset-api "smartphset-api-failed-$(date -u +%Y%m%dT%H%M%SZ)"
docker rename "$old" smartphset-api
docker start smartphset-api
curl --fail --retry 30 --retry-delay 3 --retry-all-errors http://127.0.0.1:9090/actuator/health
# If Nginx was modified, restore the script's original snapshot:
sudo cp -p "$release/nginx.before.conf" /etc/nginx/conf.d/phset.api.ratanak.me.conf
sudo nginx -t
sudo systemctl reload nginx
curl --fail https://phset.api.ratanak.me/actuator/health
```

If Docker start/rename was interrupted, inspect `docker ps -a` and the recorded
names first; do not blindly repeat rename commands. Keep failed containers/logs
for diagnosis. The fallback pull target is
`ratanaksaren/smartphset-api@sha256:380bf86be2cc97cb36919cdef08e3f1a847f6d3209ba60740db5bc275c031928`;
prefer the retained container because it preserves the complete old runtime.
No database rollback is needed for the camera-only change. Do not revert schemas
or delete Neon data. Recheck keyed IoT reads after rollback.
