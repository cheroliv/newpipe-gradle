-- ============================================================================
-- MP3 ORGANIZER - PostgreSQL Schema
-- TOUTE LA LOGIQUE MÉTIER EST DANS LA BASE DE DONNÉES
-- ============================================================================

-- ============================================================================
-- TABLES
-- ============================================================================

CREATE TABLE IF NOT EXISTS artists (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS albums (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    artist_id INTEGER REFERENCES artists(id) ON DELETE CASCADE,
    year INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    UNIQUE(title, artist_id)
);

CREATE TABLE IF NOT EXISTS tracks (
    id SERIAL PRIMARY KEY,
    title TEXT NOT NULL,
    artist_id INTEGER REFERENCES artists(id) ON DELETE CASCADE,
    album_id INTEGER REFERENCES albums(id) ON DELETE SET NULL,
    file_path TEXT NOT NULL UNIQUE,
    file_size_bytes BIGINT NOT NULL,
    duration_seconds INTEGER,
    bitrate_kbps INTEGER,
    sample_rate INTEGER,
    has_video BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS genres (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS track_genres (
    track_id INTEGER REFERENCES tracks(id) ON DELETE CASCADE,
    genre_id INTEGER REFERENCES genres(id) ON DELETE CASCADE,
    PRIMARY KEY (track_id, genre_id)
);

CREATE TABLE IF NOT EXISTS scan_history (
    id SERIAL PRIMARY KEY,
    scan_path TEXT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    files_scanned INTEGER DEFAULT 0,
    files_imported INTEGER DEFAULT 0,
    files_skipped INTEGER DEFAULT 0,
    files_failed INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS export_history (
    id SERIAL PRIMARY KEY,
    export_format TEXT NOT NULL,
    export_path TEXT NOT NULL,
    exported_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    record_count INTEGER
);

-- Indexes
CREATE INDEX idx_tracks_artist ON tracks(artist_id);
CREATE INDEX idx_tracks_album ON tracks(album_id);
CREATE INDEX idx_tracks_file_path ON tracks(file_path);
CREATE INDEX idx_tracks_size ON tracks(file_size_bytes);
CREATE INDEX idx_track_genres_track ON track_genres(track_id);

-- ============================================================================
-- VUES MÉTIER
-- ============================================================================

-- Vue complète des tracks
CREATE OR REPLACE VIEW v_tracks_full AS
SELECT 
    t.id,
    t.title,
    a.name AS artist_name,
    al.title AS album_title,
    al.year AS album_year,
    t.file_path,
    t.file_size_bytes,
    pg_size_pretty(t.file_size_bytes) AS size_pretty,
    t.duration_seconds,
    t.bitrate_kbps,
    t.sample_rate,
    t.has_video,
    t.created_at,
    COALESCE(
        (SELECT STRING_AGG(g.name, ', ') 
         FROM track_genres tg JOIN genres g ON tg.genre_id = g.id 
         WHERE tg.track_id = t.id),
        'Unknown'
    ) AS genres
FROM tracks t
LEFT JOIN artists a ON t.artist_id = a.id
LEFT JOIN albums al ON t.album_id = al.id;

-- Stats artistes
CREATE OR REPLACE VIEW v_artist_stats AS
SELECT 
    a.id,
    a.name,
    COUNT(DISTINCT t.id) AS track_count,
    COUNT(DISTINCT al.id) AS album_count,
    COALESCE(SUM(t.file_size_bytes), 0) AS total_size_bytes,
    pg_size_pretty(COALESCE(SUM(t.file_size_bytes), 0)) AS size_pretty,
    COALESCE(SUM(t.duration_seconds), 0) AS total_duration_seconds,
    TO_CHAR(
        (COALESCE(SUM(t.duration_seconds), 0) * INTERVAL '1 second'), 
        'HH24:MI:SS'
    ) AS duration_formatted
FROM artists a
LEFT JOIN tracks t ON a.id = t.artist_id
LEFT JOIN albums al ON a.id = al.artist_id
GROUP BY a.id, a.name;

-- Fichiers < 2MB
CREATE OR REPLACE VIEW v_small_files AS
SELECT 
    t.id,
    t.title,
    a.name AS artist_name,
    t.file_path,
    t.file_size_bytes,
    pg_size_pretty(t.file_size_bytes) AS size_pretty
FROM tracks t
LEFT JOIN artists a ON t.artist_id = a.id
WHERE t.file_size_bytes < 2097152
ORDER BY t.file_size_bytes DESC;

-- Tracks par genre
CREATE OR REPLACE VIEW v_tracks_by_genre AS
SELECT 
    g.name AS genre_name,
    t.id AS track_id,
    t.title AS track_title,
    a.name AS artist_name,
    al.title AS album_title
FROM genres g
JOIN track_genres tg ON g.id = tg.genre_id
JOIN tracks t ON tg.track_id = t.id
LEFT JOIN artists a ON t.artist_id = a.id
LEFT JOIN albums al ON t.album_id = al.id
ORDER BY g.name, a.name, t.title;

-- Stats collection
CREATE OR REPLACE VIEW v_collection_stats AS
SELECT 
    COUNT(DISTINCT a.id) AS total_artists,
    COUNT(DISTINCT al.id) AS total_albums,
    COUNT(t.id) AS total_tracks,
    pg_size_pretty(SUM(t.file_size_bytes)) AS total_size,
    SUM(t.file_size_bytes) AS total_size_bytes,
    TO_CHAR(
        (SUM(t.duration_seconds) * INTERVAL '1 second'), 
        'HH24:MI:SS'
    ) AS total_duration,
    SUM(t.duration_seconds) AS total_duration_seconds
FROM tracks t
LEFT JOIN artists a ON t.artist_id = a.id
LEFT JOIN albums al ON t.album_id = al.id;

-- ============================================================================
-- FONCTIONS STOCKÉES - LOGIQUE MÉTIER
-- ============================================================================

-- Fonction: upsert artist
CREATE OR REPLACE FUNCTION fn_upsert_artist(p_name TEXT)
RETURNS INTEGER AS $$
DECLARE
    v_artist_id INTEGER;
BEGIN
    INSERT INTO artists (name) 
    VALUES (p_name)
    ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
    RETURNING id INTO v_artist_id;
    
    RETURN v_artist_id;
END;
$$ LANGUAGE plpgsql;

-- Fonction: upsert album
CREATE OR REPLACE FUNCTION fn_upsert_album(p_title TEXT, p_artist_id INTEGER, p_year INTEGER DEFAULT NULL)
RETURNS INTEGER AS $$
DECLARE
    v_album_id INTEGER;
BEGIN
    INSERT INTO albums (title, artist_id, year)
    VALUES (p_title, p_artist_id, p_year)
    ON CONFLICT (title, artist_id) DO UPDATE SET year = EXCLUDED.year
    RETURNING id INTO v_album_id;
    
    RETURN v_album_id;
END;
$$ LANGUAGE plpgsql;

-- Fonction: upsert genre
CREATE OR REPLACE FUNCTION fn_upsert_genre(p_name TEXT)
RETURNS INTEGER AS $$
DECLARE
    v_genre_id INTEGER;
BEGIN
    INSERT INTO genres (name)
    VALUES (p_name)
    ON CONFLICT (name) DO NOTHING
    RETURNING id INTO v_genre_id;
    
    IF v_genre_id IS NULL THEN
        SELECT id INTO v_genre_id FROM genres WHERE name = p_name;
    END IF;
    
    RETURN v_genre_id;
END;
$$ LANGUAGE plpgsql;

-- Fonction: importer un track
CREATE OR REPLACE FUNCTION fn_import_track(
    p_title TEXT,
    p_artist_name TEXT,
    p_album_title TEXT DEFAULT NULL,
    p_album_year INTEGER DEFAULT NULL,
    p_file_path TEXT,
    p_file_size_bytes BIGINT,
    p_duration_seconds INTEGER DEFAULT NULL,
    p_bitrate_kbps INTEGER DEFAULT NULL,
    p_sample_rate INTEGER DEFAULT NULL,
    p_has_video BOOLEAN DEFAULT FALSE,
    p_genre_names TEXT[] DEFAULT NULL
)
RETURNS INTEGER AS $$
DECLARE
    v_track_id INTEGER;
    v_artist_id INTEGER;
    v_album_id INTEGER := NULL;
    v_genre_id INTEGER;
    v_genre_name TEXT;
BEGIN
    -- Upsert artist
    v_artist_id := fn_upsert_artist(p_artist_name);
    
    -- Upsert album si présent
    IF p_album_title IS NOT NULL THEN
        v_album_id := fn_upsert_album(p_album_title, v_artist_id, p_album_year);
    END IF;
    
    -- Upsert track
    INSERT INTO tracks (title, artist_id, album_id, file_path, file_size_bytes, 
                        duration_seconds, bitrate_kbps, sample_rate, has_video)
    VALUES (p_title, v_artist_id, v_album_id, p_file_path, p_file_size_bytes,
            p_duration_seconds, p_bitrate_kbps, p_sample_rate, p_has_video)
    ON CONFLICT (file_path) DO UPDATE SET
        title = EXCLUDED.title,
        artist_id = EXCLUDED.artist_id,
        album_id = EXCLUDED.album_id,
        file_size_bytes = EXCLUDED.file_size_bytes,
        duration_seconds = EXCLUDED.duration_seconds,
        bitrate_kbps = EXCLUDED.bitrate_kbps,
        sample_rate = EXCLUDED.sample_rate,
        has_video = EXCLUDED.has_video
    RETURNING id INTO v_track_id;
    
    -- Upsert genres
    IF p_genre_names IS NOT NULL AND array_length(p_genre_names, 1) > 0 THEN
        FOREACH v_genre_name IN ARRAY p_genre_names LOOP
            v_genre_id := fn_upsert_genre(v_genre_name);
            INSERT INTO track_genres (track_id, genre_id)
            VALUES (v_track_id, v_genre_id)
            ON CONFLICT (track_id, genre_id) DO NOTHING;
        END LOOP;
    END IF;
    
    RETURN v_track_id;
END;
$$ LANGUAGE plpgsql;

-- Fonction: supprimer tracks < 2MB
CREATE OR REPLACE FUNCTION fn_delete_small_tracks(p_size_threshold_bytes BIGINT DEFAULT 2097152)
RETURNS INTEGER AS $$
DECLARE
    v_deleted_count INTEGER;
BEGIN
    DELETE FROM tracks WHERE file_size_bytes < p_size_threshold_bytes;
    GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
    RETURN v_deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Fonction: organiser fichiers par artiste
CREATE OR REPLACE FUNCTION fn_get_files_to_organize()
RETURNS TABLE (
    track_id INTEGER,
    current_path TEXT,
    artist_name TEXT,
    target_folder TEXT,
    target_filename TEXT,
    target_path TEXT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        t.id,
        t.file_path,
        a.name,
        REGEXP_REPLACE(a.name, E'[<>:"/\\\\|?*]', '', 'g'),
        REGEXP_REPLACE(a.name || ' - ' || t.title, E'[<>:"/\\\\|?*]', '', 'g') || '.mp3',
        REGEXP_REPLACE(a.name, E'[<>:"/\\\\|?*]', '', 'g') || '/' || 
        REGEXP_REPLACE(a.name || ' - ' || t.title, E'[<>:"/\\\\|?*]', '', 'g') || '.mp3'
    FROM tracks t
    JOIN artists a ON t.artist_id = a.id
    WHERE a.name != 'Unknown Artist'
    ORDER BY a.name, t.title;
END;
$$ LANGUAGE plpgsql;

-- Fonction: mettre à jour path après organisation
CREATE OR REPLACE FUNCTION fn_update_track_path(p_track_id INTEGER, p_new_path TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    UPDATE tracks SET file_path = p_new_path WHERE id = p_track_id;
    RETURN FOUND;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- PROCÉDURES STOCKÉES POUR EXPORT
-- ============================================================================

-- Procédure: export complet JSON-ready
CREATE OR REPLACE FUNCTION fn_export_full_collection()
RETURNS TABLE (result JSONB) AS $$
BEGIN
    RETURN QUERY
    SELECT jsonb_build_object(
        'exported_at', NOW(),
        'summary', (
            SELECT jsonb_build_object(
                'total_artists', total_artists,
                'total_albums', total_albums,
                'total_tracks', total_tracks,
                'total_size', total_size,
                'total_size_bytes', total_size_bytes,
                'total_duration', total_duration
            ) FROM v_collection_stats
        ),
        'artists', (
            SELECT COALESCE(jsonb_agg(row_to_json(a)), '[]'::jsonb)
            FROM v_artist_stats a
        ),
        'albums', (
            SELECT COALESCE(jsonb_agg(row_to_json(al)), '[]'::jsonb)
            FROM albums al
            LEFT JOIN artists a ON al.artist_id = a.id
        ),
        'tracks', (
            SELECT COALESCE(jsonb_agg(row_to_json(t)), '[]'::jsonb)
            FROM v_tracks_full t
        ),
        'genres', (
            SELECT COALESCE(jsonb_agg(row_to_json(g)), '[]'::jsonb)
            FROM (SELECT g.name, COUNT(tg.track_id) as track_count 
                  FROM genres g 
                  LEFT JOIN track_genres tg ON g.id = tg.genre_id 
                  GROUP BY g.id, g.name) g
        )
    );
END;
$$ LANGUAGE plpgsql;

-- Procédure: stats business
CREATE OR REPLACE FUNCTION fn_business_stats()
RETURNS TABLE (result JSONB) AS $$
BEGIN
    RETURN QUERY
    SELECT jsonb_build_object(
        'top_artists', (
            SELECT COALESCE(jsonb_agg(row_to_json(a)), '[]'::jsonb)
            FROM v_artist_stats a
            ORDER BY a.track_count DESC
            LIMIT 10
        ),
        'small_files', (
            SELECT COALESCE(jsonb_agg(row_to_json(s)), '[]'::jsonb)
            FROM v_small_files s
            LIMIT 100
        ),
        'missing_metadata', (
            SELECT COALESCE(jsonb_agg(row_to_json(t)), '[]'::jsonb)
            FROM v_tracks_full t
            WHERE artist_name = 'Unknown Artist' OR title = 'Unknown'
            LIMIT 100
        ),
        'video_tracks', (
            SELECT COALESCE(jsonb_agg(row_to_json(t)), '[]'::jsonb)
            FROM v_tracks_full t
            WHERE has_video = true
            ORDER BY file_size_bytes DESC
            LIMIT 50
        ),
        'collection', (SELECT row_to_json(c) FROM v_collection_stats c)
    );
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- TRIGGERS
-- ============================================================================

-- Log export dans l'historique
CREATE OR REPLACE FUNCTION fn_log_export()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO export_history (export_format, export_path, record_count)
    VALUES (NEW.export_format, NEW.export_path, NEW.record_count);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_log_export
AFTER INSERT ON export_history
FOR EACH ROW EXECUTE FUNCTION fn_log_export();
