SELECT
    p.name AS process,
    t.name AS thread,
    s.name AS slice,
    ROUND(s.dur / 1000000.0, 2) AS duration_ms
FROM slice AS s
JOIN thread_track AS tt ON s.track_id = tt.id
JOIN thread AS t USING (utid)
JOIN process AS p USING (upid)
WHERE p.name LIKE 'com.adrianrusu.pandawave%'
  AND s.dur > 10000000
ORDER BY s.dur DESC
LIMIT 60;
