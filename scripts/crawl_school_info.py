#!/usr/bin/env python3
"""
从公开数据源爬取/生成院校基础信息。

院校代码前缀规则（国标代码 GB/T 4657）:
  A 开头 — 公办本科院校
  B 开头 — 民办本科院校
  C 开头 — 独立学院（本科）
  D 开头 — 高职（专科）院校（多数为民办）

MVP 阶段: 基于学校代码前缀推断基本信息，后续可通过 gaokao.chsi.com.cn 补充详细数据。
"""

import pandas as pd
import os
import sys


def infer_school_info(school_code: str, school_name: str) -> dict:
    """
    根据学校代码前缀推断院校性质。
    规则参考：教育部国标代码编码规范。
    """
    info = {
        'school_code': school_code,
        'school_name': school_name,
        'province': '',
        'city': '',
        'school_nature': '',
        'education_level': '',
        'school_type': '',
        'is_public': None,
    }

    if not school_code or pd.isna(school_code):
        return info

    code = str(school_code).strip()
    if not code:
        return info

    prefix = code[0].upper()

    if prefix == 'A':
        info['school_nature'] = '公办'
        info['education_level'] = '本科'
        info['is_public'] = True
    elif prefix == 'B':
        info['school_nature'] = '民办'
        info['education_level'] = '本科'
        info['is_public'] = False
    elif prefix == 'C':
        info['school_nature'] = '民办'
        info['education_level'] = '独立学院（本科）'
        info['is_public'] = False
    elif prefix == 'D':
        info['school_nature'] = '民办'
        info['education_level'] = '专科'
        info['is_public'] = False
    else:
        info['school_nature'] = '未知'
        info['education_level'] = '未知'
        info['is_public'] = None

    return info


def main():
    input_path = 'data/processed/unique_schools.csv'
    if not os.path.exists(input_path):
        print(f'ERROR: {input_path} not found. Run parse_toudangxian.py first.')
        sys.exit(1)

    schools = pd.read_csv(input_path)
    print(f'加载 {len(schools)} 所院校')

    school_infos = []
    for _, row in schools.iterrows():
        info = infer_school_info(row['school_code'], row['school_name'])
        school_infos.append(info)

    df_info = pd.DataFrame(school_infos)

    # 统计
    nature_counts = df_info['school_nature'].value_counts()
    level_counts = df_info['education_level'].value_counts()
    print(f'\n办学性质分布:')
    for nature, count in nature_counts.items():
        print(f'  {nature}: {count} 所')
    print(f'\n学历层次分布:')
    for level, count in level_counts.items():
        print(f'  {level}: {count} 所')

    output_path = 'data/processed/school_basic_info.csv'
    os.makedirs('data/processed', exist_ok=True)
    df_info.to_csv(output_path, index=False, encoding='utf-8-sig')
    print(f'\n已保存: {output_path}')


if __name__ == '__main__':
    main()
