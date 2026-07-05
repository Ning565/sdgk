#!/usr/bin/env python3
"""Import 2026 score rank data into MySQL."""
import pandas as pd
import mysql.connector

conn = mysql.connector.connect(
    host='localhost', user='admission', password='admission123', database='admission_platform'
)
cursor = conn.cursor()

# Read 2026 data
df = pd.read_excel('data/2026一分一段表.xls', header=None)
print(f'Loaded {len(df)} rows')

# Parse data rows (skip header rows 0,1)
data_rows = []
for idx, row in df.iterrows():
    if idx < 2:
        continue
    try:
        score = int(row.iloc[0])
        segment = int(row.iloc[1]) if not pd.isna(row.iloc[1]) else 0
        cumulative = int(row.iloc[2]) if not pd.isna(row.iloc[2]) else 0
    except (ValueError, TypeError):
        continue
    data_rows.append((score, segment, cumulative))

print(f'Parsed {len(data_rows)} score rows (range: {data_rows[0][0]}-{data_rows[-1][0]})')

# Create data version
cursor.execute(
    "INSERT INTO data_version (data_type, year, version_no, status, row_count) VALUES ('SCORE_RANK', 2026, 1, 'PUBLISHED', %s)",
    (len(data_rows),)
)
version_id = cursor.lastrowid
print(f'Created data_version id={version_id}')

# Check if active version exists
cursor.execute("SELECT id FROM active_data_version WHERE data_type='SCORE_RANK' AND year=2026")
existing = cursor.fetchone()
if existing:
    cursor.execute("UPDATE active_data_version SET data_version_id=%s WHERE data_type='SCORE_RANK' AND year=2026", (version_id,))
else:
    cursor.execute("INSERT INTO active_data_version (data_type, year, data_version_id) VALUES ('SCORE_RANK', 2026, %s)", (version_id,))
print('Set active_data_version')

# Import rows
for score, segment, cumulative in data_rows:
    cursor.execute(
        "INSERT INTO score_rank_segment (data_version_id, year, score, cumulative_count, segment_count) VALUES (%s, 2026, %s, %s, %s)",
        (version_id, score, cumulative, segment)
    )

conn.commit()
print(f'Imported {len(data_rows)} rows into score_rank_segment')
cursor.close()
conn.close()
print('Done!')
