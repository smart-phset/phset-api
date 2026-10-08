# Phase 7 — AI / Spring / PostgreSQL integration

Status: COMPLETE — implementation and real runtime acceptance passed.
Remote delivery pending commit/push; historical blockers below are superseded.
Research/inspection date: 2026-10-08.

## Git and user changes

Repository phset-api, workspace api/, origin https://github.com/smart-phset/phset-api.git.
Branch main; starting HEAD 3f511a0 (`add detection ingestion and retrieval endpoints`),
matching recorded origin/main. No branch switch/commit/push performed.
AI previous delivered checkpoint 8b4f97c675798cb257c6991c87f3b7882d2e1d03,
branch feat/live-camera, clean and matching recorded origin at Phase 7 start;
user confirms successful external push. Live remote not independently fetched.

The sole pre-existing backend modification was a standalone `z` before server:
in application.yaml. User explicitly approved removing only that stray character.
It now matches committed configuration; no secrets/other settings changed.

## Inspected contract/security/schema

Reviewed controller/service/DTOs/entities/repositories, V1/V2/V3 migration names
and V3 content, security chain/JWT stub, application config, Gradle/dependencies,
all existing AI backend tests and Python publisher/camera/verdict tests.
Default port 9090, DB_URL default jdbc:postgresql://localhost:5432/smartphset,
Flyway enabled, Hibernate validate, UTC JDBC.

POST /api/ai/detections; latest is GET /api/ai/detections/latest?camera=...;
history is GET /api/ai/detections?camera=...&limit=..., NOT /history.
Python payload matches snake_case DTO: event_id UUID, camera_id, captured_at,
verdict/severity/message, n_contaminated/n_healthy, max_conf/max_contaminated_conf,
width/height/inference_ms and boxes(label/conf/integer xyxy). No renaming needed.
Shared fixture records full .799999999 contamination precision and microsecond UTC.

Current SecurityConfig permits detection routes and exempts POST from CSRF,
leaving anyRequest authenticated. Controller uses app.ai.ingest-key from
AI_INGEST_KEY; Python SMARTPHSET_AI_INGEST_KEY supplies X-SmartPhset-AI-Key.
Blank/unset backend key accepts ingest from any reachable origin (not loopback
restricted); intended for isolated local dev only. Configured wrong/missing key
returns 401; public latest/history unchanged. Prior reported 403 cannot be
reproduced with a running backend here; no security weakening/change made.

V3 defines unique event_id, TIMESTAMPTZ capture/create, scans and box FK/cascade,
confidence constraints. Service uses PostgreSQL transaction advisory lock plus
unique key, first 201 / duplicate valid event 200 with original record. History
orders captured_at/created_at/id DESC and filters camera, limits 1–100. No migration
change justified by inspection. Runtime DB application/persistence not yet proven.
Publisher does not retry failed snapshots, remains single-worker/one pending and
nonblocking. Backend rejects out-of-bounds boxes; publisher currently does not clip
provider coordinates. No speculative contract/runtime changes added.

## Research

- https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html:
  current 7.1.1 docs explain unsafe-method default CSRF and selective exemption;
  existing narrow machine POST exemption is appropriate, no global disable.
- https://java.testcontainers.org/modules/databases/postgres/: official PostgreSQL
  container module matches current test approach; keep PostgreSQL 18 tests.
- https://www.postgresql.org/docs/current/explicit-locking.html#ADVISORY-LOCKS:
  transaction-level advisory locks last for transaction, matching idempotency code.
- No external versions upgraded. Project Boot 4.1.1 / Java 25 and existing dependencies
  preserved. Did not claim newly checked latest releases for unrelated libraries.

## Files and validation

Modified: AiDetectionContractTests.java (actual Python fixture deserialize/validate/
round-trip); AiDetectionIntegrationTests.java (camera isolation, microsecond capture,
box persistence, tied timestamp UUID ordering, invalid verdict/severity/timestamp).
Created: src/test/resources/ai-detection.json; this work log.
AI additions: matching fixture, golden contract/200 acknowledgement tests, opt-in
real publisher acceptance test and docs/backend-integration.md. No backend runtime
or migration changes. Fixture SHA256 in both repos:
1a251203c28c3d45975547eb6fb4f655785222f134e46744fd1ebc54818fb837.

- git diff --check PASS.
- Modified Java tests compile with Java 25 javac using cached binary jars and
  existing compiled production classes. Limited compile only, not Gradle full build.
- Three direct contract test method invocations PASS (existing round-trip/coercion
  checks plus actual publisher fixture); temporary runner outside repo, not a
  substitute for JUnit/Spring/database suite. Cached-classpath SLF4J no-binder warning.
- AI: focused contract 2 PASS, publisher 9 PASS; full 84 discovered / 83 PASS / 1
  opt-in live test SKIPPED, syntax PASS, uv check 58 compatible PASS.
- Gradle wrapper first failed writing read-only home cache. Retried existing Gradle
  with writable copied /tmp cache and Java 25 Android Studio JBR: FAILED before
  tests, cannot determine usable wildcard IP for FileLockContentionHandler.
- Default Java is 21. Toolbox Java25 runtime lacks java.instrument/javac; full
  Android Studio Java25 JBR exists and was used for successful manual compile.
- docker info FAILED permission denied /var/run/docker.sock.
- Isolated /tmp PostgreSQL 18.6 initdb succeeded; pg_ctl FAILED creating TCP socket
  (Operation not permitted); no server left running. No existing/local DB modified.
- localhost:9090 probe returned HTTP 000; pg_isready no server response. No live
  POST/latest/history/auth or Flyway/DB success claimed. No live Roboflow/camera.

## Exact next actions / commit readiness

1. Resume without recreating tests. Read AI main work log and both Git statuses.
2. In a normal environment with Java25 and Docker: ./gradlew test and ./gradlew build.
3. Start local configured PostgreSQL/backend on 9090; run AI opt-in test using
   SMARTPHSET_INTEGRATION_BACKEND_URL=http://localhost:9090 and matching ingest key.
   Repeat keyed/unkeyed mode. Follow docs/backend-integration.md in AI repo.
4. Fix actual failures; record Gradle report counts, migration/table evidence and
   live acceptance results in both logs. Do not count skipped tests as success.
5. Only after every Phase7 gate passes, update logs COMPLETE, review/stage intended
   files per repository and commit independently. Proposed backend message:
   test(api): verify AI detection integration. AI message:
   test(ai): verify Spring detection publisher integration.
6. Push independently, verify delivery. Do not begin Phase8 MQTT integration until
   Phase7 has real evidence and delivery reconciled. No commits made this session.

## Timestamp precision investigation — 2026-10-08 (current resume state)

Status: IN_PROGRESS; fix implemented, full/runtime validation still blocked within
Codex. No commit/push. Prior Phase7 work preserved and resumed, not recreated.

User reports real PostgreSQL/Spring9090 now running in their normal environment;
live test reaches persistence and fails first/duplicate body equality, with
nanosecond vs microsecond timestamp text. Actuator403 is separate and not changed.
Git reconciled: backend main HEAD3f511a0, AI feat/live-camera HEAD8b4f97c; only
previously logged unfinished Phase7 files present before this investigation.

### Diagnosis and exact evidence limits

- Initial service response used response(s) on the original new entity after
  saveAndFlush. Duplicate response uses database-loaded entity from findByEventId.
- AiScan.createdAt initialized Instant.now() (nanoseconds), capturedAt copied from
  request; DTO exposes both as Instant. V3 columns TIMESTAMPTZ (microseconds).
- Live fixture captured_at is .123456Z, already exact microseconds. Therefore
  created_at is the likely differing field in the user's report; this is a
  source-based inference, NOT an independently captured live field diff.
- Actual live test rerun here failed before POST: PermissionError socket creation
  Operation not permitted. User runtime availability does not grant Codex socket
  access. Asked user to rerun diagnostic before restarting old backend; precise
  field-level comparison is now added to assertion message, equality unchanged.
- Official research checked: PostgreSQL18 datetime types (1us resolution);
  Jakarta Persistence3.2 EntityManager.refresh (reload managed entity from DB);
  Spring Data JPA entity-persistence docs (save can merge assigned-ID entities).
  URLs: https://www.postgresql.org/docs/current/datatype-datetime.html,
  https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/entitymanager,
  https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html.
- Independently ran isolated /tmp PostgreSQL18.6 in single-user mode (no TCP, no
  user DB changes): .123456100 -> .123456; .123456900 -> .123457;
  .999999900 -> next second. This proves rounding behavior, not API integration.

### Fix / regression

AiDetectionService now uses returned managed entity from scans.saveAndFlush(s),
then em.refresh(persisted), then response(persisted). Refreshing original s would
be unsafe because assigned UUID can cause merge. Let database/driver canonicalize
both timestamp fields; no naive Java microsecond truncation, schema change,
manual rounding, or loss of captured timestamp semantics beyond DB precision.
Transaction/advisory-lock/idempotency/status/auth/order/DTO remain unchanged.

Added initialDuplicateAndQueriesExposeDatabaseCanonicalTimestamps integration test:
sub-microsecond values below/above rounding threshold and second rollover; strict
whole JSON equality first201/duplicate200/latest/history; compare captured_at and
created_at to actual stored values, PostgreSQL CAST is rounding oracle; scans/boxes
counts checked. Pending Testcontainers runtime execution.

### Checks attempted/results

- Java25 javac service and changed integration test compile PASS with cached
  binary dependencies/existing main classes; deprecation note for Jackson asText,
  no compile error. Not a replacement for Gradle build/tests.
- AI isolated suite PASS83 / SKIP1 (84 discovered), 1.871s; syntax/diff PASS.
- Required Gradle focused '*AiDetection*', full test and build each attempted with
  Java25/writable cache/cached wrapper: all FAILED before task execution because
  FileLockContentionHandler cannot determine usable wildcard IP (sandbox).
- Live opt-in HTTP test FAILED before request due sandbox socket PermissionError.
  No DB-backed runtime pass claimed; user's running services left untouched.

### Exact next actions

1. Capture original live field differences using updated diagnostic test in normal
   terminal before restarting old backend; record exact fields/values, no keys.
2. Normal Java25/Docker terminal: ./gradlew test --tests '*AiDetection*',
   ./gradlew test, ./gradlew build. Fix genuine failures; retain strict equality.
3. Restart backend from corrected source with existing private configuration and
   keep PostgreSQL running. Rerun AI opt-in test against localhost9090, keyed and
   unkeyed as documented. Existing running JVM will not use source fix until restart.
4. Only once full/runtime gates pass update both logs COMPLETE and independently
   commit/push. Backend proposed message now fix(api): return persisted detection timestamps.
   AI proposed message remains test(ai): verify Spring detection publisher integration.
5. Phase8 prohibited until Phase7 fully green and delivery reconciled.

## Confirmed runtime field diff — 2026-10-08

User reran the diagnostic against real Spring/PostgreSQL and confirmed the ONLY
initial/duplicate difference is created_at:
- initial: 2026-10-08T10:27:48.472512510Z
- duplicate: 2026-10-08T10:27:48.472513Z
No other field differs. This replaces the earlier provisional field inference.
Evidence was supplied from the user's normal terminal, not captured by Codex.
PostgreSQL rounds this value upward; no truncation/ignored-field workaround used.

Reviewed the existing pending fix against that evidence: saveAndFlush's returned
managed entity is refreshed before mapping first201. Duplicate200 and strict whole
response equality remain unchanged. Regression compares both timestamps with the
stored row and first/duplicate/latest/history bodies, including sub-microsecond
capture inputs and second rollover. DTO/schema/auth/idempotency unchanged.

Retried focused backend AI tests, full Gradle test and build using Java25 and
writable cached Gradle home: all blocked BEFORE tasks by unusable wildcard IP.
Focused Python backend suites: 12 discovered, 11 PASS, 1 live SKIP. Manual Java25
compile of corrected service/test PASS; not a successful Gradle build. Diff checks
PASS. No commit/push; Phase7 remains IN_PROGRESS.

Next: in normal Java25/Docker terminal run ./gradlew test --tests '*AiDetection*',
./gradlew test, ./gradlew build. After successful build restart Spring from corrected
source, keeping PostgreSQL available; rerun the opt-in Python live integration test.
Do not mark complete or commit until required full/backend/live validation passes.

## Phase 7 final acceptance — COMPLETE (2026-10-08)

This final status supersedes the historical IN_PROGRESS/blocker entries above.
Implementation/runtime acceptance COMPLETE; remote delivery pending push attempt.
Both repositories reconciled; only intended Phase7 changes were present. No
validated source/test changes made after the successful real runtime acceptance.

### Real developer-environment validation

User ran and reported PASS: ./gradlew test --tests '*AiDetection*', ./gradlew test,
and ./gradlew build. Spring Boot started on localhost:9090 against PostgreSQL18.6.
Flyway connected, validated all 3 migrations, schema version3 current/up to date.
Generated local JUnit XML reports independently inspected: 13 tests total,
0 failures/errors/skips (3 contract,8 AI integration,1 local-mode,1 application).
initialDuplicateAndQueriesExposeDatabaseCanonicalTimestamps PASS in those reports.

Actual Python BackendPublisher -> Spring -> PostgreSQL acceptance PASS:
test_publisher_persistence_duplicate_queries_and_auth; 1 test in 1.264s, OK.
Confirmed persistence/boxes, duplicate first201/replay200, identical canonical
response bodies, latest/history/camera/limit and applicable ingest authentication.
Configured-key and open-development behavior also covered by passing backend tests.
This live validation was performed in the NORMAL DEVELOPER ENVIRONMENT because
Codex sandbox cannot bind/create localhost sockets; not an independently executed
Codex live pass. No additional live run required because integration code unchanged.

### Final fix and preserved contract

Only originally differing field was created_at: .472512510Z initial vs .472513Z
persisted duplicate. Initial POST now maps the managed entity returned by
saveAndFlush AFTER EntityManager.refresh, returning DB-canonical values. PostgreSQL
performs rounding; no manual Java truncation or ignored timestamp field. Strict
whole-response equality retained. DTO schema, event_id idempotency, 201/200,
authentication, ordering and migrations unchanged. Research official PostgreSQL
precision/JPA refresh/Spring Data merge behavior recorded above, no new dependency.
Shared payload fixture verifies exact schema and confidence precision.

### Final safe validation in Codex

Focused Python backend tests: 11 PASS,1 opt-in live SKIP (12 discovered).
Full AI suite: 83 PASS,1 opt-in live SKIP (84 discovered),2.513s.
Compileall and integration imports PASS; uv pip check PASS58 compatible packages.
Both repository diff checks PASS; only intended source/tests/fixtures/docs/logs
included, no generated reports/CSV/build outputs/secrets staged.
Final backend focused/full/build reruns attempted but blocked BEFORE task execution
by sandbox FileLockContentionHandler unusable wildcard IP. Successful normal-
environment results and generated XML above provide backend validation evidence.

### Limitations / intentional non-changes

No real ESP32 hardware acceptance in Phase7, no paid Roboflow inference, no model,
threshold, camera, backend payload schema, security policy or dependency changes.
Publisher remains bounded/best-effort and drops failed snapshots; outage tests pass.
Out-of-frame coordinates remain rejected by existing backend validation. Actuator403
is separate; unrelated security was not weakened. Blank ingest key is open local-dev
mode, configure privately before exposure. Synthetic acceptance leaves test records.

### Delivery and next exact actions

GitHub DNS preflight FAILED inside Codex: Could not resolve host github.com.
Commit validated changes independently and attempt each existing branch push.
If push fails retain commits, report exact SHA/branch/message in chat; no second
log-only commit/amend, and no Phase8 until external pushes are reconciled.
After delivery: Phase8 ESP32 sensor/actuator <-> MQTT <-> Spring integration;
read both logs/Git first and record previous Phase7 SHAs as normal Phase8 work.
Frontend remains Phase9; complete system acceptance Phase10.

Backend files modified: AiDetectionService.java, AiDetectionContractTests.java,
AiDetectionIntegrationTests.java. Files created: src/test/resources/ai-detection.json,
docs/ai-integration-work-log.md. No dependencies/schema/migrations/config changes.
Exact backend commit message: fix(api): return canonical persisted detection timestamps.
Message reflects the production correction; accompanying Phase7 tests verify the
broader payload/persistence/idempotency/query/authentication integration.
