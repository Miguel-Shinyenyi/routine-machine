CREATE TABLE measurements (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(20) NOT NULL,
    metric VARCHAR(50) NOT NULL,
    measurement_date DATE NOT NULL,
    value NUMERIC NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT measurements_source_valid CHECK (source IN ('WAKATIME', 'ACTIVITYWATCH', 'GITHUB', 'RESCUETIME', 'HEALTH')),
    CONSTRAINT measurements_source_metric_date_unique UNIQUE (source, metric, measurement_date)
);

CREATE INDEX idx_measurements_measurement_date ON measurements(measurement_date);
