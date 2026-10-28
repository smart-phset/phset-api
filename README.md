# SmartPhset · API

Spring Boot backend for ESP32 telemetry, expiring MQTT actuator commands,
PostgreSQL detection history and an authenticated operator-managed camera registry.
Production origin: **https://phset.api.ratanak.me**. Repository remote is
`smart-phset/phset-api`; this workspace directory is `api`.

**Release state:** EC2 was inspected on 2026-10-28. It runs
`ratanaksaren/smartphset-api:1.0.0`; the new local camera registry is **not deployed**.
Nginx currently denies registry routes. See [AWS release runbook](docs/aws-redeployment.md)
for verified runtime settings, image comparison, blockers, deployment and rollback.

## Architecture and dependencies

```text
ESP32 ↔ Mosquitto ↔ MQTT gateway → validated reports → PostgreSQL
Next.js server → keyed REST state/commands and camera registry
Python shared worker → keyed detection ingestion → PostgreSQL → permitted history consumers
```

Java **25**, Spring Boot **4.1.1**, Gradle wrapper **9.7.1**. `build.gradle`
includes Spring MVC/Validation/Security/Data JPA/Integration MQTT/WebSocket/Actuator,
PostgreSQL JDBC, Flyway PostgreSQL, Paho 1.2.5, springdoc 3.1.1, JJWT 0.13.0,
Lombok and MapStruct 1.6.3. Tests use JUnit, Mockito, Spring Security/MockMvc,
Testcontainers PostgreSQL 18 and JaCoCo. Declared WebSocket/JWT dependencies do
not establish an implemented real-time or JWT authentication contract.

```text
src/main/java/com/smart/phset/api/
  config/          security chain, OpenAPI, JWT stubs
  feature/iot/     REST, MQTT, validation and persistence
  feature/ai/      detection ingestion, history and DTOs
  feature/camera/  keyed static registry controller
src/main/resources/application.yaml         common configuration
src/main/resources/application-render.yaml  container resource/profile settings
src/main/resources/db/migration/            V1–V4 Flyway migrations
src/test/                                   unit and integration tests
Dockerfile / .dockerignore                  Java 25 multistage non-root image
 docker-compose.yml / mosquitto/            development infrastructure only
 docs/                                      contracts and release runbook
 scripts/release/                            opt-in release tooling
```

## Authentication and security boundaries

`SecurityConfig` allows specific routes through to controller-level keys:
IoT uses `X-SmartPhset-IoT-Key`, ingestion uses `X-SmartPhset-AI-Key`, registry
uses `X-SmartPhset-Camera-Key`. Wrong/missing keys return 401; a missing configured
key disables its feature with 503. Credentials are compared in constant time.
Only existing keyed machine POST routes bypass CSRF. Other routes require
Spring authentication; non-health actuator routes are denied.

`AuthController`, `JwtService` and `JwtAuthenticationFilter` are stubs;
`JWT_SECRET` does not implement login. Next's team-cookie login is separate.
Detection GET/history is public in application code. Current AWS Nginx blocks
those reads and all unmatched paths; preserve that restriction. Health hides component details in the local image (group names may also appear); OpenAPI is disabled in the `render` profile.
Do not give service keys to browser JavaScript.

## Safe local development

Install JDK 25 (the default host `java` may be 21), Docker with Compose for
isolated PostgreSQL/Testcontainers, and Git. Confirm toolchain with
`./gradlew -q javaToolchains`. No global Gradle install is needed.
Do not source the production env file into a development API instance.

```bash
cp .env.example .env
# Edit with separate development DB and REST keys; set MQTT_ENABLED=false initially.
set -a
source .env
set +a
# Start only disposable local PostgreSQL, loopback-bound:
docker compose -f compose.local.yaml up -d postgres
export MQTT_ENABLED=false
./gradlew bootRun
```

The provided `compose.local.yaml` is separate from the existing Compose file;
it creates its own project/volume and does not start Mosquitto. The original
`docker-compose.yml` contains **only Postgres and Mosquitto**, no API deployment
service, and publishes anonymous development broker ports to all interfaces.
Do not run it on EC2 or attach a test API to production MQTT. Optional local
MQTT testing requires a separately isolated broker, device IDs and client ID.

Spring does not load `.env` itself. Bash `source` executes shell syntax, so source
only a trusted private file. Compose reads interpolation values but does not
export them to `bootRun`. Database migrations run at startup; Hibernate validates
schema rather than creating it. Never point test configuration at Neon production.

## Configuration

| Variable | Behavior |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | JDBC PostgreSQL connection; password required; use Neon JDBC SSL requirements from private production config |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Development Compose DB initialization only |
| `PORT` | HTTP port, default 9090; container binds 0.0.0.0 internally |
| `SPRING_PROFILES_ACTIVE` | Image default `render`; preserves existing deployment profile when explicitly provided |
| `DB_POOL_MAX_SIZE` | `render` profile default 2, idle minimum 0 |
| `IOT_API_KEY` | Private REST state/actuator key |
| `AI_INGEST_KEY` | Private detection POST key |
| `MQTT_ENABLED` | Default true; false for tests/safe DB-only development |
| `MQTT_BROKER_URL` | Default `tcp://localhost:1883`; production uses existing private configuration |
| `MQTT_CLIENT_ID`, `MQTT_DEVICES` | Unique backend MQTT client and comma-separated device allowlist |
| `MQTT_USERNAME`, `MQTT_PASSWORD` | Broker credentials; separate from HTTP keys |
| `SMARTPHSET_CAMERA_REGISTRY` | New: comma-separated `id=label`, empty means no registered cameras |
| `SMARTPHSET_CAMERA_API_KEY` | New: registry service secret; match Next server configuration |
| `AI_BASE_URL`, `AI_API_KEY` | Existing AI properties; not session-worker routing |
| `JWT_SECRET` | Existing property; JWT implementation remains a stub |
| `JAVA_TOOL_OPTIONS` | Image JVM sizing; never put secrets here, JVM prints it |

Registry example: `SMARTPHSET_CAMERA_REGISTRY=esp32-cam=Grow room,worker-usb=Worker USB`.
IDs accept letters/digits/underscore/hyphen, 1–100 characters, unique; `browser`
is reserved. Invalid entries fail application construction. Labels must be nonblank.
Camera URLs belong solely in Python `SMARTPHSET_CAMERA_SOURCES`; matching IDs are
required in both systems. Registry availability is always `unknown`: Spring does
not probe the laptop worker. Next merges the worker's `/sources` status.

## REST and MQTT contracts

| Endpoint | Contract |
|---|---|
| `GET /actuator/health` | Public health; local profile hides component details |
| `GET /api/iot/devices/{device}/state` | IoT key; latest reported state/telemetry, 404 before first report or for unknown device |
| `POST /api/iot/devices/{device}/actuators/{fan\|light\|pump}` | IoT key, `{"on":true}` or false; 202 receipt, 503 if broker unavailable |
| `POST /api/ai/detections` | Ingest key; validated JSON metadata, 201 new or 200 duplicate `event_id` |
| `GET /api/ai/detections/latest?camera=esp32-cam` | Latest or 404; application-public, AWS proxy-blocked |
| `GET /api/ai/detections?camera=esp32-cam&limit=20` | Array, limit 1–100; application-public, AWS proxy-blocked |
| `GET /api/cameras` | Camera key; array of `{id,label,availability:"unknown"}` |
| `GET /api/cameras/{id}/availability` | Camera key; same entry or 404 |

Detection payload includes `event_id`, `camera_id`, `captured_at`, verdict,
severity, counts/confidences, positive width/height, `inference_ms` and boxes
`{label,conf,xyxy:[x1,y1,x2,y2]}`. No video/images are stored. See the complete
[AI contract](docs/ai-detection-contract.md) and [IoT contract](docs/iot-integration.md).

MQTT subscribes to `smartphset/devices/+/telemetry` and `/state` at QoS1,
validating allowlisted devices and timestamps. Commands go to
`smartphset/devices/{device}/commands`, QoS0, non-retained, with a UUID
`command_id`, 10-second `expires_at` and explicit `F1/F0`, `L1/L0`, `P1/P0`.
No offline replay/retry. A 202 receipt is not acknowledgment of physical motion.
Firmware publishes non-retained reported GPIO state independently.

Flyway: V1 initial schema, V2 auth tables, V3 AI detections/boxes, V4 IoT state.
No new migration accompanies the camera registry. Retain migration history;
do not edit applied versions or use destructive reset commands.

## Tests, build and verification

```bash
./gradlew --no-daemon test bootJar
./gradlew --no-daemon build
curl --fail http://127.0.0.1:9090/actuator/health
# Authorized read-only local checks, with private keys already exported:
curl --fail -H "X-SmartPhset-IoT-Key: $IOT_API_KEY" http://127.0.0.1:9090/api/iot/devices/grow-room-1/state
curl --fail -H "X-SmartPhset-Camera-Key: $SMARTPHSET_CAMERA_API_KEY" http://127.0.0.1:9090/api/cameras
git diff --check
```

`test` includes Testcontainers integration tests; there is no separate
`integrationTest` Gradle task. Containers are disposable, MQTT is disabled/mocked.
Reports: `build/reports/tests/test/index.html`, `build/reports/jacoco/test/html/index.html`.
On 2026-10-28 the full suite passed **42 tests, zero failures/errors/skips**;
`bootJar` passed on the normal host. These checks do not prove physical IoT loads.

The Dockerfile rebuilds the JAR in a Java 25 builder, uses a non-root Java 25 JRE,
`render` profile and bounded JVM heap/native settings. It has **no Docker HEALTHCHECK**;
health is verified externally with `/actuator/health`. It exposes container port
9090; host publication must remain loopback only. See [AWS runbook](docs/aws-redeployment.md)
for candidate build/tag/push, isolated image smoke testing, existing EC2 network,
private env, Nginx and rollback. No deployment/push has been performed for this release.

## Troubleshooting

- JDK mismatch: select JDK 25/toolchain; do not downgrade the Dockerfile to Java 21.
- Tests fail before running: Docker daemon access and downloadable PostgreSQL image are required.
- DB/Flyway startup failure: verify dev URL/password, schema history and PostgreSQL reachability. Do not run repairs blindly on Neon.
- 401: distinguish registry, IoT and ingestion keys; 503 can mean missing configured key or unavailable broker.
- 403 registry on AWS: current Nginx blocks it and the old JAR lacks it. It is not proof the new registry is broken.
- 404 history/state: no data for the exact camera/device ID; 200 may still be stale.
- MQTT unavailable: verify credentials/ACL, network and unique client ID. Never test using production command traffic.
- Worker unreachable: Spring registry is configuration only. AWS cannot reach laptop `127.0.0.1:8002`.

Keep `.env` private/ignored; examples contain placeholders only. Review staged
changes and rotate exposed credentials before any public GitHub release.
