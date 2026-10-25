# SmartPhset API

Spring REST backend for grow-room monitoring, MQTT actuator commands and stored AI detections.

## Role in SmartPhset

[Regular ESP32 firmware](https://github.com/smart-phset/smart-things) ↔ Mosquitto ↔ Spring ↔ PostgreSQL.
[Python AI](https://github.com/smart-phset/phset-ai/tree/feat/live-camera) sends detection JSON to Spring;
[Next.js](https://github.com/smart-phset/phset-client/tree/feat/grow-room-monitoring) proxies REST calls.
The [dedicated ESP32-CAM repository](https://github.com/smart-phset/web-cam) supplies
the raw camera stream to Python. Video goes directly from Python to Next.js, not
through Spring or PostgreSQL; the camera does not connect to MQTT.
See the frontend [full-system startup](https://github.com/smart-phset/phset-client/blob/feat/grow-room-monitoring/README.md#start-smartphset-from-a-fresh-computerreboot).

## Requirements

Linux: Git, a **JDK 25** (not just a JRE), Docker Engine with Compose v2, Bash.
Windows: Git for Windows, PowerShell, JDK 25, Docker Desktop configured for Linux
containers. Start Docker Desktop before Compose or tests. Native PowerShell is
supported; a WSL shell is not required.

`build.gradle` pins Spring Boot **4.1.1**, Java toolchain **25**, and springdoc **3.1.1**.
The checked-in Gradle wrapper downloads **9.7.1**; do not install a separate Gradle.
Compose uses PostgreSQL **18** and Eclipse Mosquitto **2**. Flyway runs migrations
at startup; Hibernate validates the schema. Do not create tables by hand.

Point `JAVA_HOME` at your installed JDK 25 directory (not its bin directory) and
put its bin directory on PATH. Confirm `java -version` and the wrapper version.
For example, using your actual installation path:

```bash
export JAVA_HOME="/path/to/jdk-25"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --version
```

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-25"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --version
```

## Clone

```text
git clone https://github.com/smart-phset/phset-api.git
cd phset-api
git switch main
```

## Configuration

Copy `.env.example` to private `.env`. Replace every `YOUR_*` value before startup.
Compose loads `.env` automatically; **Spring does not**. Database passwords now
have no shared source-code default: both POSTGRES_PASSWORD and DB_PASSWORD must
be supplied explicitly. Export the same file in
each Spring terminal using the setup below. Keep simple unquoted `NAME=value`
lines (no spaces around `=`, no inline comments); generated hex keys work in both shells.
All local values belong in ignored `.env`; only placeholders/defaults belong in `.env.example`.

| Variable | Purpose / example | Secret? |
|---|---|---|
| POSTGRES_DB / POSTGRES_USER | Compose database/user, `smartphset` | No |
| POSTGRES_PASSWORD | Database container password, `YOUR_DB_PASSWORD` | Yes |
| DB_URL | Spring JDBC URL, `jdbc:postgresql://localhost:5432/smartphset` | No |
| DB_USERNAME | Match POSTGRES_USER, `smartphset` | No |
| DB_PASSWORD | Match POSTGRES_PASSWORD, `YOUR_DB_PASSWORD` | Yes |
| IOT_API_KEY | REST header key, `YOUR_PRIVATE_IOT_KEY` | Yes |
| AI_INGEST_KEY | AI ingestion header key, `YOUR_PRIVATE_AI_KEY` | Yes |
| MQTT_ENABLED | `true`; false disables MQTT gateway | No |
| MQTT_BROKER_URL | `tcp://localhost:1883` from Spring host | No |
| MQTT_CLIENT_ID | Unique Spring MQTT client, `smartphset-api` | No |
| MQTT_DEVICES | Comma-separated allowed IDs, `grow-room-1` | No |
| MQTT_USERNAME / MQTT_PASSWORD | Empty for current anonymous broker; configure broker too if enabling auth | Password yes |
| AI_BASE_URL / AI_API_KEY | Configured legacy AI URL `http://localhost:8000` / optional key; not live ingestion settings | Key yes |
| JWT_SECRET | Reserved security configuration; JwtService is currently a stub, not frontend login | Yes |

`IOT_API_KEY` in Spring **must match** `SMARTPHSET_IOT_API_KEY` in Next.js.
`AI_INGEST_KEY` in Spring **must match** `SMARTPHSET_AI_INGEST_KEY` in Python.
A blank IoT key disables IoT REST (503). A blank AI key allows unauthenticated
local ingestion; set a key for team development. Next.js team login is configured
separately in the client repository.

### Generate development keys

Generate separate values for DB password, IoT key, AI key and frontend auth secret;
copy each privately to the matching configuration fields. Do not paste keys into Git.
Linux with OpenSSL installed:

```bash
openssl rand -hex 32
```

Windows PowerShell (uses .NET; OpenSSL is not required):

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
([System.BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
```

## Linux setup

One time (do not overwrite an existing private file):

```bash
cp .env.example .env
# Edit .env privately with your editor.
```

Each session, with Docker running:

```bash
set -a
source .env
set +a
docker compose up -d postgres mosquitto
docker compose ps
docker compose exec postgres pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"
./gradlew bootRun
```

## Windows setup

One time:

```powershell
Copy-Item .env.example .env
notepad .env
```

Each session, start Docker Desktop and wait for its engine, then:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
docker compose up -d postgres mosquitto
docker compose ps
docker compose exec postgres pg_isready -U $env:POSTGRES_USER -d $env:POSTGRES_DB
.\gradlew.bat bootRun
```

## Running

Order: Docker → PostgreSQL/Mosquitto ready → Spring. Keep the Spring terminal open.
Normal command is `./gradlew bootRun` on Linux or `.\gradlew.bat bootRun` on Windows.
Wait for the Started application message before starting the frontend.

| Service | Port | Purpose |
|---|---|---|
| Spring | 9090 | REST, AI ingestion, Swagger |
| PostgreSQL | 5432 | Database |
| Mosquitto | 1883 | MQTT for ESP32 and Spring |
| Mosquitto WebSocket | 9001 | Configured WebSocket listener |

Swagger: http://localhost:9090/swagger-ui/index.html; OpenAPI: http://localhost:9090/v3/api-docs.
Authorize with `iotKey` using your IoT key. Firmware must use the **laptop LAN IP**
for MQTT, not localhost; Spring on that laptop uses localhost. See the hardware guide.

## Testing

Docker must be available for Testcontainers PostgreSQL tests.

```bash
./gradlew test
./gradlew build
curl -H "X-SmartPhset-IoT-Key: $IOT_API_KEY" http://localhost:9090/api/iot/devices/grow-room-1/state
curl 'http://localhost:9090/api/ai/detections/latest?camera=webcam-0'
git diff --check
```

```powershell
.\gradlew.bat test
.\gradlew.bat build
Invoke-RestMethod -Headers @{ 'X-SmartPhset-IoT-Key' = $env:IOT_API_KEY } -Uri 'http://localhost:9090/api/iot/devices/grow-room-1/state'
Invoke-RestMethod 'http://localhost:9090/api/ai/detections/latest?camera=webcam-0'
git diff --check
```

Before the first board message or AI publish, a 404 for latest/state is expected.
Verify advancing timestamps, not merely HTTP success. See [IoT contract](docs/iot-integration.md)
and [AI contract](docs/ai-detection-contract.md) for payloads and optional integration checks.

## Common problems

- Docker daemon unavailable: start Docker Desktop/Engine; check `docker info` in
  the same terminal. On Linux ensure your user can access the Docker socket.
- Port 9090 occupied: stop the old Spring terminal with Ctrl+C. On Linux inspect
  `ss -ltnp 'sport = :9090'`; on Windows use
  `Get-NetTCPConnection -LocalPort 9090 -State Listen`, then inspect
  `Get-Process -Id <OwningProcess>` before `Stop-Process -Id <OwningProcess>`.
- PostgreSQL not ready: check `docker compose logs postgres` and `pg_isready`.
  Changing POSTGRES_PASSWORD after initialization does not change the existing
  volume's password. Use the existing password or deliberately administer it;
  do not delete a team database volume to fix credentials.
- MQTT unavailable: inspect `docker compose logs mosquitto`, port 1883 and
  MQTT_BROKER_URL. ESP32 requires the current laptop LAN IP and reachable network.
- 401 IoT: missing/wrong header key; compare private Spring and Next.js values.
  503 IoT state: IOT_API_KEY is blank. 503 command: MQTT unavailable/disabled
  can also be the cause. Read the response and Spring logs.
- Java/toolchain error: `java -version` alone may show an older system Java;
  install JDK 25 and set JAVA_HOME. No automatic toolchain downloader is configured.
- Gradle cache permission/DNS failures: use a writable GRADLE_USER_HOME and
  restore network access; don't change pinned versions to hide an environment error.

Mosquitto currently permits anonymous LAN access and the Compose ports are
published on the host. Use a trusted development network; these settings are not
an Internet deployment configuration.

## Git workflow

Start with a clean worktree (`git status`). Update the branch named in Clone before
creating your own feature branch. These commands work in Bash and PowerShell:

```text
git pull --ff-only
git switch -c feat/my-change
# Edit and run the checks above.
git status
git add README.md
# Add other intended files explicitly; never add private configuration.
git diff --cached
git commit -m "Describe the change"
git push -u origin feat/my-change
```

Open a Pull Request against the team's agreed integration branch. Do not push
teammate changes directly to main. Never use `git push --force` unless the team
explicitly agrees and understands why. If pull refuses, inspect local changes and
coordinate; do not discard work with a hard reset.

## Security notes

Never commit `.env`, `.env.local`, private `config.h`, Wi-Fi passwords, API keys,
DB passwords or auth secrets. Commit only safe example files. Inspect
`git diff --cached` before each commit; do not force-add ignored files. If a secret
was committed, rotate it and coordinate cleanup; deleting it does not revoke it.
