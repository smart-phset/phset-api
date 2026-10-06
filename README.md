# SmartPhset API: live AI detection ingestion

Java 25 / Spring Boot 4.1.1, PostgreSQL, Flyway. Backend defaults to
`http://localhost:9090`. Authentication/JWT remains outside this feature.

## Local backend

```bash
cd /home/ratanak/smart-phset/api
set -a
source .env
set +a
./gradlew bootRun
```

Flyway applies `V3__create_ai_detection_tables.sql`: `ai_scans` stores metadata,
`ai_detection_boxes` stores the associated boxes with cascading deletion.
Application code generates UUIDs. V1 and V2 remain unchanged.

## Optional ingest key

If `AI_INGEST_KEY` is unset or blank, POST accepts local requests without a key.
When set, POST requires the exact value in `X-SmartPhset-AI-Key`; an absent or
incorrect key returns HTTP 401. GET endpoints are public for this phase.

Set a private value in your shell or ignored `.env` (never commit real secrets):

```bash
export AI_INGEST_KEY='<your-local-key>'
# In the AI terminal, use the same private value:
export SMARTPHSET_AI_INGEST_KEY='<your-local-key>'
```

Only these detection routes bypass the existing default security restriction.
CSRF is ignored for the machine POST route. Other routes remain authenticated.

## Contract

`POST /api/ai/detections` with `Content-Type: application/json`:

```json
{
  "event_id": "f37a6c16-e11a-45af-a7df-b2d73128e41d",
  "camera_id": "webcam-0",
  "captured_at": "2026-10-06T11:25:00Z",
  "verdict": "contamination_suspected",
  "severity": "RED",
  "message": "Possible contamination",
  "n_contaminated": 1,
  "n_healthy": 0,
  "max_conf": 0.91,
  "max_contaminated_conf": 0.91,
  "width": 1280,
  "height": 720,
  "inference_ms": 84,
  "boxes": [{"label": "contaminated", "conf": 0.91, "xyxy": [120, 180, 450, 650]}]
}
```

First insertion returns HTTP 201 with the snapshot plus `id` and `created_at`.
Reusing an `event_id` returns HTTP 200 and the original stored snapshot, without
changing it or creating new boxes. A PostgreSQL transaction advisory lock plus
unique event constraint handles concurrent duplicates. The duplicate request must
still be valid. No video, JPEG or image data is accepted by this contract.

* `GET /api/ai/detections/latest?camera=webcam-0` returns the latest snapshot or
  HTTP 404 with `detection_not_found` when none exists.
* `GET /api/ai/detections?camera=webcam-0&limit=20` returns a JSON array, ordered by
  `captured_at` descending, then `created_at` and UUID descending to break ties.
  Limit must be 1–100; default 20. An empty history returns `[]`.
* Invalid input returns HTTP 400: `{"error":"invalid_detection","message":"..."}`.
* Key failure returns HTTP 401: `{"error":"unauthorized","message":"Invalid AI ingest key"}`.

Required fields, lengths, nonnegative counts/latency, finite confidence in [0,1],
positive dimensions and four integer box coordinates are validated. Boxes must
be ordered and within the image. JSON scalar coercion is disabled, including
fractional-to-integer truncation and numeric strings, through the shared JSON mapper.

Verdicts: `contamination_suspected`, `no_contamination_seen`, `no_detection`,
`camera_error`. Severity is stored as reported, with contradictory combinations
rejected. RED requires contamination confidence >= 0.80; AMBER requires >= 0.40
and < 0.80; GREEN requires healthy bags without contamination. Unavailable states
are GREY and never healthy. For compatibility with the working detector, lowering
YOLO's threshold below 0.40 permits GREY contamination below 0.40.
Severity validation uses **max_contaminated_conf**, never overall max_conf.

## Tests and verification

Docker must be accessible for Testcontainers; tests create their own PostgreSQL 18
containers and do not depend on manually running PostgreSQL.

```bash
./gradlew clean test
./gradlew clean build
curl 'http://localhost:9090/api/ai/detections/latest?camera=webcam-0'
curl 'http://localhost:9090/api/ai/detections?camera=webcam-0&limit=20'
```

After sourcing `.env`, optional SQL verification using psql (DB_URL is JDBC format):

```bash
PGPASSWORD="$DB_PASSWORD" psql "${DB_URL#jdbc:}" -U "$DB_USERNAME" \
  -c 'SELECT event_id, camera_id, captured_at, verdict, severity, max_contaminated_conf FROM ai_scans ORDER BY captured_at DESC LIMIT 20;'
PGPASSWORD="$DB_PASSWORD" psql "${DB_URL#jdbc:}" -U "$DB_USERNAME" \
  -c 'SELECT scan_id, label, confidence, x1, y1, x2, y2 FROM ai_detection_boxes LIMIT 20;'
```
