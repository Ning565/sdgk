#!/usr/bin/env python3
"""
解析山东高考投档线 Excel 文件
处理不同年份的列布局差异，导出为统一格式的 CSV。
输出目录: data/processed/

文件格式差异说明:
  - 2023/2024 第一批次: 5列 (col 0 为空填充, col 1-4 = 专业/院校/计划数/位次)
  - 2025 第一批次:      4列 (col 0-3 = 专业/院校/计划数/位次，无空列)
  - 全部第二批次:        6列 (col 0 为空填充, col 1-5 = 层次/专业/院校/计划数/位次)
  - 2024年批次二文件标题写的是"2025年"，但实际是 2024 年第二次志愿数据
"""

import pandas as pd
import re
import os
import sys
from typing import Optional


def parse_school_code(raw_code: str) -> Optional[str]:
    """提取院校代号，如 'A003清华大学' -> 'A003'"""
    if pd.isna(raw_code):
        return None
    match = re.match(r'([A-Z]\d+)', str(raw_code))
    return match.group(1) if match else None


def parse_major_code(raw_major: str) -> Optional[str]:
    """提取专业代号，如 '1C文科试验班类(文科各专业)' -> '1C'"""
    if pd.isna(raw_major):
        return None
    match = re.match(r'([\dA-Za-z]+)', str(raw_major))
    return match.group(1) if match else None


def parse_major_name(raw_major: str) -> str:
    """提取专业名称，去掉代号前缀"""
    if pd.isna(raw_major):
        return ''
    s = str(raw_major).strip()
    s = re.sub(r'^[\dA-Za-z]+\s*', '', s)
    return s


def parse_rank(raw_rank) -> Optional[int]:
    """解析位次，'前50名' 等特殊值返回 None"""
    if pd.isna(raw_rank):
        return None
    s = str(raw_rank).strip()
    if '前' in s or '名' in s:
        return None
    try:
        return int(float(s))
    except (ValueError, TypeError):
        return None


def is_header_row(row: pd.Series, header_keywords: list) -> bool:
    """判断某行是否为标题行"""
    for val in row:
        s = str(val).strip()
        if any(kw in s for kw in header_keywords):
            return True
    return False


def find_columns(df: pd.DataFrame) -> dict:
    """
    动态定位各列在 DataFrame 中的索引。
    通过扫描前几行找到标题行，确定各列的列号。
    返回 {'major_col': int, 'school_col': int, 'plan_col': int, 'rank_col': int, 'level_col': int|None}
    """
    result = {
        'major_col': None,
        'school_col': None,
        'plan_col': None,
        'rank_col': None,
        'level_col': None,
        'header_row': None,
    }

    for idx, row in df.iterrows():
        row_strs = [str(v).strip() for v in row]

        # 查找标题行：包含"院校"和"专业"关键词
        has_school = any('院校' in s for s in row_strs)
        has_major = any('专业' in s for s in row_strs)
        has_plan = any('计划' in s for s in row_strs)
        has_rank = any('位次' in s for s in row_strs)
        has_level = any('层次' in s for s in row_strs)

        if has_school and has_major:
            result['header_row'] = idx
            # 按列内容确定列索引
            for col_idx, s in enumerate(row_strs):
                if '院校' in s:
                    result['school_col'] = col_idx
                elif '专业' in s:
                    result['major_col'] = col_idx
                elif '计划' in s:
                    result['plan_col'] = col_idx
                elif '位次' in s:
                    result['rank_col'] = col_idx
                elif '层次' in s:
                    result['level_col'] = col_idx
            break

    return result


def parse_file(filepath: str, year: int, batch: int) -> pd.DataFrame:
    """
    解析单个投档线 Excel 文件。
    自动检测列布局。
    """
    print(f'Parsing {filepath} ...')

    df_raw = pd.read_excel(filepath, header=None)
    cols = find_columns(df_raw)

    if cols['header_row'] is None:
        print(f'  WARNING: Could not find header row in {filepath}, skipping.')
        return pd.DataFrame()

    print(f'  Header at row {cols["header_row"]}, '
          f'major_col={cols["major_col"]}, school_col={cols["school_col"]}, '
          f'plan_col={cols["plan_col"]}, rank_col={cols["rank_col"]}, '
          f'level_col={cols["level_col"]}')

    data_start = cols['header_row'] + 1
    data_rows = []

    for idx, row in df_raw.iterrows():
        if idx < data_start:
            continue

        major_raw = row.iloc[cols['major_col']]
        school_raw = row.iloc[cols['school_col']]

        # 跳过空行
        if pd.isna(school_raw) or pd.isna(major_raw):
            continue

        school_raw_str = str(school_raw).strip()
        major_raw_str = str(major_raw).strip()

        # 跳过标题/表头残余
        if school_raw_str in ['院校代号及名称', '院校'] or major_raw_str in ['专业代号及名称', '专业']:
            continue

        # 跳过全空/无效行（院校代号应包含字母+数字模式）
        if not re.match(r'[A-Z]\d+', school_raw_str):
            continue

        # 计划数和位次
        plan_val = row.iloc[cols['plan_col']]
        plan_count = int(float(plan_val)) if not pd.isna(plan_val) else 0

        rank_val = row.iloc[cols['rank_col']]
        min_rank = parse_rank(rank_val)

        # 层次（第二批次有）
        education_level = None
        if cols['level_col'] is not None:
            level_val = row.iloc[cols['level_col']]
            education_level = str(level_val).strip() if not pd.isna(level_val) else None

        # 第一批次默认本科
        if batch == 1:
            education_level = '本科'

        school_code = parse_school_code(school_raw_str)
        school_name = school_raw_str
        if school_code:
            school_name = school_raw_str.replace(school_code, '', 1).strip()

        data_rows.append({
            'year': year,
            'batch': batch,
            'batch_name': f'常规批第{batch}次志愿',
            'major_code': parse_major_code(major_raw_str),
            'major_name': parse_major_name(major_raw_str),
            'major_raw': major_raw_str,
            'school_code': school_code,
            'school_name': school_name,
            'plan_count': plan_count,
            'min_rank': min_rank,
            'education_level': education_level,
        })

    result_df = pd.DataFrame(data_rows)
    print(f'  {len(result_df)} rows parsed')
    return result_df


def main():
    os.makedirs('data/processed', exist_ok=True)

    files = [
        ('data/2023年投档线.xls', 2023, 1),
        ('data/2023年批次二投档线.xls', 2023, 2),
        ('data/2024年投档线.xls', 2024, 1),
        ('data/2024年批次二投档线.xls', 2024, 2),
        ('data/2025年投档线.xls', 2025, 1),
        ('data/2025年批次二投档线.xls', 2025, 2),
    ]

    all_data = []
    total_rows = 0

    for filepath, year, batch in files:
        if not os.path.exists(filepath):
            print(f'SKIP: {filepath} not found')
            continue
        df = parse_file(filepath, year, batch)
        if len(df) > 0:
            all_data.append(df)
            total_rows += len(df)

    if not all_data:
        print('ERROR: No data parsed.')
        sys.exit(1)

    combined = pd.concat(all_data, ignore_index=True)

    # --- 统计信息 ---
    print(f'\n{"="*50}')
    print(f'总计: {len(combined)} 行')
    print(f'唯一院校: {combined["school_code"].nunique()} 所')
    print(f'唯一专业(按 raw): {combined["major_raw"].nunique()} 个')

    print(f'\n年度分布:')
    for year in [2023, 2024, 2025]:
        count = len(combined[combined['year'] == year])
        batch1 = len(combined[(combined['year'] == year) & (combined['batch'] == 1)])
        batch2 = len(combined[(combined['year'] == year) & (combined['batch'] == 2)])
        print(f'  {year}: {count} 行 (第1次: {batch1}, 第2次: {batch2})')

    print(f'\n批次分布:')
    for batch in [1, 2]:
        count = len(combined[combined['batch'] == batch])
        print(f'  第{batch}次志愿: {count} 行')

    print(f'\n位次有效数据: {combined["min_rank"].notna().sum()} 行')
    print(f'位次缺失(含"前N名"): {combined["min_rank"].isna().sum()} 行')

    # --- 保存 ---
    output_path = 'data/processed/all_toudangxian.csv'
    combined.to_csv(output_path, index=False, encoding='utf-8-sig')
    print(f'\n已保存: {output_path}')

    # 唯一院校列表
    schools = combined[['school_code', 'school_name']].drop_duplicates(subset='school_code')
    schools = schools.sort_values('school_code')
    schools_path = 'data/processed/unique_schools.csv'
    schools.to_csv(schools_path, index=False, encoding='utf-8-sig')
    print(f'已保存: {schools_path} ({len(schools)} 所)')

    # 唯一专业列表
    majors = combined[['major_code', 'major_name', 'major_raw']].drop_duplicates(subset='major_raw')
    majors = majors.sort_values('major_raw')
    majors_path = 'data/processed/unique_majors.csv'
    majors.to_csv(majors_path, index=False, encoding='utf-8-sig')
    print(f'已保存: {majors_path} ({len(majors)} 个)')

    print(f'\n全部解析完成。')


if __name__ == '__main__':
    main()
