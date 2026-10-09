# Phase 8 — regular ESP32 / MQTT / backend integration

Status: PHASE 8 SOFTWARE INTEGRATION VERIFIED; PHYSICAL HARDWARE ACCEPTANCE PENDING.
Full Phase 8 remains INCOMPLETE; no Phase 9. See latest acceptance entry.
Final sandbox Gradle reruns blocked; completed real MQTT validation is preserved.

## Delivery reconciliation and initial inspection

AI feat/live-camera c2b8b0091d9c256c43cc51211c5ac1ad29923086 and backend main
 ae9963c38efec13ad71fbcd89de53f94c66e8bda are now externally pushed per user.
Both trees initially clean and matching recorded origin branches. Read prior AI/
backend logs, checked statuses/branches/logs/remotes. No fetch success claimed.
Phase7 runtime acceptance remains COMPLETE. AI is untouched during Phase8.

Backend inspected: complete tracked source/test inventory, build.gradle, YAML,
compose, Mosquitto config, security, migrations/entities/repos/controllers. MQTT
Paho1.2.5 and Spring Integration MQTT7.1.1 already installed/dependencies present;
only URL/credentials existed, no topic/handler/device entity/REST contract.
Broker compose eclipse-mosquitto:2, TCP1883/WebSocket9001, anonymous persistent
local broker. Configuration/auth unchanged; actual running broker inaccessible.

Regular ESP32 original smart-things/smart-things.ino inspected: DHT11 GPIO4,
soil34, outputs25/26/27, startupLOW, serial commands, blocking2s loop, no Wi-Fi/MQTT.
Preserved unchanged. User selected dedicated smart-phset/smart-things project;
initialized sibling folder as Git main (no remote added). New MQTT sketch alongside
original. Git ls-remote candidate URL failed DNS; web lookup cache miss. Remote
existence UNKNOWN, not assumed absent or present. No firmware commit until validated
and remote existence checked as user directed. No ESP32-CAM changes.

## Official research / decisions

Checked official sources before implementation (2026-10-08):
- https://eclipse.dev/paho/files/javadoc/org/eclipse/paho/client/mqttv3/MqttAsyncClient.html
  async connect/subscribe/publish, memory persistence/clean session; exact installed
  1.2.5 source/JAR inspected. IMqttAsyncClient force-disconnect has TWO args, not
  concrete-client three-arg overload; initial compile caught mismatch, fixed.
- https://github.com/eclipse-paho/paho.mqtt.java/releases : latest listed stable1.2.5;
  retain installed pin, no upgrade. Existing Java25 compile API verified.
- https://mosquitto.org/man/mosquitto-conf-5.html and official authentication docs:
  listener1883/9001, persistence/anonymous access; existing compose unchanged.
- https://docs.espressif.com/projects/arduino-esp32/en/latest/api/wifi.html and adc.html:
  STA Wi-Fi reconnect, ADC raw readings/resolution; classicESP32 GPIO34 is ADC1.
- https://github.com/espressif/arduino-esp32/releases : stable3.3.12 listed;
  firmware compile candidate only, toolchain not installed/combined runtime unverified.
- https://pubsubclient.knolleary.net/api and official releases/source:
  latest2.8; setBufferSize/SocketTimeout, length-based callback, QoS0 publish.
- https://github.com/adafruit/DHT-sensor-library/releases : latest1.4.7.
- https://github.com/bblanchon/ArduinoJson/releases and
  https://arduinojson.org/v7/api/json/deserializejson/ : latest7.4.3; JsonDocument,
  deserializeJson(payload,length), strict is<bool>/is<int64_t> input checks.
All firmware versions are documented build candidates, not claimed installed.
No cloud/hosted service/payment involved; no external dependency changes in backend.

## Implemented contract / persistence / security

New smartphset/devices/{device}/telemetry and /state JSON; commands/{fan|light|pump}
non-retained QoS0. Device allowlist MQTT_DEVICES, matching payload/topic ID required.
Commands explicit on:boolean + command_id + expires_at:integer epoch seconds (10s).
No command replay buffering;202 means transmission accepted, not physical success.
Firmware reports GPIO state separately (not sensed electrical load feedback).

V4 adds iot_device_state only, leaves appliedV1–V3 untouched. Atomic conditional
upserts keep telemetry and output timestamps independent; older/duplicate readings
cannot override newer state. Current state only (no telemetry history). Finite/range/
required-field/identity/timestamp/size validation; safe callback exception boundary.
Bounded async connection retries at5s including initial failure; one connection in
flight, clean-session resubscription, no preview/AI changes. Broker failure does not
prevent backend startup. Shutdown closes client/retry executor.

GET /api/iot/devices/{id}/state and POST /api/iot/devices/{id}/actuators/{actuator}
with {"on":false}. Narrow IoT machine routes require IOT_API_KEY header
X-SmartPhset-IoT-Key: unset key503, wrong/missing401, unknown device404, invalid400,
broker unavailable503. Existing security had no usable machine IoT route; this
feature-local key is required (no open actuation default), no JWT/login redesign.
Only those IoT paths permit controller authorization and POST CSRF exemption;
AI and unrelated rules unchanged. Broker anonymous authentication unchanged.

Firmware startup/reconnect/loss OFF, pump max10s cooperative timeout, no manual
relay energization by Codex. NTP required, sensor invalid data skipped, calibration
config examples only; credentials ignored config.h. Pin polarity must be checked
with loads disconnected; never direct-drive motor/relay loads from GPIO.

## Files

Created backend feature/iot: IotMessages, IotProperties, DeviceState,
DeviceStateRepository, IotIngestionService, MqttGateway, IotController.
Created V4 migration, tests IotIntegrationTests/IotIngestionTests/IotControllerTests/
MqttGatewayTests, tools/verify_mqtt_integration.py, docs/iot-integration.md and this log.
Modified README, application.yaml and SecurityConfig; existing three SpringBootTest
classes disable MQTT to avoid contacting real broker in automated suites.
Firmware files separately tracked in sibling CODEX_WORK_LOG.md.

## Validation and current blockers

PASS Java25 cached javac of new feature/tests/security. Five unit methods (MQTT
connect/retry/resubscribe/single-flight, commands/no offline queue, callback failure,
input validation/persistence boundary, controller auth/device boundaries) executed
directly with Mockito agent and PASS. SLF4J no-binder warning from combined cached
classpath; not a Gradle/JUnit/Spring/full build pass.
PASS isolated PostgreSQL18.6 single-user transaction: actual V4 DDL and exact source
upsert SQL, older telemetry ignored, sensor/output independence preserved, rollback.
No user DB/network touched. Initial temporary harness used wrong distro binary path
and multi-line single-user input; corrected to installed /usr/bin/postgres with
single-line command input. These were harness errors, no schema fix required.
PASS Python acceptance-script syntax/help and host C++ policy tests in firmware.
Backend diff check PASS. No generated Python cache retained. No secrets added.

Required focused Gradle '*Iot*'/'*Mqtt*', full test and build ATTEMPTED using Java25,
writable cached GRADLE_USER_HOME; blocked BEFORE tasks by unusable wildcard IP.
Docker ps/logs failed permission denied Docker socket. Thus real broker startup,
Spring V4/Hibernate validation, REST persistence and real MQTT publish have NOT yet
passed. Old Phase7 reports are not Phase8 success. Actual ESP32 build ATTEMPTED:
arduino-cli command not found. No upload, hardware, MQTT runtime claim.

## Exact next actions

1. In normal Java25/Docker terminal: ./gradlew test --tests '*Iot*' --tests '*Mqtt*',
   ./gradlew test, ./gradlew build. Fix any real failure, not skip tests.
2. Configure private IOT_API_KEY and MQTT_DEVICES=grow-room-1,phase8-test; keep existing
   DB/private config. Start Mosquitto and restart backend with V4. Run
   python3 tools/verify_mqtt_integration.py; verify broker reconnect checklist in docs.
   No paid inference or physical ON command needed for software test.
3. Install Arduino toolchain/library candidates and compile separate mqtt-firmware
   per sibling README. Keep original serial sketch unchanged. Report compiler output.
4. Physical board checks separately: calibration, sensors, output drivers/polarity,
   command handling, timeout, Wi-Fi/broker/reboot recovery, finish all loads OFF.
   Mark hardware acceptance PENDING until actual evidence, never imply full proof.
5. Confirm https://github.com/smart-phset/smart-things exists before firmware commit/
   delivery. DNS failure is not proof of nonexistence. No remote configured yet.
6. Only after required validation passes update logs accurately, stage intended
   files independently; suggested backend commit feat(iot): integrate MQTT sensor
   and actuator flow; firmware feat(esp32): add MQTT telemetry and actuator control.
   Push each existing/verified remote; if DNS fails report validated local SHA,
   no second log-only commit. No commits while build gates fail/unverified.
7. No Phase9 until Phase8 completion and delivery state reconciled. No frontend work.

## SHUTDOWN CHECKPOINT — 2026-10-08T18:01:47.390822+07:00

Status: Phase 8 INCOMPLETE / IN_PROGRESS. Stop for the day, not completion.
This checkpoint supersedes stale missing-tool/remote/build statuses above while
preserving the historical record. No new Phase8 implementation, commit, push,
stash, reset, untracked cleanup, hardware test or shutdown performed.

### Verified/preserved state

- Backend implementation present, uncommitted on main HEAD
  ae9963c38efec13ad71fbcd89de53f94c66e8bda (published Phase7).
- Backend focused IoT/MQTT tests PASS externally; full Gradle tests PASS externally;
  Gradle build PASS externally, reported by user. Local generated XML reports
  independently inspected:21 tests,0 failures/errors/skips, including8 IoT/MQTT.
- Mosquitto was running successfully in normal developer environment, TCP1883 and
  WebSocket9001 available, per user. No new Codex broker-runtime acceptance claim.
- arduino-cli installed via Arch package, version1.5.1 independently confirmed.
- smart-things repository created/pushed by user:
  https://github.com/smart-phset/smart-things.git, branch main tracks origin/main.
  HEAD and local origin/main both6fbeb94cf354272195b54545e01dc18b202eb3e1.
  Fresh remote lookup still fails github.com DNS in Codex; external publication
  confirmed by user and matching recorded tracking ref. Do not amend/rewrite it.
- Firmware commit was made BEFORE actual Arduino compilation/live acceptance.
  Firmware has NOT yet been compiled for ESP32. Host policy tests are not a substitute.
- Original smart-things.ino checksum unchanged:
  d45cbb2fc6d75b962e44613dd9112b51dbc3819d90d28be9dbefdd22d4fea546.
  Separate MQTT sketch, policy.h, config.example.h and README compile steps present.
- SmartPhset-AI feat/live-camera HEAD
  c2b8b0091d9c256c43cc51211c5ac1ad29923086 clean, no Phase8 changes.
- No real credentials detected in inspected backend diff/untracked source or tracked
  firmware; config.h not tracked. Environment/private credential files not backed up.
- No project Spring/bootRun process visible in this sandbox's /proc scan. No process
  killed; host processes may be outside its view. If a normal-terminal bootRun is
  still running, use Ctrl+C there. Containers/data remain intact; no volume deletion.

### Remaining acceptance (all pending)

ESP32 Arduino compile; real Mosquitto telemetry -> backend -> DB/state -> REST;
REST actuator -> MQTT observation; real broker command non-retention; malformed
telemetry runtime safety; relevant broker-unavailable/recovery check. Physical ESP32,
DHT11, soil, fan/light/pump, Wi-Fi/MQTT reconnect/reboot acceptance NOT performed.
Do not energize loads automatically; finish hardware tests all OFF. Do not mark
Phase8 COMPLETE or start Phase9 until completion/delivery conditions are met.

### Recovery / resume instructions

Backend primary copy is its current working tree. Recovery directory:
/home/ratanak/smart-phset/phase8-backup (see manifest and checksums there).
Contains binary tracked-diff patch plus relative-path copies of every intended
untracked Phase8 file, excluding build/.gradle/dependencies/private configs/secrets.
Firmware checkpoint log modification is separately patched/backed up; published
6fbeb94 is unchanged. No shutdown-only WIP commit. Checkpoints deliberately remain
uncommitted. Backups are disaster recovery, never apply blindly over current work.
Read manifest before any recovery; do not restart completed work or discard WIP.
Exact first action next session:
cat /home/ratanak/smart-phset/api/docs/iot-integration-work-log.md
Then read /home/ratanak/smart-phset/smart-things/CODEX_WORK_LOG.md completely.

## Useful commands — preserved from implementation/docs

Run in the indicated repository; this checkpoint does not execute installs,
compilation, firmware upload, live actuation or new acceptance work.

Backend (`/home/ratanak/smart-phset/api`):
```bash
git status
git status -sb
git branch --show-current
git log --oneline -10
git remote -v
./gradlew test --tests '*Iot*' --tests '*Mqtt*'
./gradlew test
./gradlew build
docker ps
docker logs --tail 30 smartphset-mosquitto
docker compose up -d postgres mosquitto
./gradlew bootRun
python3 tools/verify_mqtt_integration.py --backend-url http://localhost:9090
mosquitto_sub -h localhost -p 1883 -t 'smartphset/devices/phase8-test/#' -v
```

Before bootRun set correct local DB environment privately (DB_URL, DB_USERNAME,
DB_PASSWORD), MQTT_ENABLED=true, MQTT_BROKER_URL=tcp://localhost:1883,
MQTT_DEVICES=grow-room-1,phase8-test and private IOT_API_KEY. Never put secret
values in this log or command output. Script uses IOT_API_KEY from environment;
REST header is X-SmartPhset-IoT-Key. Do not overwrite existing private .env.

Actual mosquitto CLI invocation templates used by verify_mqtt_integration.py
(the angle-bracket values are script-generated placeholders, not literal input):
```text
mosquitto_pub -h localhost -p 1883 -q 0 -t smartphset/devices/phase8-test/telemetry -m '<JSON built by script with current captured_at>'
mosquitto_pub -h localhost -p 1883 -q 0 -t smartphset/devices/phase8-test/state -m '<OFF-state JSON built by script with current captured_at>'
mosquitto_sub -h localhost -p 1883 -t smartphset/devices/phase8-test/commands/fan -q 0 -C 1 -W 10
```
Use the documented Python script for executable end-to-end software acceptance.
It sends fan OFF only. The script does not itself prove retain=false; explicitly
verify non-retention with a fresh subscriber after command publication next session.
Malformed telemetry and broker unavailable/recovery checks remain pending.
Do not interrupt the broker while real equipment depends on it.

Firmware (`/home/ratanak/smart-phset/smart-things`), copied from README:
```bash
git status
git status -sb
git branch --show-current
git log --oneline -10
git remote -v
arduino-cli version
arduino-cli core update-index --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core install esp32:esp32@3.3.12 --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli lib install 'DHT sensor library@1.4.7' 'PubSubClient@2.8' 'ArduinoJson@7.4.3'
# Only if config.h does not already exist: copy the template, then edit privately.
cp mqtt-firmware/config.example.h mqtt-firmware/config.h
arduino-cli compile --fqbn esp32:esp32:esp32 mqtt-firmware
```
Do not overwrite an existing private config.h. It is ignored and deliberately
excluded from recovery backups. No need to reinstall core/libraries already present;
inspect installed state first. No upload or hardware ON command at shutdown.

## NEXT SESSION — PHASE 8 RESUME ORDER

1. Read the entire work log.
2. Reconcile Git state in api and smart-things.
3. Confirm Docker PostgreSQL and Mosquitto.
4. Follow smart-things/README.md and compile firmware with arduino-cli.
5. Fix compilation defects if any; do not amend 6fbeb94.
6. Follow api/docs/iot-integration.md.
7. Start backend with the correct local DB/MQTT environment.
8. Test real MQTT telemetry:
   publisher -> broker -> backend -> DB/state -> REST readback.
9. Test real actuator flow:
   REST -> backend -> MQTT -> subscriber.
10. Verify command retain=false.
11. Verify malformed telemetry remains safe.
12. Perform safe broker failure/recovery test if documented.
13. If board is available, perform ESP32 hardware acceptance.
14. Only after software acceptance passes, finalize Phase 8 commits.
15. Phase 9 frontend integration begins only after Phase 8 delivery is reconciled.

## Phase 8 resumed — normal-environment runtime results (2026-10-08)

User-reported normal-environment evidence, not re-executed in Codex:
- PostgreSQL and Mosquitto running; Spring Boot started on port 9090.
- Flyway V4 (iot_device_state) applied successfully.
- ESP32 core 3.3.12; DHT 1.4.7, PubSubClient 2.8, ArduinoJson 7.4.3 installed.
- MQTT firmware compiled: 949265 bytes program storage (72%); 48628 bytes
  dynamic memory (14%). Private mqtt-firmware/config.h is Git-ignored.
- Real integration verifier PASS: telemetry/state -> Mosquitto -> Spring ->
  PostgreSQL/current state -> REST; REST fan OFF -> Spring -> MQTT observation.
  No physical response asserted. These completed checks must not be restarted.
- Temporary local test API key was weak; its value is deliberately omitted.
  Replace with a generated private key before hardware acceptance; configure the
  backend and verifier privately with the same key. No secret recorded here.

Remaining software acceptance: command non-retention, malformed telemetry through
real broker, safe broker outage/reconnect. New tools/verify_mqtt_remaining.py in
api provides separate normal-terminal checks (synthetic phase8-test, OFF only).
Syntax/help checks PASS; new live checks NOT RUN here: localhost socket access
is denied, Docker socket inaccessible, IOT_API_KEY absent from this session.
Follow api/docs/iot-integration.md remaining-check procedure. Outage test only
when no equipment relies on broker; keep Spring running and always restart broker.

After software acceptance: identify board/serial port, verify private config,
LAN broker/device allowlist and relay polarity with loads disconnected; upload
compiled MQTT sketch, then record actual hardware checklist observations.
Physical upload/board/sensor/actuator acceptance remains PENDING. Do not claim
completion, amend/rewrite firmware 6fbeb94, or start Phase 9. Backend WIP preserved;
no staging, commit or push. Original shutdown recovery backup remains unchanged;
it predates this resume entry and the new verifier.

## BREAK CHECKPOINT — 2026-10-08T19:06:44.967713+07:00

Phase 8 INCOMPLETE. Preservation only; this section supersedes earlier pending
software statuses and resume orders. User requested a pause, not further debugging.

### Verified runtime state (normal-environment results reported by user)

PASS: backend focused IoT/MQTT tests; full backend tests; backend build;
PostgreSQL/Flyway V4 migration; ESP32 firmware compilation; real MQTT telemetry
-> Spring -> PostgreSQL/current state -> REST; real REST actuator OFF -> MQTT
command observation; command non-retention; malformed telemetry safety; broker
recovery after restart. Firmware: 949265 bytes flash (72%), 48628 bytes RAM (14%).
These passes do not prove offline HTTP behavior or physical hardware acceptance.

### CURRENT BLOCKER — real broker outage returns 403 instead of 503

Same authenticated actuator request with broker ONLINE: HTTP 202.
Then `docker stop smartphset-mosquitto`; docker inspect confirmed Running=false;
waited 10 seconds; same authenticated actuator request OFFLINE: HTTP 403.
Expected HTTP 503 Service Unavailable. Broker restart/recovery works afterward.
Do NOT change the contract to accept 403. Required behavior remains online 202,
unavailable 503, wrong/missing IoT key existing 401, unknown device existing 404.
Likely investigation areas (unconfirmed): MQTT disconnect-state tracking,
MqttGateway availability, exception mapping, Spring Security ERROR dispatch and
/error handling, and why the intended unavailable response becomes 403.
No outage fix investigated or implemented during this break checkpoint.

The temporary IOT_API_KEY was exposed and MUST be rotated before further non-local
testing. Its value is not recorded or backed up. Private config.h/.env/credentials
are excluded from recovery copies; preserve/configure them privately.

### Repository preservation

api main HEAD ae9963c38efec13ad71fbcd89de53f94c66e8bda: unfinished backend WIP
remains uncommitted. smart-things main published HEAD
6fbeb94cf354272195b54545e01dc18b202eb3e1: unchanged, never amend/rewrite; checkpoint
log remains modified. SmartPhset-AI feat/live-camera HEAD
c2b8b0091d9c256c43cc51211c5ac1ad29923086: clean, unchanged. All match local
upstream refs; no fresh remote verification claimed. No staging/commit/push,
stash/reset/clean, process/container shutdown, hardware testing or Phase 9 work.

Recovery directory: /home/ratanak/smart-phset/phase8-backup (refreshed current
patches, intended untracked files, manifests and SHA256SUMS.json). Older snapshot
preserved in a timestamped sibling archive. Primary copy remains working trees;
never apply backup blindly or overwrite newer work. Generated build/dependencies
and private configuration are excluded. Checkpoint checks are recorded in backup
CHECKS.txt after preservation.

### NEXT SESSION — exact resume action

Read both complete work logs and reconcile Git first. Then investigate and fix the
real MQTT broker-outage 403 -> expected 503 behavior, and rerun:
- focused IoT/MQTT tests: ./gradlew test --tests '*Iot*' --tests '*Mqtt*'
- full Gradle tests: ./gradlew test
- Gradle build: ./gradlew build
- real online 202 / offline 503 / recovery PASS validation

Only after that should physical ESP32 acceptance begin. Do not redo Phase 7,
restart Phase 8, start Phase 9, or mark hardware acceptance complete without actual
board flash/testing evidence. Stop work now after checkpoint preservation.

## Phase 8 outage defect resume — 2026-10-08

Read both work logs and reconciled repository heads against the latest break:
api main ae9963c38efec13ad71fbcd89de53f94c66e8bda and smart-things main
6fbeb94cf354272195b54545e01dc18b202eb3e1 unchanged; existing WIP preserved.
Completed Phase 8 acceptance checks were not repeated. No Phase 7/9 work,
firmware build/upload, hardware actuation, staging, commit or push.

Reproduced the HTTP masking path in new IotErrorDispatchTests: controller returns
503 for BrokerUnavailableException, but servlet ERROR dispatch to Boot /error was
rejected with 403 by SecurityConfig.anyRequest().authenticated(). Existing
MockMvc integration tests checked only the first response and missed redispatch.
SecurityConfig now permits DispatcherType.ERROR before request-path rules. Direct
REQUEST access to /error and unrelated protected endpoints still returns 403;
IoT controller key/device checks and MQTT connection logic remain unchanged.

Regression evidence: direct Java 25.0.4.1 compilation and JUnit Platform execution
with cached Spring Boot 4.1.1 dependencies, existing compiled main classes and
Mockito agent. Before fix: 3 tests, 1 pass, 2 failures (503 and 401 masked as 403).
After fix: all 3 PASS, including 503, 401/404 error rendering and protected ordinary
requests. This is a Spring MVC/security slice with explicitly simulated container
ERROR dispatch, not a live broker or embedded-server acceptance claim. Temporary
runner/output under /tmp/phset-outage-check; existing Gradle reports not replaced.

Focused IoT/MQTT Gradle tests, full tests and build ATTEMPTED with Java 25 and a
writable /tmp/phset-outage-gradle cache copy; all blocked BEFORE tasks by
"Could not determine a usable wildcard IP for this machine." Docker ps denied
access to /var/run/docker.sock. No approval bypass attempted. Live outage retest
NOT RUN; do not mark the real defect validated yet. No broker/process stopped.

Added --check available (synthetic fan OFF, asserts HTTP 202) to the existing
remaining-check verifier and documented an isolated 202 -> 503 -> 202 procedure
in api/docs/iot-integration.md. Python syntax/help PASS. This avoids rerunning the
completed telemetry/non-retention/malformed checks. New available live check has
not run. Rotate the previously exposed key privately before non-local testing.

NEXT: in the normal Java25/Docker terminal run focused IoT/MQTT tests, full tests,
and build; restart Spring with changed code/private configuration; use the latest
HTTP regression retest procedure (only with no equipment depending on broker).
Record online202/offline503/recovery202 evidence. Physical ESP32 acceptance remains
PENDING. Phase 8 remains INCOMPLETE; published firmware commit is untouched.

## Phase 8 software acceptance VERIFIED — 2026-10-08

User supplied final normal-environment runtime evidence after the ERROR-dispatch
fix. Root cause: Spring Security blocked servlet ERROR dispatch to /error,
masking the intended 503 with 403. Fix: permit DispatcherType.ERROR before path
rules; ordinary requests and controller IoT authorization retain their checks.
Fix VERIFIED in real runtime:
- Broker ONLINE: authenticated actuator POST -> HTTP 202.
- docker stop smartphset-mosquitto; inspect Running=false; waited 10 seconds.
- Broker OFFLINE: same authenticated POST -> HTTP 503.
- docker start smartphset-mosquitto; waited 8 seconds.
- Recovered: tools/verify_mqtt_integration.py --backend-url http://localhost:9090
  PASS: real MQTT telemetry/state persistence and backend OFF command observed.
No physical device response asserted. Evidence is user-reported from the actual
normal developer environment, not a new Codex runtime execution.

PHASE 8 SOFTWARE INTEGRATION VERIFIED.
PHYSICAL HARDWARE ACCEPTANCE PENDING. Full Phase 8 remains INCOMPLETE.
This supersedes prior pending live software-outage/recovery status. No completed
real MQTT validation repeated; no production/firmware source changed this turn.
No Phase 9, staging, commits or pushes.

Requested final safe checks:
- Focused IoT/MQTT tests, full Gradle tests, Gradle build each ATTEMPTED with Java25
  and writable cached GRADLE_USER_HOME. Each blocked before tasks by sandbox
  "Could not determine a usable wildcard IP for this machine." These final reruns
  are NOT claimed passed; earlier reported normal-environment passes remain recorded.
- git diff --check PASS in api and smart-things.
- Firmware policy tests: c++ -std=c++17 -Wall -Wextra -Werror, executable PASS.
- Heuristic secret scan of 66 non-ignored text files across both repositories:
  no detected secret literals after review. Two candidate README matches were
  PGPASSWORD="$DB_PASSWORD" shell references, not credentials. Scan included
  private-key markers, AWS/GitHub token patterns, JWTs and credential assignments;
  this is not an exhaustive guarantee. .env and config.h remain Git-ignored;
  no secret values printed, copied or changed.

Hardware inspection: arduino-cli board list could not start serial/DFU discovery
(operation not permitted / libusb error); no serial device paths visible. This
cannot establish whether a board is connected in the host environment. No upload,
serial monitor, physical response or hardware acceptance performed here.
Added smart-things/HARDWARE_ACCEPTANCE.md with normal-terminal port discovery,
load-disconnected upload/polarity/startup checks, serial/MQTT/time/sensor/backend
observations, per-actuator tests, pump timeout and reboot/reconnect evidence table.
Firmware has no connection-success serial banners; use fresh MQTT timestamps and
backend readback for evidence. Never infer physical state solely from GPIO JSON.

NEXT: normal-terminal final Gradle checks (sandbox cannot execute them); follow
physical acceptance guide on the actual board. Record each actual observation,
finish outputs OFF. Do not repeat completed software MQTT acceptance unless code
changes; do not mark full Phase 8 COMPLETE until hardware acceptance is done.

## FINAL PAUSE CHECKPOINT — 2026-10-08T19:38:56+07:00

PHASE 8 SOFTWARE INTEGRATION VERIFIED.
PHYSICAL HARDWARE ACCEPTANCE PENDING. Full Phase 8 is NOT COMPLETE.
This checkpoint supersedes earlier resume instructions and hardware-discovery/
upload pending statuses. Evidence below was reported by the user from the normal
developer environment; it was not re-executed during this pause.

Verified in the normal developer environment:
- Broker online actuator request -> HTTP 202.
- Broker offline actuator request -> HTTP 503.
- Broker restored -> full MQTT integration verifier PASS.
- Firmware compilation PASS.
- ESP32 firmware uploaded successfully to /dev/ttyUSB0.
- Detected hardware: ESP32-D0WD-V3 via CP2102.
- Firmware boots and runs.
- Wi-Fi/MQTT execution path is reached.
- No sensors are currently connected.
- Serial repeatedly reports: "Sensor/time unavailable; telemetry skipped".
  This is expected because DHT11/soil sensors are not connected yet. The message
  does not independently establish NTP sync or successful real sensor telemetry.

Current hardware acceptance state:
- ESP32 discovery ✅
- Firmware upload ✅
- Firmware boot ✅
- DHT11 ⏳ not connected
- Soil sensor ⏳ not connected
- Real sensor telemetry ⏳
- Fan/light/pump hardware ⏳
- Actuator physical acceptance ⏳

Preservation: all existing work remains in the working trees, including uncommitted
backend Phase 8 files, both work logs and HARDWARE_ACCEPTANCE.md. Only these two
work logs were changed for this checkpoint. No staging, commit, push, stash, reset,
cleanup, backup overwrite, private configuration change, process/container stop,
firmware upload or hardware actuation performed during this pause. Existing
recovery backups remain untouched and predate this checkpoint; never apply them
blindly over the current working trees. No completed MQTT validation repeated.
Safe local checks only: git status inspected; git diff --check PASS in api and
smart-things after updating this checkpoint. No new Gradle or firmware tests run.

NEXT SESSION: read both work logs, use this latest checkpoint and preserve current
WIP. Resume by connecting DHT11 and soil sensor safely with board power removed
and actuator load power disconnected; follow smart-things/HARDWARE_ACCEPTANCE.md.
Then verify real sensor telemetry from grow-room-1 and matching backend state,
then proceed to actuator hardware acceptance, including startup OFF, pump auto-OFF
and reboot/reconnect safety. Record actual observations and finish outputs OFF.
Do not redo completed software MQTT acceptance unless code changes. Do NOT mark
full Phase 8 COMPLETE until hardware acceptance is done. Do NOT start Phase 9.

Pause after saving this checkpoint. SAFE TO CLOSE CODEX.


## HARDWARE ACCEPTANCE CHECKPOINT — 2026-10-09

PHASE 8 SOFTWARE INTEGRATION VERIFIED.
PHYSICAL HARDWARE ACCEPTANCE IN PROGRESS; full Phase 8 INCOMPLETE. No Phase 9.
This entry supersedes earlier DHT11-pending and soil-first resume instructions.
Evidence below is user-reported physical observation, not re-executed by Codex.

Verified:
- ESP32 detected on /dev/ttyUSB0, identified as ESP32-D0WD-V3 via CP2102.
- Firmware uploaded successfully; boots and runs normally.
- Blue 3-pin DHT11 module: VCC -> ESP32 3.3V, GND -> GND, DATA -> GPIO4.
- Stable, plausible DHT11 readings: temperature 25.30 C; humidity 69.00%.
- No abnormal heating observed.

| Acceptance area | Status |
|---|---|
| ESP32 hardware | VERIFIED |
| Firmware upload/boot | VERIFIED |
| DHT11 | VERIFIED — reported stable/plausible readings |
| Soil sensor wiring / ADC validation | DEFERRED / PENDING — hardware unavailable |
| Real soil calibration | DEFERRED / PENDING |
| Fan/light/pump hardware | PENDING |
| Actuator safety tests | PENDING |
| Full Phase 8 | INCOMPLETE |

Soil sensor is not available or connected. Any readings from disconnected GPIO34
are floating ADC values and MUST NOT count as soil acceptance or calibration.
DHT11 acceptance does not establish matching grow-room-1 MQTT/backend/REST
readback; that physical telemetry path remains pending explicit evidence. Firmware
currently includes a soil field even with no soil sensor; ignore that field for
acceptance. Do not infer valid soil hardware from successful JSON ingestion.

Soil-specific acceptance is deferred without blocking independent actuator tests.
NEXT: identify actual relay/driver modules, input compatibility, supply and polarity
with ESP32 power removed and all actuator loads disconnected. Then proceed one
physical step at a time, waiting for user observations: disconnected-load output
checks, fan/light/pump commands, startup OFF, measured pump auto-OFF, and physical
Wi-Fi/MQTT disconnect/recovery/reboot safety. Record DHT11 MQTT/backend/REST
readback separately when available; complete soil wiring/ADC/calibration later.
Never infer physical output behavior solely from GPIO JSON or HTTP 202.

Preservation: repository branches/HEADs unchanged (api main ae9963c; smart-things
main 6fbeb94). Existing WIP and backups preserved. Only both Phase 8 work logs and
HARDWARE_ACCEPTANCE.md updated; no source/private config changes, staging, commit,
push, upload or actuation. Completed software/MQTT validation was not repeated.


## DHT11 end-to-end acceptance and local Swagger — 2026-10-09

PHASE 8 SOFTWARE INTEGRATION VERIFIED. DHT11 REAL END-TO-END HARDWARE PATH
VERIFIED. Full Phase 8 INCOMPLETE; soil and actuator physical acceptance DEFERRED.
This entry supersedes earlier pending DHT11 MQTT/backend/REST and actuator statuses.

Read both full Phase 8 logs before changes; reconciled actual local WIP, not only
committed main. api main ae9963c38efec13ad71fbcd89de53f94c66e8bda; smart-things main
6fbeb94cf354272195b54545e01dc18b202eb3e1, both match local upstream refs. Existing
backend WIP preserved. AI feat/live-camera and frontend main clean. No fresh remote
verification, commits, staging, push, backup application, firmware change or Phase 9.

User-reported normal-environment hardware evidence (not re-executed here):
- Available: ESP32 DevKit, DHT11 3-pin module, ESP32-CAM, breadboard.
- ESP32-D0WD-V3 via CP2102 on /dev/ttyUSB0; compiled/uploaded/boots normally,
  no abnormal heating. DHT11 VCC=3.3V, GND=GND, DATA=GPIO4.
- Laptop Wi-Fi network change left MQTT_HOST pointing at its previous LAN IP.
  Private firmware config corrected to 192.168.1.200, port 1883, grow-room-1;
  firmware recompiled and uploaded successfully. No private config read or changed
  by Codex this turn; no Wi-Fi passwords or API keys recorded.
- Real grow-room-1 MQTT state: fan=false, light=false, pump=false, fresh captured_at.
  Telemetry example: temperature_c=24.8, humidity_percent=55, fresh captured_at.
  Verifies ESP32 Wi-Fi, Mosquitto connection, device ID, synced NTP/time, state
  publishing and real DHT11 -> ESP32 -> MQTT telemetry.
- Correctly authenticated GET /api/iot/devices/grow-room-1/state returned real
  temperature_c=24.9, humidity_percent=54.0, outputs=false and fresh independent
  state/telemetry captured_at and received_at timestamps. Verifies DHT11 -> ESP32
  -> Wi-Fi -> Mosquitto -> Spring MQTT ingestion -> PostgreSQL/current state -> REST.

Soil sensor is absent. soil_moisture_percent=100 is invalid floating GPIO34 data.
Soil wiring, ADC validation, calibration and real moisture telemetry DEFERRED;
never count JSON ingestion or that field as acceptance. Relay/MOSFET drivers,
fan, light, pump and multimeter unavailable. Driver compatibility/polarity, wiring,
physical fan/light/pump tests, startup physical OFF, physical pump auto-OFF and
physical disconnect/reboot safety all DEFERRED. MQTT booleans are not physical
acknowledgement or measured OFF. Updated HARDWARE_ACCEPTANCE.md accordingly.

Swagger implementation:
- Existing springdoc 3.1.1 dependency retained; no build.gradle changes.
- Added OpenApiConfig: OpenAPI component schemes iotKey (X-SmartPhset-IoT-Key)
  and aiKey (X-SmartPhset-AI-Key), plus operation metadata customizer. No environment
  or secret values are read into documentation. IoT tagged IoT, AI tagged AI Detection.
- IoT operations require iotKey in documentation; Authorize injects the header.
  Removed duplicate manual header inputs. State is latest/current, not history;
  POST lists fan/light/pump and defaults example to {"on":false}; documents 202 as
  accepted MQTT transmission only, 503 unavailable, 401 key errors, 404 unknown/no
  state and 400 invalid command. Existing route parameter remains {device}.
- AI ingestion retains its existing conditional key check. Its separate scheme is
  documented with an anonymous alternative and explicit configured-key condition;
  AI reads do not inherit ingestion security. No AI/controller auth source changed.
- SecurityConfig only adds documentation matchers /swagger-ui/**, /swagger-ui.html,
  /v3/api-docs and /v3/api-docs/**. Existing narrow machine-route controller auth,
  CSRF exemptions, anyRequest().authenticated() and ERROR-dispatch permission
  preserved. No /api/iot/** wildcard permission added. Direct /error stays protected.
- Added IotOpenApiTests using real springdoc/security/resources and mocked backend
  services, without broker/database. Documented local UI and OFF-only usage in
  api/docs/iot-integration.md. Official springdoc reference: https://springdoc.org/faq.html.

Validation:
- Java25.0.4.1 direct compilation of updated config and focused tests PASS, using
  cached dependencies and existing compiled main classes. Temporary harness in
  /tmp/phset-swagger-check; production Gradle reports not overwritten.
- Direct JUnit Platform: IotOpenApiTests 4 PASS + IotErrorDispatchTests 3 PASS =
  7 tests, 0 failures, 0 errors/aborts/skips. Actual generated OpenAPI and Swagger
  HTML/CSS/JS/config routes verified without keys; separate header schemes, IoT
  operation security, actuator enum/OFF example, optional AI security, and absence
  of configured synthetic test keys in generated document checked. IoT missing/
  wrong keys ->401, available command ->202, unavailable ->503, unknown ->404,
  unrelated routes/direct /error ->403; ERROR redispatch retains 503 and 401/404.
  Initial additional enum assertion used a wrong JsonPath selector (only first enum
  item); corrected assertion. An experimental TestConfiguration caused Boot to
  include the real application and fail on absent JdbcTemplate; replaced it with
  an explicit Configuration marked TestComponent so other suites cannot scan it.
  Final direct rerun: 7/7 PASS. No production MQTT/auth defect found.
- Focused Gradle Swagger/error tests ATTEMPTED first, then required
  ./gradlew test --tests '*Iot*' --tests '*Mqtt*', ./gradlew test, ./gradlew build
  with Java25, writable /tmp/phset-swagger-gradle, offline/no-daemon: each BLOCKED
  BEFORE tasks by "Could not determine a usable wildcard IP for this machine."
  No Gradle pass claimed; direct cached-classpath run is not a full build.
- git diff --check PASS in api and smart-things.
- No completed real Mosquitto/Docker acceptance repeated: metadata/doc-route changes
  do not alter MQTT runtime. No hardware test, ON command or broker interruption.

NEXT: normal developer terminal run the three required Gradle commands, restart
backend with current source/private config, open
http://localhost:9090/swagger-ui/index.html; Authorize iotKey privately, then GET
state with device=grow-room-1. OpenAPI JSON: http://localhost:9090/v3/api-docs.
Use OFF command examples for software testing; physical ON acceptance waits for
hardware/driver identification. Full Phase 8 remains INCOMPLETE; no Phase 9.


## Final software/DHT11 validation checkpoint — 2026-10-09

PHASE 8 SOFTWARE INTEGRATION VERIFIED. DHT11 -> ESP32 -> MQTT -> Spring ->
PostgreSQL/current state -> REST -> SWAGGER END-TO-END PATH VERIFIED.
FULL PHASE 8 REMAINS INCOMPLETE. No Phase 9.
This entry supersedes pending normal-machine Gradle and Swagger smoke-test actions
above; historical sandbox failures remain preserved as historical evidence.

User ran on the real developer machine:
- ./gradlew test --tests '*Iot*' --tests '*Mqtt*': PASS, including IotControllerTests,
  IotErrorDispatchTests, IotIngestionTests, IotIntegrationTests, IotOpenApiTests and
  MqttGatewayTests. Covers docs accessibility/security schemes, IoT key protection,
  success responses, broker unavailable503, unknown404, ERROR dispatch and MQTT
  reconnect/non-retention behavior.
- ./gradlew test: PASS, including all AI integration/contract tests.
- ./gradlew build: PASS.
- git diff --check: PASS.
Codex independently inspected the available Gradle XML reports: 28 tests, zero
failures/errors/skips across 10 suites (15 IoT/MQTT tests and 13 application/AI
tests). These are normal-environment reports, not a new sandbox Gradle execution.
Build and real Swagger observations below are user-reported, not re-executed here.

Real Swagger UI opened at http://localhost:9090/swagger-ui/index.html; iotKey
Authorize worked. GET /api/iot/devices/grow-room-1/state returned temperature_c=24.5,
humidity_percent=51, fan=false, light=false, pump=false and fresh independent
state/telemetry captured_at and received_at timestamps. Swagger -> authenticated
REST -> current real IoT state VERIFIED, completing the DHT11 path through Swagger.
POST /api/iot/devices/grow-room-1/actuators/fan with {"on":false} returned HTTP202
with generated command_id and expires_at. Swagger -> REST -> IoT key authentication
-> MQTT command acceptance VERIFIED; does NOT verify physical fan operation.

soil_moisture_percent=100 remains INVALID: no physical sensor on floating GPIO34.
DEFERRED due unavailable hardware: soil wiring, ADC validation, calibration and
real soil telemetry; relay/MOSFET compatibility/polarity/wiring; physical fan,
light and pump; physical startup OFF, pump auto-OFF, reboot/disconnect fail-safe.
Available ESP32/DHT11/Wi-Fi/MQTT/NTP/state publishing and real sensor path are
VERIFIED. No physical actuator pass inferred from output booleans or HTTP202.

Delivery preparation: reconciled api main ae9963c and smart-things main 6fbeb94,
with existing Phase8 WIP preserved and no pre-existing staged changes. Completed
backend IoT/MQTT/OpenAPI source, V4 migration, regression tests, software verifiers
and documentation are ready for a normal new backend commit. Firmware repository
changes are documentation only (this log and HARDWARE_ACCEPTANCE.md); published
firmware 6fbeb94cf354272195b54545e01dc18b202eb3e1 will not be amended or rewritten.
Private .env/config.h remain ignored, excluded from staging, unread and unchanged.
No further validation reruns, MQTT commands, source changes, uploads, broker stops
or Phase9 work performed for this checkpoint. Local diff checks passed.

NEXT: preserve these completed software/DHT11 results; physical soil/actuator
acceptance resumes only when hardware is available. Full Phase8 stays INCOMPLETE.
