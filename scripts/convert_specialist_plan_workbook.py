#!/usr/bin/env python3
"""Convert the 2023-2026 specialist admission workbook into model input CSV.

The source workbook is a wide manually curated sheet:
- A:S are 2026 enrollment-plan columns.
- T:AA are 2025 admission columns.
- AB:AG are 2024 admission columns.
- AH:AM are 2023 admission columns.
- AN onward are school and major reference columns.

The training script already expects this exact "three-in-one" shape with
duplicate historical column names disambiguated by pandas as .1/.2 suffixes.
This converter makes the import deterministic and filters out rows without a
usable 2026 specialist plan.
"""

from __future__ import annotations

import json
import math
import re
from pathlib import Path
from typing import Any

import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "data/2023-2026投档表专科.xlsx"
OUT = ROOT / "data/organized/modeling_inputs/extracted_csv/shandong_2026_specialist_3in1.csv"
META = ROOT / "data/organized/modeling_inputs/extracted_csv/shandong_2026_specialist_3in1.meta.json"

REQUIRED_COLUMNS = [
    "年份",
    "生源地",
    "批次",
    "科类",
    "计划类别",
    "院校代码",
    "院校名称",
    "专业代码",
    "专业全称",
    "专业名称",
    "选科要求",
    "专业层次",
    "计划人数",
    "学制",
    "学费",
    "门类",
    "专业类",
    "录取人数",
    "最低分",
    "最低位次",
    "录取人数.1",
    "最低分.1",
    "最低位次.1",
    "录取人数.2",
    "最低分.2",
    "最低位次.2",
]


def clean_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, float) and math.isnan(value):
        return ""
    text = str(value).strip()
    if text.lower() in {"nan", "none"}:
        return ""
    return text


def int_like(value: Any) -> int | None:
    text = clean_text(value).replace(",", "")
    if not text:
        return None
    match = re.search(r"-?\d+(?:\.\d+)?", text)
    if not match:
        return None
    number = float(match.group())
    if number <= 0:
        return None
    return int(round(number))


def ensure_required_columns(df: pd.DataFrame) -> None:
    missing = [col for col in REQUIRED_COLUMNS if col not in df.columns]
    if missing:
        raise ValueError(f"专科投档表缺少必要列: {missing}")


def main() -> None:
    if not SOURCE.is_file():
        raise FileNotFoundError(SOURCE)

    OUT.parent.mkdir(parents=True, exist_ok=True)

    df = pd.read_excel(SOURCE, sheet_name=0, header=1, dtype=str)
    df = df.dropna(how="all")
    df.columns = [clean_text(col) for col in df.columns]
    ensure_required_columns(df)

    for col in df.columns:
        df[col] = df[col].map(clean_text)

    before_rows = len(df)
    df = df[df["年份"].map(int_like).eq(2026)]
    df = df[df["专业层次"].str.contains("专科", na=False)]
    df = df[df["计划人数"].map(int_like).fillna(0).astype(int) > 0]
    df = df[df["院校代码"].str.len() > 0]
    df = df[df["专业名称"].str.len() > 0]

    # Normalize common blanks without destroying raw labels from the workbook.
    df.loc[df["选科要求"].eq(""), "选科要求"] = "不限"
    df.loc[df["批次"].eq(""), "批次"] = "专科批"
    df.loc[df["科类"].eq(""), "科类"] = "综合"
    df.loc[df["生源地"].eq(""), "生源地"] = "山东"

    df.to_csv(OUT, index=False, encoding="utf-8-sig")

    meta = {
        "source": str(SOURCE.relative_to(ROOT)),
        "output": str(OUT.relative_to(ROOT)),
        "source_rows": int(before_rows),
        "output_rows": int(len(df)),
        "sheet": "山东专科",
        "header_row": 2,
        "rules": [
            "以2026招生计划列为准，仅保留年份=2026、专业层次含专科、计划人数>0的院校专业行。",
            "2025/2024/2023历史录取列按原宽表顺序保留，供训练脚本识别为无后缀/.1/.2。",
            "空白历史列表示该年该专业无招生或无可用投档记录，不做补零。",
        ],
    }
    META.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(meta, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
