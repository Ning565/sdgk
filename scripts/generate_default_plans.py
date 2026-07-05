#!/usr/bin/env python3
"""Generate default enrollment plan data from admission history."""
import mysql.connector

conn = mysql.connector.connect(
    host='localhost', user='admission', password='admission123', database='admission_platform'
)
cursor = conn.cursor()

# Get unique school+major combos from history
cursor.execute("""
    SELECT DISTINCT school_code, major_name, major_code, admission_batch, education_level
    FROM admission_history
    WHERE school_code IS NOT NULL
""")
combos = cursor.fetchall()
print(f'Found {len(combos)} unique school+major combos')

# Create data version for PLAN 2026
cursor.execute(
    "INSERT INTO data_version (data_type, year, version_no, status, row_count) VALUES ('PLAN', 2026, 1, 'PUBLISHED', %s)",
    (len(combos),)
)
plan_version_id = cursor.lastrowid
print(f'Created PLAN data_version id={plan_version_id}')

# Check/create active version
cursor.execute("SELECT id FROM active_data_version WHERE data_type='PLAN' AND year=2026")
if cursor.fetchone():
    cursor.execute("UPDATE active_data_version SET data_version_id=%s WHERE data_type='PLAN' AND year=2026", (plan_version_id,))
else:
    cursor.execute("INSERT INTO active_data_version (data_type, year, data_version_id) VALUES ('PLAN', 2026, %s)", (plan_version_id,))

# Also set up for 2025
cursor.execute("SELECT id FROM data_version WHERE data_type='PLAN' AND year=2025")
if not cursor.fetchone():
    cursor.execute("INSERT INTO data_version (data_type, year, version_no, status, row_count) VALUES ('PLAN', 2025, 1, 'PUBLISHED', %s)", (len(combos),))
    v2025 = cursor.lastrowid
    cursor.execute("INSERT INTO active_data_version (data_type, year, data_version_id) VALUES ('PLAN', 2025, %s)", (v2025,))
    print(f'Created PLAN 2025 version id={v2025}')

# ALL_SUBJECTS bitmap = (1<<20)-1 = 1048575
ALL_BITMAP = 1048575

imported = 0
for school_code, major_name, major_code, batch, edu_level in combos:
    cursor.execute("SELECT id FROM school WHERE code=%s", (school_code,))
    school_row = cursor.fetchone()
    school_id = school_row[0] if school_row else None

    try:
        cursor.execute("""
            INSERT INTO enrollment_plan
            (data_version_id, year, school_id, school_code, major_code, major_name,
             plan_count, subject_requirement_text, subject_rule_json,
             eligible_subject_bitmap, subject_rule_status, plan_status,
             education_level, enrollment_type_code)
            VALUES (%s, 2026, %s, %s, %s, %s, 5, '不限',
                    '{"display_text":"不限","match_type":"none","subjects":[]}',
                    %s, 'MANUAL_CONFIRMED', 'ACTIVE', %s, 'NORMAL')
        """, (plan_version_id, school_id, school_code, major_code, major_name, ALL_BITMAP, edu_level or 'UNDERGRADUATE'))
        imported += 1
    except Exception as e:
        pass  # skip duplicates

    if imported % 5000 == 0:
        conn.commit()
        print(f'  {imported} plans...')

conn.commit()
cursor.close()
conn.close()
print(f'Done! Imported {imported} enrollment plans')
