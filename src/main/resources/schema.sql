CREATE TABLE IF NOT EXISTS videos (
    id           BIGSERIAL PRIMARY KEY,
    url          TEXT NOT NULL UNIQUE,
    platform     VARCHAR(50) NOT NULL,
    video_id     VARCHAR(255) NOT NULL,
    title        VARCHAR(500),
    view_count   BIGINT DEFAULT 0,
    last_updated TIMESTAMP,
    last_error   TEXT,
    is_available BOOLEAN DEFAULT TRUE,
    added_by     BIGINT NOT NULL,
    created_at   TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS view_stats (
    id          BIGSERIAL PRIMARY KEY,
    video_id    BIGINT NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    view_count  BIGINT NOT NULL,
    recorded_at TIMESTAMP DEFAULT NOW()
);

CREATE OR REPLACE VIEW stats_summary AS
SELECT
    COUNT(*)                                          AS total_videos,
    COALESCE(SUM(view_count), 0)                     AS total_views,
    COUNT(*) FILTER (WHERE NOT is_available)         AS unavailable_count,
    COUNT(*) FILTER (WHERE platform = 'YOUTUBE')     AS youtube_count,
    COALESCE(SUM(view_count) FILTER (WHERE platform = 'YOUTUBE'), 0) AS youtube_views,
    COUNT(*) FILTER (WHERE platform = 'RUTUBE')      AS rutube_count,
    COALESCE(SUM(view_count) FILTER (WHERE platform = 'RUTUBE'), 0)  AS rutube_views
FROM videos;
