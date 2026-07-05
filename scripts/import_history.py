#!/usr/bin/env python3
"""Import admission history data into MySQL."""
import mysql.connector
import sys

conn = mysql.connector.connect(
    host='localhost', user='admission', password='admission123', database='admission_platform'
)
cursor = conn.cursor()
total = 0

for year in [2023, 2024, 2025]:
    print(f'Importing {year}...')
    count = 0
    with open(f'data/processed/sql/02_history_{year}.sql') as f:
        for line in f:
            line = line.strip()
            if line.startswith('INSERT INTO'):
                try:
                    cursor.execute(line)
                    count += 1
                    total += 1
                    if count % 5000 == 0:
                        conn.commit()
                        print(f'  {count} rows')
                except Exception as e:
                    pass
    conn.commit()
    print(f'{year}: {count} rows')

cursor.close()
conn.close()
print(f'Total: {total} rows imported')
