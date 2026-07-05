#!/usr/bin/env python3
"""
从处理后的 CSV 数据生成 MySQL INSERT SQL 语句。
用于初始化项目数据库表: school, admission_history。
"""

import pandas as pd
import os
import sys


def escape_sql(val) -> str:
    """SQL 值转义，NaN/None -> NULL"""
    if pd.isna(val) or val is None:
        return 'NULL'
    s = str(val).replace("'", "\\'").replace('\\', '\\\\')
    return f"'{s}'"


def generate_school_sql(df_schools: pd.DataFrame) -> str:
    """
    生成 school 表 INSERT 语句。
    字段: code, name, short_name, province, city, school_type, school_tag, status
    """
    lines = [
        '-- ============================================',
        '-- 院校基础数据 (school 表)',
        '-- 生成时间: 自动生成',
        '-- ============================================',
        '',
        'INSERT INTO school (code, name, short_name, province, city, school_type, school_tag, status) VALUES',
    ]

    values = []
    for _, row in df_schools.iterrows():
        code = escape_sql(row['school_code'])
        name = escape_sql(row['school_name'])
        short = escape_sql(row.get('school_name', ''))
        province = escape_sql(row.get('province', '山东'))
        city = escape_sql(row.get('city', ''))
        nature = escape_sql(row.get('school_nature', ''))
        tag = escape_sql(row.get('school_tag', ''))
        values.append(
            f"({code}, {name}, {short}, {province}, {city}, {nature}, {tag}, 'ACTIVE')"
        )

    return '\n'.join(lines) + '\n' + ',\n'.join(values) + ';\n'


def generate_admission_history_sql(df: pd.DataFrame, batch_size: int = 500) -> list:
    """
    生成 admission_history 表 INSERT 语句。
    按批次拆分，每批最多 batch_size 行，避免单个 SQL 过大。
    字段: year, school_code, major_code, major_name, plan_count, min_rank,
          admission_batch, education_level, data_version_id
    """
    records = []
    for _, row in df.iterrows():
        year = int(row['year'])
        school_code = escape_sql(row['school_code'])
        major_code = escape_sql(row.get('major_code', ''))
        major_name = escape_sql(row['major_name'])
        plan_count = int(row['plan_count']) if not pd.isna(row['plan_count']) else 0
        min_rank = int(row['min_rank']) if not pd.isna(row.get('min_rank')) else 'NULL'
        if isinstance(min_rank, int):
            min_rank = str(min_rank)
        batch = int(row.get('batch', 1))
        edu_level = escape_sql(row.get('education_level', '本科'))

        records.append(
            f"INSERT INTO admission_history "
            f"(year, school_code, major_code, major_name, plan_count, min_rank, "
            f"admission_batch, education_level, data_version_id) "
            f"VALUES ({year}, {school_code}, {major_code}, {major_name}, "
            f"{plan_count}, {min_rank}, {batch}, {edu_level}, 1);"
        )

    # 按 batch_size 分组
    batches = []
    for i in range(0, len(records), batch_size):
        chunk = records[i:i + batch_size]
        header = [
            f'-- ============================================',
            f'-- 录取历史数据 (admission_history 表)',
            f'-- 批次 {i // batch_size + 1}/{(len(records) - 1) // batch_size + 1}',
            f'-- 行数: {len(chunk)}',
            f'-- ============================================',
            '',
        ]
        batches.append('\n'.join(header) + '\n'.join(chunk) + '\n')

    return batches


def main():
    data_path = 'data/processed/all_toudangxian.csv'
    schools_path = 'data/processed/unique_schools.csv'

    if not os.path.exists(data_path):
        print(f'ERROR: {data_path} not found. Run parse_toudangxian.py first.')
        sys.exit(1)
    if not os.path.exists(schools_path):
        print(f'ERROR: {schools_path} not found. Run parse_toudangxian.py first.')
        sys.exit(1)

    df = pd.read_csv(data_path)
    df_schools = pd.read_csv(schools_path)

    print(f'加载 {len(df)} 条投档数据, {len(df_schools)} 所院校')

    output_dir = 'data/processed/sql'
    os.makedirs(output_dir, exist_ok=True)

    # --- 1. School INSERT ---
    school_sql = generate_school_sql(df_schools)
    school_out = os.path.join(output_dir, '01_schools.sql')
    with open(school_out, 'w', encoding='utf-8') as f:
        f.write(school_sql)
    print(f'已生成: {school_out} ({len(df_schools)} 所院校)')

    # --- 2. Admission History INSERT (按年度拆分，每年度合并为一个文件) ---
    for year in [2023, 2024, 2025]:
        df_year = df[df['year'] == year].copy()
        if len(df_year) == 0:
            continue

        batches = generate_admission_history_sql(df_year, batch_size=500)
        combined = ''.join(batches)
        out_path = os.path.join(output_dir, f'02_history_{year}.sql')
        with open(out_path, 'w', encoding='utf-8') as f:
            f.write(
                f'-- 山东省普通类常规批投档线 {year} 年\n'
                f'-- 总行数: {len(df_year)}\n'
                f'-- 分为 {len(batches)} 个 INSERT 批次\n\n'
            )
            f.write(combined)
        print(f'已生成: {out_path} ({len(df_year)} 行)')

    print(f'\n全部 SQL 文件生成完毕。输出目录: {output_dir}/')


if __name__ == '__main__':
    main()
