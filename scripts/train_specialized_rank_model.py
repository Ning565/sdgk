#!/usr/bin/env python3
"""Train a pragmatic Shandong specialized-rank recommendation model.

This is a fast baseline model for launch:
- Reads the organized 2026 specialist 3-in-1 table.
- Builds a robust weighted historical cutoff rank estimate.
- Applies transparent adjustments for plan size, plan type, school/major
  attractiveness, tuition, and the current undergraduate expansion scenario.
- Writes a compact CSV artifact that the Java app can load at runtime.

The output is not a black-box ML model; it is a calibrated, inspectable model
artifact built from local data so the website can serve recommendations today.
"""

from __future__ import annotations

import hashlib
import json
import math
import re
from pathlib import Path
from typing import Any

import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
IN = ROOT / "data/organized/modeling_inputs/extracted_csv/shandong_2026_specialist_3in1.csv"
ASSUMPTIONS = ROOT / "data/organized/modeling_inputs/assumptions/2026_undergraduate_market_assumptions.csv"
OUT_DIR = ROOT / "data/organized/modeling_outputs"
OUT_CSV = OUT_DIR / "specialized_rank_model_2026.csv"
OUT_META = OUT_DIR / "specialized_rank_model_2026.meta.json"

MODEL_VERSION = "specialized-rank-v2-specialist-workbook"


HOT_MAJOR_PATTERNS = {
    "医学": 0.045,
    "口腔": 0.070,
    "护理": 0.030,
    "药学": 0.025,
    "眼视光": 0.035,
    "铁路": 0.040,
    "铁道": 0.040,
    "轨道": 0.030,
    "电力": 0.040,
    "电气": 0.035,
    "计算机": 0.025,
    "软件": 0.025,
    "人工智能": 0.025,
    "大数据": 0.020,
    "师范": 0.020,
    "动物医学": 0.030,
}


def s(value: Any) -> str:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return ""
    text = str(value).strip()
    return "" if text.lower() == "nan" else text


def num(value: Any) -> float | None:
    text = s(value)
    if not text or text in {"/", "待定", "免费"}:
        return 0.0 if text == "免费" else None
    text = text.replace(",", "")
    m = re.search(r"-?\d+(?:\.\d+)?", text)
    return float(m.group()) if m else None


def int_num(value: Any) -> int | None:
    n = num(value)
    if n is None or n <= 0:
        return None
    return int(round(n))


def stable_id(*parts: Any, modulo: int = 9_000_000_000) -> int:
    raw = "|".join(s(p) for p in parts)
    digest = hashlib.sha1(raw.encode("utf-8")).hexdigest()
    return 1_000_000_000 + (int(digest[:12], 16) % modulo)


def normalize_enrollment_type(plan_type: str, major_full: str) -> str:
    text = plan_type + " " + major_full
    if "3+2" in text or "贯通" in text:
        return "高职3+2"
    if "中外" in text:
        return "中外合作"
    if "校企" in text:
        return "校企合作"
    if "专项" in text:
        return "专项计划"
    if "技师" in text:
        return "高职技师"
    if "公费" in text:
        return "公费医学生"
    return "普通计划"


def subject_tokens(req: str) -> set[str]:
    text = s(req)
    if not text or text == "不限":
        return set()
    tokens = set()
    for name in ["物理", "化学", "生物", "思想政治", "政治", "历史", "地理"]:
        if name in text:
            tokens.add("思想政治" if name == "政治" else name)
    return tokens


def rank_weighted(row: pd.Series) -> tuple[float, int, float]:
    ranks = [
        int_num(row.get("最低位次")),
        int_num(row.get("最低位次.1")),
        int_num(row.get("最低位次.2")),
    ]
    weights = [0.55, 0.30, 0.15]
    valid = [(r, w) for r, w in zip(ranks, weights) if r]
    if not valid:
        fallback = int_num(row.get("院校最低位次")) or 520_000
        return float(fallback), 0, 0.22
    total_w = sum(w for _, w in valid)
    mean = sum(r * w for r, w in valid) / total_w
    if len(valid) >= 2:
        plain = [r for r, _ in valid]
        avg = sum(plain) / len(plain)
        vol = min(0.35, (sum(abs(r - avg) for r in plain) / len(plain)) / max(avg, 1))
    else:
        vol = 0.16
    return mean, len(valid), vol


def attractiveness_adjust(row: pd.Series, enrollment_type: str) -> tuple[float, list[str]]:
    """Return rank multiplier delta. Negative means harder, positive easier."""
    delta = 0.0
    reasons: list[str] = []
    major = s(row.get("专业全称")) + " " + s(row.get("专业名称")) + " " + s(row.get("专业类"))
    school_nature = s(row.get("公私性质"))
    school_level = s(row.get("本科/专科")) + " " + s(row.get("院校水平"))
    city_tag = s(row.get("城市标签"))
    tuition = num(row.get("学费"))

    if "公办" in school_nature:
        delta -= 0.030
        reasons.append("公办院校竞争偏强")
    elif "民办" in school_nature:
        delta += 0.035
        reasons.append("民办院校竞争相对缓和")

    if "本科" in school_level:
        delta -= 0.020
        reasons.append("本科院校专科专业吸引力较高")

    if "3+2" in enrollment_type:
        delta -= 0.080
        reasons.append("高职3+2贯通培养热度高")
    elif "中外" in enrollment_type:
        delta += 0.045
        reasons.append("中外合作学费门槛降低竞争")
    elif "校企" in enrollment_type:
        delta += 0.020
        reasons.append("校企合作按弱热度处理")
    elif "专项" in enrollment_type or "公费" in enrollment_type:
        delta -= 0.025
        reasons.append("特殊计划需资格匹配")

    if "一线" in city_tag or "新一线" in city_tag:
        delta -= 0.015
        reasons.append("城市吸引力较强")

    if tuition is not None:
        if tuition >= 18000:
            delta += 0.040
            reasons.append("学费较高")
        elif 0 < tuition <= 6000:
            delta -= 0.012
            reasons.append("学费较低")

    for pattern, effect in HOT_MAJOR_PATTERNS.items():
        if pattern in major:
            delta -= effect
            reasons.append(f"{pattern}类专业热度较高")
            break

    return delta, reasons


def plan_adjust(row: pd.Series) -> tuple[float, str]:
    current = int_num(row.get("计划人数")) or 0
    last_admit = int_num(row.get("录取人数"))
    if current <= 0 or not last_admit:
        return 0.0, "新增"
    ratio = max(0.25, min(4.0, current / max(last_admit, 1)))
    # More seats make cutoff rank looser; fewer seats make it harder.
    delta = math.log(ratio) * 0.055
    if current - last_admit > 0:
        label = "增加"
    elif current - last_admit < 0:
        label = "减少"
    else:
        label = "持平"
    return delta, label


def scenario_adjust(row: pd.Series) -> tuple[float, list[str]]:
    req = s(row.get("选科要求"))
    reasons: list[str] = []
    delta = 0.012
    reasons.append("2026本科整体扩招，专科高分下沉压力按预估缓释")
    if "物理" in req and "化学" in req:
        delta += 0.018
        reasons.append("物理+化学本科计划扩招明显")
    if req == "不限":
        delta += 0.006
        reasons.append("不限选科本科计划扩招")
    return delta, reasons


def confidence(history_years: int, volatility: float, current_plan: int | None) -> str:
    if history_years >= 3 and volatility < 0.12 and (current_plan or 0) >= 10:
        return "高"
    if history_years >= 2 and volatility < 0.22:
        return "中"
    return "低"


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    df = pd.read_csv(IN, dtype=str)
    rows: list[dict[str, Any]] = []

    for idx, row in df.iterrows():
        school_code = s(row.get("院校代码"))
        major_code = s(row.get("专业代码"))
        school_name = s(row.get("院校名称"))
        major_name = s(row.get("专业全称")) or s(row.get("专业名称"))
        enrollment_type = normalize_enrollment_type(s(row.get("计划类别")), major_name)
        plan_count = int_num(row.get("计划人数")) or 0
        base_rank, history_years, vol = rank_weighted(row)
        plan_delta, plan_change = plan_adjust(row)
        attr_delta, attr_reasons = attractiveness_adjust(row, enrollment_type)
        scenario_delta, scenario_reasons = scenario_adjust(row)

        total_delta = max(-0.22, min(0.22, plan_delta + attr_delta + scenario_delta))
        p50 = int(max(1, round(base_rank * (1.0 + total_delta))))

        width = 0.075 + min(0.22, vol * 0.55)
        if plan_count < 5:
            width += 0.060
        elif plan_count < 10:
            width += 0.035
        if history_years == 0:
            width += 0.120
        elif history_years == 1:
            width += 0.065
        if "新增" in s(row.get("是否新增")) or plan_change == "新增":
            width += 0.045
        width = max(0.07, min(0.32, width))

        p10 = int(max(1, round(p50 * (1.0 - width))))
        p90 = int(max(p10 + 1, round(p50 * (1.0 + width))))
        conf = confidence(history_years, vol, plan_count)

        risk = []
        if plan_count < 10:
            risk.append("小计划波动较大")
        if history_years < 2:
            risk.append("历史样本不足")
        if vol >= 0.20:
            risk.append("历年位次波动较大")
        if "3+2" in enrollment_type:
            risk.append("3+2可能出现高分下沉")
        if not risk:
            risk.append("历史走势相对稳定")

        plan_id = stable_id(school_code, major_code, major_name, enrollment_type)
        school_id = stable_id(school_code, school_name, modulo=900_000_000)
        reasons = []
        if plan_change != "持平":
            reasons.append(f"计划{plan_change}")
        reasons.extend(attr_reasons[:2])
        reasons.extend(scenario_reasons[:2])
        if not reasons:
            reasons.append("按近三年专业最低位次加权预测")

        rows.append(
            {
                "plan_id": plan_id,
                "school_id": school_id,
                "school_code": school_code,
                "school_name": school_name,
                "major_code": major_code,
                "major_name": major_name,
                "major_short_name": s(row.get("专业名称")),
                "province": s(row.get("所在省")),
                "city": s(row.get("城市")),
                "city_tag": s(row.get("城市标签")),
                "school_type": s(row.get("公私性质")),
                "school_tag": s(row.get("院校水平")) or s(row.get("院校标签")),
                "major_category": s(row.get("门类")),
                "major_subcategory": s(row.get("专业类")),
                "enrollment_type": enrollment_type,
                "education_level": "SPECIALIZED",
                "plan_count": plan_count,
                "tuition": int_num(row.get("学费")) or "",
                "duration": int_num(row.get("学制")) or "",
                "subject_requirement": s(row.get("选科要求")) or "不限",
                "subject_tokens": "|".join(sorted(subject_tokens(row.get("选科要求")))),
                "last_year_min_score": int_num(row.get("最低分")) or "",
                "last_year_min_rank": int_num(row.get("最低位次")) or "",
                "two_year_min_rank": int_num(row.get("最低位次.1")) or "",
                "three_year_min_rank": int_num(row.get("最低位次.2")) or "",
                "last_year_plan_count": int_num(row.get("录取人数")) or "",
                "predicted_rank_p10": p10,
                "predicted_rank_p50": p50,
                "predicted_rank_p90": p90,
                "volatility": round(vol, 4),
                "history_years": history_years,
                "confidence": conf,
                "plan_change": plan_change,
                "risk_tips": "；".join(risk[:3]),
                "reason_text": "；".join(reasons[:4]),
                "model_version": MODEL_VERSION,
                "source": "2023-2026投档表专科.xlsx",
            }
        )

    out = pd.DataFrame(rows)
    out = out.sort_values(["predicted_rank_p50", "school_name", "major_name"], ascending=[True, True, True])
    out.to_csv(OUT_CSV, index=False, encoding="utf-8-sig")

    meta = {
        "model_version": MODEL_VERSION,
        "input": str(IN.relative_to(ROOT)),
        "output": str(OUT_CSV.relative_to(ROOT)),
        "row_count": int(len(out)),
        "assumptions": str(ASSUMPTIONS.relative_to(ROOT)),
        "probability_runtime": "Java computes candidate-specific probability from p10/p50/p90 and clamps to 1..99.99",
        "notes": [
            "本科第1次录取后剩余考生流尚无真实观测，当前只使用扩招情景参数。",
            "专科模型以 data/2023-2026投档表专科.xlsx 转换后的宽表为主数据源。",
            "历史列为空表示该年该院校专业无招生或无可用投档记录，不按0处理。",
        ],
    }
    OUT_META.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(meta, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
