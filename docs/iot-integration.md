# Regular ESP32 / MQTT / Spring integration (Phase 8)

Status: backend tests/build passed at shutdown; firmware compile and real MQTT
telemetry/OFF command flow passed in the normal environment per user. Remaining
software checks and physical hardware acceptance are pending.
ESP32-CAM and AI detection are separate; their code/contracts are unchanged.

## Architecture and configuration

DHT11 + soil ADC -> regular ESP32 -> Mosquitto -> Spring Paho listener -> PostgreSQL
`iot_device_state` -> REST current state. REST commands -> MQTT -> ESP32 outputs ->
reported state. A 202 command response means accepted for transmission, **not**
physical actuation or even device acknowledgement. Only incoming `state` updates
reported output state. This version stores current state, not sensor history.

Existing compose uses `eclipse-mosquitto:2`, TCP 1883 and WebSocket 9001, persistent
broker storage and anonymous access. No broker authentication changes were made.
Restrict this development broker to a trusted network. ESP32 uses broker LAN address,
not `localhost`; backend on the laptop uses `tcp://localhost:1883`.

Backend environment (credentials supplied privately, never in committed files):

- `MQTT_ENABLED=true` (false disables connection attempts in isolated tests)
- `MQTT_BROKER_URL=tcp://localhost:1883`
- `MQTT_CLIENT_ID=smartphset-api` (unique per backend process)
- `MQTT_USERNAME`, `MQTT_PASSWORD` optional, existing broker uses anonymous access
- `MQTT_DEVICES=grow-room-1,phase8-test` known-device allowlist
- `IOT_API_KEY` private REST read/control key; `X-SmartPhset-IoT-Key` header

IoT REST returns 503 when its key is not configured, 401 for missing/wrong key,
404 for unknown device or missing current state. These narrow IoT routes use
explicit machine-key authorization/CSRF exemption; unrelated security and AI
routes remain unchanged. This permits normal-terminal machine tests without
inventing a JWT login flow for this phase. MQTT device identity is an allowlist,
not cryptographic identity; existing anonymous broker is development-only.

## MQTT contract (new; no previous implementation existed)

| Topic | Producer | Payload | Retained / QoS |
|---|---|---|---|
| `smartphset/devices/{deviceId}/telemetry` | ESP32 | sensor JSON below | no / 0 firmware, backend subscription 1 |
| `smartphset/devices/{deviceId}/state` | ESP32 | reported outputs JSON | no / 0 firmware, backend subscription 1 |
| `smartphset/devices/{deviceId}/commands/fan` | backend | explicit command JSON | no / 0 |
| `smartphset/devices/{deviceId}/commands/light` | backend | explicit command JSON | no / 0 |
| `smartphset/devices/{deviceId}/commands/pump` | backend | explicit command JSON | no / 0 |

```json
{"device_id":"grow-room-1","temperature_c":25.8,"humidity_percent":87.0,"soil_moisture_percent":68.0,"captured_at":"2026-10-08T12:00:00.123456Z"}
```

```json
{"device_id":"grow-room-1","fan":false,"light":false,"pump":false,"captured_at":"2026-10-08T12:00:00.123456Z"}
```

```json
{"command_id":"generated-uuid","on":false,"expires_at":1791460810}
```

Command `expires_at` is **integer Unix epoch seconds**, generated 10 seconds ahead.
Device requires synchronized time, rejects expired/implausibly future commands,
rejects missing/malformed fields/unknown actuator and ignores its most recent
command ID per actuator. ON/OFF assignment is idempotent, never a toggle. No offline
command queue or retained actuation; requests fail 503 when broker/subscription is
unavailable. QoS0 offers no delivery guarantee; inspect subsequent reported state.

Device IDs: 1–64 ASCII letters/digits/underscore/hyphen, must be configured in
MQTT_DEVICES and match payload identity. Telemetry requires all finite readings:
temperature -20..60 Celsius, humidity/moisture 0..100 percent, timestamp at most
60 seconds ahead. Reported state requires all three actual booleans and timestamp.
Payload limit 2048 bytes. Invalid data is rejected without exposing raw payload or
credentials. No AI fields are mixed into telemetry.

Flyway **V4** creates current-state storage; V1–V3 remain unchanged. Each stream
updates atomically only when its timestamp is newer; duplicate/older data cannot
replace newer state. Telemetry and actuator timestamps are independent. Null
fields mean not yet reported, **not** OFF or zero. Received timestamps and capture
timestamps are exposed; old stored readings remain visible, so clients must check
age rather than treating historical values as currently fresh.

Backend connection attempts are asynchronous, at most one in flight, retried every
5 seconds including initial broker absence. Clean sessions resubscribe on each
connection; failed subscription forces reconnect. Callback failures do not escape
and kill the MQTT listener. No disconnected command buffering. Shutdown closes
client and retry executor. No broker availability requirement for application start.

## REST

- GET `/api/iot/devices/{deviceId}/state` -> latest known sensor/output fields
- POST `/api/iot/devices/{deviceId}/actuators/{fan|light|pump}`
  body `{"on":false}` -> 202 command ID/expiry, or 503 unavailable

Use private `IOT_API_KEY` and header `X-SmartPhset-IoT-Key` for both routes.
The header must match the backend value; never publish it in logs/screenshots.

## Normal-environment software validation (no board required)

```bash
docker ps
docker logs --tail 30 smartphset-mosquitto
./gradlew test --tests '*Iot*' --tests '*Mqtt*'
./gradlew test
./gradlew build
```

Tests use mocks and PostgreSQL Testcontainers; they do not contact your real broker.
Start PostgreSQL/Mosquitto normally (`docker compose up -d postgres mosquitto`),
configure private DB credentials, IOT_API_KEY and allowlist above, restart backend
on 9090 with the new V4 migration. Install `mosquitto_pub` / `mosquitto_sub` in your
normal environment, then:

```bash
python3 tools/verify_mqtt_integration.py --backend-url http://localhost:9090
```

The script requires IOT_API_KEY in its environment. It publishes synthetic sensor
and OFF state to isolated configured device `phase8-test`, verifies database-backed
REST state, invokes fan **OFF**, and observes the exact outbound command through
Mosquitto. It leaves the synthetic current-state row. It does not claim a physical
actuator responded. Subscribe separately to inspect traffic:

```bash
mosquitto_sub -h localhost -p 1883 -t 'smartphset/devices/phase8-test/#' -v
```

To verify actual backend reconnect, stop only Mosquitto in a developer environment,
confirm backend remains running/current state stays readable and commands return
503, restart broker, rerun script and confirm ingestion/commands recover. Do not
perform this interruption while real equipment depends on the broker.

## Hardware checklist

Firmware root: sibling `smart-things/`. Existing serial sketch preserved; MQTT
sketch is `mqtt-firmware/mqtt-firmware.ino`; see firmware README for exact setup.

| Function | GPIO |
|---|---|
| DHT11 data | 4 |
| Soil analog output (ADC1) | 34 |
| Fan driver | 25 |
| Light driver | 26 |
| Pump driver | 27 |

Use appropriate transistor/MOSFET/relay drivers, external power as required, common
ground and inductive-load protection. Never power a motor/pump/light load directly
from GPIO. Ensure soil analog output is within ESP32 input voltage limits. Verify
relay polarity without loads before applying power. Startup/reconnect/disconnect
outputs OFF; pump auto-OFF at configured limit (cooperative loop, not a certified
hardware safety timer). Calibrate wet/dry endpoints in your actual substrate.

- [ ] Board firmware compiles and uploads; no credentials logged/committed.
- [ ] Startup all outputs OFF; DHT11/soil readings plausible and calibrated.
- [ ] NTP sync, telemetry and state arrive and persist for correct device.
- [ ] Fan/light/pump command observed physically, reported state agrees.
- [ ] Pump auto-OFF; all loads OFF after each test.
- [ ] Wi-Fi loss, broker loss, broker recovery and ESP32 reboot recover safely.
- [ ] Malformed/expired commands do not energize any output.

No real ESP32 acceptance is claimed yet. Phase 8 cannot be fully proven from mocked
software tests. Frontend remains Phase 9 and has not started.

## Remaining Phase 8 normal-terminal checks

Keep the running backend and its private IOT_API_KEY environment. Do not print or
commit the key. The verifier uses only isolated phase8-test and fan OFF commands.
No physical response is inferred. Run in api/:

```bash
python3 tools/verify_mqtt_remaining.py --check non-retention
python3 tools/verify_mqtt_remaining.py --check malformed
```

The first observes a new OFF command, then requires a fresh subscriber to time out
without any retained message. The second checks eight invalid telemetry payloads
leave REST current state unchanged, then verifies a valid reading still persists.
Do not run concurrent publishers for phase8-test during these comparisons.

Only when no equipment relies on Mosquitto, run this outage/recovery sequence.
Leave Spring running throughout. The trap restores the broker on shell exit or
interruption; restart manually if the terminal is forcibly killed.

```bash
(
  set -e
  trap 'docker compose start mosquitto' EXIT
  docker compose stop mosquitto
  python3 tools/verify_mqtt_remaining.py --check outage
  docker compose start mosquitto
  sleep 10
  python3 tools/verify_mqtt_integration.py --backend-url http://localhost:9090
)
```

Successful final verification proves reconnect/resubscription and restored flows;
this rerun is recovery acceptance, not repeating initial acceptance. Record each
result in both work logs. Do not remove volumes or restart PostgreSQL/Spring.
Before physical upload, replace the weak temporary API key with a generated private
key and restart/configure backend accordingly. No key value belongs in work logs.
Hardware checks above remain pending until the actual board is flashed and tested.

## Broker-outage HTTP regression retest (latest checkpoint)

Telemetry, command observation, non-retention, malformed telemetry and firmware
compilation already passed; do not repeat them for this fix. Security now permits
servlet ERROR dispatches so Boot `/error` preserves 503/401/404. Ordinary requests
to `/error` remain protected. Restart Spring with the updated code and private key.
Run focused IoT/MQTT tests, full tests and build after this change, then test only
online 202 -> offline 503 -> recovered 202. With no equipment relying on the broker,
run in api/ with IOT_API_KEY configured privately:

```bash
(
  set -e
  python3 tools/verify_mqtt_remaining.py --check available
  trap 'docker compose start mosquitto' EXIT
  docker compose stop mosquitto
  python3 tools/verify_mqtt_remaining.py --check outage
  docker compose start mosquitto
  sleep 10
  python3 tools/verify_mqtt_remaining.py --check available
)
```

This uses only synthetic phase8-test fan OFF and leaves Spring running throughout
the outage. Recovery 202 proves command acceptance; previous completed telemetry
and subscription validation remains recorded separately. Record actual results;
physical hardware acceptance is still pending.

## Local Swagger UI

Restart the backend with the current source and existing private configuration,
then open http://localhost:9090/swagger-ui/index.html. The OpenAPI JSON is at
http://localhost:9090/v3/api-docs. Documentation routes are accessible without an
API key; the IoT controller still requires its existing machine key.

1. Click **Authorize**, enter your privately configured IoT key in **iotKey**,
   click **Authorize**, then close the dialog. Enter the value only, without a
   header name or Bearer prefix. Swagger sends `X-SmartPhset-IoT-Key` automatically.
2. Under **IoT**, expand GET `/api/iot/devices/{device}/state`, select **Try it out**,
   enter `grow-room-1` as `device`, then **Execute**. This reads current/latest
   state, not history. Check fresh telemetry/state timestamps.
3. For software command testing, expand POST
   `/api/iot/devices/{device}/actuators/{actuator}`, use `grow-room-1`, choose
   `fan`, `light`, or `pump`, and retain the example `{"on":false}`. HTTP 202
   means accepted for MQTT transmission, not physical acknowledgement. HTTP 503
   means MQTT/broker unavailable or the backend IoT key is unconfigured; missing/
   wrong keys remain 401, unknown devices remain 404, invalid commands remain 400.
   No physical ON tests while drivers and loads are unavailable.
4. The separate **aiKey** entry sends `X-SmartPhset-AI-Key` for AI ingestion. It is
   needed only when the existing backend ingest key is configured. IoT and AI keys
   are independent. AI reads do not require this ingestion key.

No key values are embedded in OpenAPI or source, and authorization persistence is
not enabled. Swagger/OpenAPI documentation uses springdoc's OpenAPI bean and
customizer support: https://springdoc.org/faq.html.

### Available hardware acceptance — 2026-10-09

User-reported real DHT11 -> ESP32 -> Wi-Fi -> Mosquitto -> Spring Boot ->
PostgreSQL/current state -> REST path VERIFIED. A stale broker LAN IP after a
laptop Wi-Fi change was corrected privately in firmware config; broker host is
192.168.1.200:1883 and device ID is grow-room-1. Recompile/upload succeeded.
Fresh real MQTT state reported all three outputs false; telemetry included
24.8 C / 55%. REST returned fresh 24.9 C / 54.0% with advancing captured/received
state and telemetry timestamps. These output booleans do not verify physical OFF.

No soil sensor is connected: any reported soil_moisture_percent=100 is an invalid
floating GPIO34 value. Soil wiring, ADC validation, calibration and real soil
telemetry remain DEFERRED. Drivers, fan, light, pump and multimeter are unavailable;
all actuator compatibility/wiring, physical tests, startup OFF, pump auto-OFF and
physical disconnect/reboot safety remain DEFERRED. Full Phase 8 INCOMPLETE; no
Phase 9. Completed real MQTT acceptance does not need repetition for these
metadata/documentation security changes.
