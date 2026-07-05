#!/usr/bin/env python3
"""Generate idempotent SQL seed for 2026 plan data from the organized workbook CSV."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
from pathlib import Path

SUBJECTS = ["物理", "化学", "生物", "历史", "地理", "政治"]
COMBOS = [
    ["物理", "化学", "生物"],
    ["物理", "化学", "政治"],
    ["物理", "化学", "历史"],
    ["物理", "化学", "地理"],
    ["物理", "生物", "政治"],
    ["物理", "生物", "历史"],
    ["物理", "生物", "地理"],
    ["物理", "政治", "历史"],
    ["物理", "政治", "地理"],
    ["物理", "历史", "地理"],
    ["化学", "生物", "政治"],
    ["化学", "生物", "历史"],
    ["化学", "生物", "地理"],
    ["化学", "政治", "历史"],
    ["化学", "政治", "地理"],
    ["化学", "历史", "地理"],
    ["生物", "政治", "历史"],
    ["生物", "政治", "地理"],
    ["生物", "历史", "地理"],
    ["政治", "历史", "地理"],
]
ALL_BITMAP = (1 << len(COMBOS)) - 1


def clean(value: object) -> str:
    return "" if value is None else str(value).strip()


def sql(value: object) -> str:
    if value is None:
        return "NULL"
    text = str(value)
    return "'" + text.replace("\\", "\\\\").replace("'", "''") + "'"


def int_or_none(value: object) -> int | None:
    text = clean(value)
    if not text or text in {"-", "—", "无"}:
        return None
    match = re.search(r"-?\d+", text.replace(",", ""))
    return int(match.group()) if match else None


def decimal_or_none(value: object) -> str | None:
    text = clean(value)
    if not text or text in {"-", "—", "无"}:
        return None
    match = re.search(r"-?\d+(?:\.\d+)?", text.replace(",", ""))
    return match.group() if match else None


def normalize_level(value: str) -> str:
    if "专科" in value or "高职" in value:
        return "SPECIALIZED"
    return "UNDERGRADUATE"


def enrollment_type(row: dict[str, str]) -> str:
    text = " ".join(
        clean(row.get(k))
        for k in ("计划类别", "专业全称", "专业名称", "专业备注")
    )
    if "中外" in text:
        return "SINO_FOREIGN"
    if "校企" in text:
        return "SCHOOL_ENTERPRISE"
    return "NORMAL"


def major_code(row: dict[str, str]) -> str:
    original = clean(row.get("专业代码")) or "NA"
    row_id = clean(row.get("ID")) or hashlib.sha1(
        (clean(row.get("院校代码")) + clean(row.get("专业全称"))).encode("utf-8")
    ).hexdigest()[:10]
    return f"{original}_{row_id}"[:50]


def standard_major_code(row: dict[str, str]) -> str:
    key = "|".join(
        clean(row.get(k))
        for k in ("门类", "专业类", "专业名称", "专业层次")
    )
    return "M" + hashlib.sha1(key.encode("utf-8")).hexdigest()[:18]


def subject_rule(text: str) -> tuple[str, int]:
    raw = clean(text)
    if not raw or raw == "不限":
        rule = {"displayText": raw or "不限", "matchType": "all", "subjects": []}
        return json.dumps(rule, ensure_ascii=False, separators=(",", ":")), ALL_BITMAP

    picked = [subject for subject in SUBJECTS if subject in raw]
    match_type = "any" if "或" in raw else "all"
    bitmap = 0
    for index, combo in enumerate(COMBOS):
        combo_set = set(combo)
        if not picked:
            matched = True
        elif match_type == "any":
            matched = any(subject in combo_set for subject in picked)
        else:
            matched = all(subject in combo_set for subject in picked)
        if matched:
            bitmap |= 1 << index

    rule = {"displayText": raw, "matchType": match_type, "subjects": picked}
    return json.dumps(rule, ensure_ascii=False, separators=(",", ":")), bitmap or ALL_BITMAP


def load_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return [
            row for row in csv.DictReader(handle)
            if clean(row.get("年份")) == "2026" and clean(row.get("院校代码")) and clean(row.get("院校名称"))
        ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True, type=Path)
    args = parser.parse_args()

    rows = load_rows(args.input)
    schools: dict[str, dict[str, str]] = {}
    majors: dict[str, dict[str, str]] = {}
    plan_rows: list[list[object]] = []

    for row in rows:
        school_code = clean(row.get("院校代码"))
        schools.setdefault(school_code, row)

        sm_code = standard_major_code(row)
        majors.setdefault(sm_code, row)

        predicted = int_or_none(row.get("26年预估位次"))
        rank_min = int(predicted * 0.92) if predicted else None
        rank_max = int(predicted * 1.08) if predicted else None
        rule_json, bitmap = subject_rule(clean(row.get("选科要求")))
        level = normalize_level(clean(row.get("专业层次")))

        plan_rows.append([
            clean(row.get("ID")),
            2026,
            school_code,
            sm_code,
            major_code(row),
            clean(row.get("专业全称")) or clean(row.get("专业名称")),
            enrollment_type(row),
            None,
            None,
            level,
            int_or_none(row.get("计划人数")) or 0,
            decimal_or_none(row.get("学费")),
            int_or_none(row.get("学制")),
            clean(row.get("选科要求")) or "不限",
            rule_json,
            bitmap,
            "PARSED",
            "NEW" if clean(row.get("是否新增")) else "ACTIVE",
            clean(row.get("专业备注")) or None,
            int_or_none(row.get("最低位次1")),
            decimal_or_none(row.get("最低分1")),
            int_or_none(row.get("计划人数结果1")),
            int_or_none(row.get("最低位次2")),
            int_or_none(row.get("最低位次3")),
            predicted,
            rank_min,
            rank_max,
        ])

    print("-- 2026 enrollment plan seed generated by scripts/generate_undergraduate_seed_sql.py")
    print("START TRANSACTION;")
    print("DELETE FROM active_data_version WHERE data_type = 'PLAN' AND year = 2026;")
    print("DELETE FROM enrollment_plan WHERE data_version_id IN (SELECT id FROM data_version WHERE data_type = 'PLAN' AND year = 2026);")
    print("DELETE FROM data_version WHERE data_type = 'PLAN' AND year = 2026;")
    print(f"INSERT INTO data_version (data_type, year, version_no, status, row_count, published_by, published_at) VALUES ('PLAN', 2026, 1, 'PUBLISHED', {len(plan_rows)}, 0, NOW());")
    print("SET @plan_version_id = LAST_INSERT_ID();")
    print("INSERT INTO active_data_version (data_type, year, data_version_id) VALUES ('PLAN', 2026, @plan_version_id);")

    school_rows = []
    for code, row in sorted(schools.items()):
        tags = " ".join(filter(None, [
            clean(row.get("院校标签")),
            clean(row.get("院校水平")),
            clean(row.get("本科/专科")),
        ]))
        school_rows.append([
            code,
            clean(row.get("院校名称")),
            None,
            clean(row.get("所在省")) or None,
            clean(row.get("城市")) or None,
            clean(row.get("公私性质")) or clean(row.get("类型")) or None,
            tags or None,
            clean(row.get("招生章程")) or None,
            "ACTIVE",
        ])

    if school_rows:
        for start in range(0, len(school_rows), 500):
            batch = school_rows[start:start + 500]
            print("INSERT INTO school (code, name, short_name, province, city, school_type, school_tag, website, status) VALUES")
            print(",\n".join("(" + ", ".join(sql(v) for v in row) + ")" for row in batch))
            print("ON DUPLICATE KEY UPDATE name=VALUES(name), province=VALUES(province), city=VALUES(city), school_type=VALUES(school_type), school_tag=VALUES(school_tag), website=VALUES(website), status='ACTIVE';")

    major_rows = []
    for code, row in sorted(majors.items()):
        level = normalize_level(clean(row.get("专业层次")))
        major_rows.append([
            code,
            clean(row.get("专业名称")) or clean(row.get("专业全称")) or code,
            None,
            clean(row.get("门类")) or None,
            None,
            clean(row.get("专业类")) or None,
            level,
        ])
    if major_rows:
        for start in range(0, len(major_rows), 500):
            batch = major_rows[start:start + 500]
            print("INSERT INTO standard_major (code, name, category_code, category_name, subcategory_code, subcategory_name, education_level) VALUES")
            print(",\n".join("(" + ", ".join(sql(v) for v in row) + ")" for row in batch))
            print("ON DUPLICATE KEY UPDATE name=VALUES(name), category_name=VALUES(category_name), subcategory_name=VALUES(subcategory_name), education_level=VALUES(education_level);")

    plan_columns = [
        "plan_code", "year", "school_id", "school_code", "standard_major_code",
        "major_code", "major_name", "enrollment_type_code", "campus_code", "campus_name",
        "education_level", "plan_count", "tuition", "duration", "subject_requirement_text",
        "subject_rule_json", "eligible_subject_bitmap", "subject_rule_status", "plan_status",
        "remark", "last_year_min_rank", "last_year_min_score", "last_year_plan_count",
        "two_year_min_rank", "three_year_min_rank", "predicted_rank", "rank_range_min",
        "rank_range_max",
    ]
    for start in range(0, len(plan_rows), 300):
        batch = plan_rows[start:start + 300]
        print(f"INSERT INTO enrollment_plan (data_version_id, {', '.join(plan_columns)}) VALUES")
        values_sql = []
        for values in batch:
            plan_code, year, school_code, *rest = values
            selected = [
                "@plan_version_id",
                sql(plan_code),
                str(year),
                f"(SELECT id FROM school WHERE code = {sql(school_code)} LIMIT 1)",
                sql(school_code),
                *[sql(value) for value in rest],
            ]
            values_sql.append("(" + ", ".join(selected) + ")")
        print(",\n".join(values_sql) + ";")

    print("COMMIT;")


if __name__ == "__main__":
    main()
