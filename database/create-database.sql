SELECT 'CREATE DATABASE crane_monitoring WITH ENCODING ''UTF8'''
WHERE NOT EXISTS (
    SELECT 1
    FROM pg_database
    WHERE datname = 'crane_monitoring'
)\gexec
