SELECT
    p.name AS process,
    t.name AS thread,
    ROUND(SUM(s.dur) / 1000000.0, 2) AS scheduled_cpu_ms
FROM sched AS s
JOIN thread AS t USING (utid)
JOIN process AS p USING (upid)
WHERE p.name LIKE 'com.adrianrusu.pandawave%'
GROUP BY p.name, t.name
ORDER BY SUM(s.dur) DESC
LIMIT 40;
