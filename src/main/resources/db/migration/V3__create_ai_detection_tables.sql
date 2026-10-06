CREATE TABLE ai_scans (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    camera_id VARCHAR(100) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    verdict VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(255),
    n_contaminated INTEGER NOT NULL DEFAULT 0 CHECK (n_contaminated >= 0),
    n_healthy INTEGER NOT NULL DEFAULT 0 CHECK (n_healthy >= 0),
    max_conf DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (max_conf BETWEEN 0 AND 1),
    max_contaminated_conf DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (max_contaminated_conf BETWEEN 0 AND 1),
    width INTEGER NOT NULL CHECK (width > 0),
    height INTEGER NOT NULL CHECK (height > 0),
    inference_ms INTEGER NOT NULL CHECK (inference_ms >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE ai_detection_boxes (
    id UUID PRIMARY KEY,
    scan_id UUID NOT NULL REFERENCES ai_scans(id) ON DELETE CASCADE,
    label VARCHAR(50) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence BETWEEN 0 AND 1),
    x1 INTEGER NOT NULL,
    y1 INTEGER NOT NULL,
    x2 INTEGER NOT NULL,
    y2 INTEGER NOT NULL
);
CREATE INDEX idx_ai_scans_camera_time ON ai_scans(camera_id, captured_at DESC);
CREATE INDEX idx_ai_scans_verdict ON ai_scans(verdict);
