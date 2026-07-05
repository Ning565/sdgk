#!/usr/bin/env python3
"""Organize Shandong gaokao source files into a curated data layout.

The script is intentionally non-destructive: it copies selected source files
and writes derived CSV files under data/organized/ without moving or deleting
the original messy source tree.
"""

from __future__ import annotations

import csv
import hashlib
import shutil
from dataclasses import dataclass
from pathlib import Path

import pandas as pd


ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / "data"
SRC_PACK = DATA / "14、山东-2026高考志愿填报资料"
OUT = DATA / "organized"


@dataclass(frozen=True)
class FileSpec:
    src: Path
    dst: Path
    category: str
    priority: str
    note: str
    extract: bool = False
    header: int | None = 0


def ensure_dirs() -> None:
    dirs = [
        OUT / "raw_core" / "2026_plan",
        OUT / "raw_core" / "history_admission",
        OUT / "raw_core" / "history_plan",
        OUT / "raw_core" / "score_rank",
        OUT / "raw_core" / "toudang_lines",
        OUT / "raw_validation" / "crawler_outputs",
        OUT / "reference" / "school",
        OUT / "reference" / "major",
        OUT / "reference" / "rankings",
        OUT / "reference" / "rules",
        OUT / "reference" / "templates",
        OUT / "modeling_inputs" / "extracted_csv",
        OUT / "modeling_inputs" / "assumptions",
        OUT / "manifests",
    ]
    for d in dirs:
        d.mkdir(parents=True, exist_ok=True)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def copy_file(src: Path, dst: Path) -> None:
    if not src.exists():
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)


def extract_excel(src: Path, dst_csv: Path, header: int | None = 0) -> tuple[int, int]:
    """Extract the first sheet of an Excel file to UTF-8 CSV."""
    if not src.exists():
        return 0, 0
    dst_csv.parent.mkdir(parents=True, exist_ok=True)
    df = pd.read_excel(src, sheet_name=0, dtype=str, header=header)
    df = df.dropna(how="all")
    df.to_csv(dst_csv, index=False, encoding="utf-8-sig")
    return int(df.shape[0]), int(df.shape[1])


def combine_score_rank(files: list[tuple[int, Path]], dst_csv: Path) -> tuple[int, int]:
    frames = []
    for year, src in files:
        if not src.exists():
            continue
        df = pd.read_excel(src, sheet_name=0, dtype=str)
        df = df.dropna(how="all")
        if "年份" not in df.columns:
            df.insert(0, "年份", str(year))
        frames.append(df)
    if not frames:
        return 0, 0
    out = pd.concat(frames, ignore_index=True)
    out.to_csv(dst_csv, index=False, encoding="utf-8-sig")
    return int(out.shape[0]), int(out.shape[1])


def write_assumptions() -> list[dict[str, str]]:
    assumptions_path = OUT / "modeling_inputs" / "assumptions" / "2026_undergraduate_market_assumptions.csv"
    rows = [
        {
            "metric": "undergraduate_plan_total_2026",
            "value": "287723",
            "unit": "persons",
            "source": "user_provided",
            "model_usage": "本科吸收能力上限/总量假设",
            "confidence": "medium",
        },
        {
            "metric": "ordinary_class_line_score_2026",
            "value": "442",
            "unit": "score",
            "source": "user_provided",
            "model_usage": "一段线资格边界",
            "confidence": "medium",
        },
        {
            "metric": "ordinary_class_line_reached_2026",
            "value": "355637",
            "unit": "persons",
            "source": "user_provided",
            "model_usage": "一段线上人数/本科潜在竞争池",
            "confidence": "medium",
        },
        {
            "metric": "undergraduate_plan_expansion_total_2026_vs_2025",
            "value": "17294",
            "unit": "plans",
            "source": "user_provided",
            "model_usage": "本科扩招对专科高分下沉的缓释项",
            "confidence": "medium",
        },
        {
            "metric": "sino_foreign_cooperation_expansion_2026_vs_2025",
            "value": "5823",
            "unit": "plans",
            "source": "user_provided",
            "model_usage": "中外合作本科扩招对不同支付能力考生的吸收项",
            "confidence": "medium",
        },
        {
            "metric": "physics_chemistry_special_expansion_2026_vs_2025",
            "value": "13740",
            "unit": "plans",
            "source": "user_image_subject_change",
            "model_usage": "物化组合本科计划扩招吸收项",
            "confidence": "medium",
        },
    ]
    with assumptions_path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        writer.writeheader()
        writer.writerows(rows)
    return rows


def write_subject_changes() -> list[dict[str, str]]:
    subject_path = OUT / "modeling_inputs" / "assumptions" / "2026_subject_plan_changes.csv"
    rows = [
        ("物理和化学", 172131, 158391, 13740, "high"),
        ("不限", 77397, 73390, 4007, "high"),
        ("物理", 17743, 17035, 708, "high"),
        ("思想政治", 2557, 2616, -59, "high"),
        ("地理", 2044, 1845, 199, "high"),
        ("化学", 1799, 1687, 112, "high"),
        ("生物", 1729, 1525, 204, "high"),
        ("物理和化学和生物", 1708, 1653, 55, "high"),
        ("历史", 1499, 1458, 41, "high"),
        ("思想政治和历史", 227, 223, 4, "high"),
        ("物理和生物", 192, 212, -20, "high"),
        ("物理和地理", 148, 147, 1, "high"),
        ("化学和生物", 137, 145, -8, "high"),
        ("历史和地理", 43, 41, 2, "high"),
        ("化学和地理", 41, 49, -8, "high"),
        ("思想政治和历史和地理", 32, 35, -3, "high"),
        ("物理和历史", 11, 2, 9, "medium"),
        ("生物和地理", 2, 2, 0, "high"),
        ("思想政治和地理", 1, 3, -2, "high"),
        ("生物和思想政治", 1, 3, -2, "high"),
        ("物理和生物和思想政治", 1, "", "", "low"),
        ("生物和思想政治和历史", 1, "", 1, "low"),
    ]
    fieldnames = ["subject_requirement", "plan_2026", "plan_2025", "delta", "confidence", "source"]
    out_rows = [
        {
            "subject_requirement": name,
            "plan_2026": str(plan_2026),
            "plan_2025": str(plan_2025),
            "delta": str(delta),
            "confidence": confidence,
            "source": "user_provided_image_2026_subject_plan_change",
        }
        for name, plan_2026, plan_2025, delta, confidence in rows
    ]
    with subject_path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(out_rows)
    return out_rows


def build_specs() -> list[FileSpec]:
    return [
        FileSpec(
            SRC_PACK / "2-山东26招生计划+政策汇总【持续更新】/1-山东2026招生计划/山东-2026-招生计划.xlsx",
            OUT / "raw_core/2026_plan/shandong_2026_enrollment_plan_all_batches.xlsx",
            "core_2026_plan",
            "P0",
            "2026 全批次招生计划，需清理批次行/学校行/专业行",
            True,
            0,
        ),
        FileSpec(
            SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/2022-2025山东【高职专科】志愿填报三表合一（已合并26年招生计划）.xlsx",
            OUT / "raw_core/2026_plan/shandong_2026_specialist_3in1.xlsx",
            "core_2026_specialist_plan_and_history",
            "P0",
            "2026 专科计划主表，含 2025/2024/2023 历史录取字段",
            True,
            1,
        ),
        FileSpec(
            SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/2022-2025山东【本科】志愿填报三表合一（已合并26年招生计划）.xlsx",
            OUT / "raw_core/2026_plan/shandong_2026_bachelor_3in1.xlsx",
            "core_2026_bachelor_plan_and_history",
            "P1",
            "本科计划和历史录取，用于估计本科吸收和下沉压力",
            True,
            1,
        ),
        FileSpec(
            SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/2022-2025年全国高校在山东的专业录取分数.xlsx",
            OUT / "raw_core/history_admission/shandong_major_admission_2022_2025.xlsx",
            "core_history_admission_major",
            "P0",
            "2022-2025 专业级最低分、最低位次、录取人数",
            True,
            0,
        ),
        FileSpec(
            SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/2022-2025年全国高校在山东的招生计划.xlsx",
            OUT / "raw_core/history_plan/shandong_major_plan_2022_2025.xlsx",
            "core_history_plan_major",
            "P0",
            "2022-2025 专业级招生计划、学制、学费、选科",
            True,
            0,
        ),
        FileSpec(
            SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/2022-2025年全国高校在山东的院校录取分数.xlsx",
            OUT / "raw_core/history_admission/shandong_school_admission_2022_2025.xlsx",
            "history_admission_school",
            "P1",
            "院校级录取分，可用于新增专业 fallback",
            True,
            0,
        ),
        FileSpec(DATA / "2026一分一段表.xls", OUT / "raw_core/score_rank/shandong_score_rank_2026.xls", "score_rank", "P0", "2026 一分一段原始 xls", True, 0),
        FileSpec(DATA / "2023年投档线.xls", OUT / "raw_core/toudang_lines/shandong_toudang_2023.xls", "legacy_toudang", "P1", "仓库原有 2023 投档线"),
        FileSpec(DATA / "2023年批次二投档线.xls", OUT / "raw_core/toudang_lines/shandong_batch2_toudang_2023.xls", "legacy_toudang", "P1", "仓库原有 2023 批次二投档线"),
        FileSpec(DATA / "2024年投档线.xls", OUT / "raw_core/toudang_lines/shandong_toudang_2024.xls", "legacy_toudang", "P1", "仓库原有 2024 投档线"),
        FileSpec(DATA / "2024年批次二投档线.xls", OUT / "raw_core/toudang_lines/shandong_batch2_toudang_2024.xls", "legacy_toudang", "P1", "仓库原有 2024 批次二投档线"),
        FileSpec(DATA / "2025年投档线.xls", OUT / "raw_core/toudang_lines/shandong_toudang_2025.xls", "legacy_toudang", "P1", "仓库原有 2025 投档线"),
        FileSpec(DATA / "2025年批次二投档线.xls", OUT / "raw_core/toudang_lines/shandong_batch2_toudang_2025.xls", "legacy_toudang", "P1", "仓库原有 2025 批次二投档线"),
        FileSpec(DATA / "processed/all_toudangxian.csv", OUT / "raw_core/toudang_lines/all_toudangxian_processed.csv", "processed_legacy_toudang", "P1", "已有结构化投档线"),
        FileSpec(DATA / "processed/school_basic_info.csv", OUT / "reference/school/school_basic_info_processed.csv", "school_reference", "P1", "已有学校基础信息"),
        FileSpec(
            SRC_PACK / "1-志愿填报必备资料/2、院校介绍/全国院校基础信息-带保研率.xlsx",
            OUT / "reference/school/national_school_basic_with_postgraduate_rate.xlsx",
            "school_reference",
            "P1",
            "院校属性、层次、公民办、保研率",
            True,
            1,
        ),
        FileSpec(
            SRC_PACK / "1-志愿填报必备资料/2、院校介绍/大学院校基础信息（含部标代码）.xlsx",
            OUT / "reference/school/national_school_basic_with_ministry_code.xlsx",
            "school_reference",
            "P1",
            "部标代码和学校基础属性",
            True,
            0,
        ),
        FileSpec(
            SRC_PACK / "1-志愿填报必备资料/3、专业介绍/其他专业资料（供参考）/大学专业基础信息.xlsx",
            OUT / "reference/major/major_basic_info.xlsx",
            "major_reference",
            "P1",
            "专业门类、专业类、标准名称",
            True,
            0,
        ),
        FileSpec(
            SRC_PACK / "1-志愿填报必备资料/3、专业介绍/其他专业资料（供参考）/专业满意度.xlsx",
            OUT / "reference/major/major_satisfaction.xlsx",
            "major_reference",
            "P2",
            "专业满意度弱特征",
            True,
            0,
        ),
        FileSpec(
            SRC_PACK / "1-志愿填报必备资料/3、专业介绍/其他专业资料（供参考）/专业就业信息.xlsx",
            OUT / "reference/major/major_employment_info.xlsx",
            "major_reference",
            "P2",
            "专业就业方向和薪酬弱特征",
            True,
            0,
        ),
        FileSpec(
            SRC_PACK / "1-志愿填报必备资料/1、大学排名/22-25软科排名/2025年软科中国大学排名数据.xlsx",
            OUT / "reference/rankings/shanghai_ranking_china_university_2025.xlsx",
            "ranking_reference",
            "P2",
            "院校层次弱特征",
            True,
            0,
        ),
        FileSpec(
            SRC_PACK / "1-志愿填报必备资料/6、十大热门专业分析/最新25年招生章程链接.xlsx",
            OUT / "reference/rules/admission_charter_links_2025.xlsx",
            "rule_reference",
            "P1",
            "招生章程链接，用于后续限制规则抽取",
            True,
            1,
        ),
        FileSpec(SRC_PACK / "5-高考志愿报考提前看/色觉异常、高度近视限制专业.pdf", OUT / "reference/rules/physical_exam_color_vision_myopia_restrictions.pdf", "rule_reference", "P1", "体检/色觉/近视限制，待 OCR"),
        FileSpec(SRC_PACK / "5-高考志愿报考提前看/平行志愿录取规则.pdf", OUT / "reference/rules/parallel_volunteer_rules.pdf", "rule_reference", "P1", "平行志愿规则"),
        FileSpec(SRC_PACK / "5-高考志愿报考提前看/双高计划.pdf", OUT / "reference/rules/double_high_plan.pdf", "school_reference", "P2", "高职双高计划标签，待 OCR"),
        FileSpec(SRC_PACK / "4-山东2026志愿填报样表/2026山东高考志愿填报表（新高考版）.xlsx", OUT / "reference/templates/shandong_2026_volunteer_form_template.xlsx", "template_reference", "P2", "96 志愿表导出模板"),
        FileSpec(DATA / "processed/eol_sd_enrollment_plan_2025_2026.csv", OUT / "raw_validation/crawler_outputs/eol_sd_enrollment_plan_2025_2026.csv", "validation_source", "P2", "EOL 爬取计划，用于交叉校验"),
        FileSpec(DATA / "processed/eol_sd_plan_crawl_summary.csv", OUT / "raw_validation/crawler_outputs/eol_sd_plan_crawl_summary.csv", "validation_source", "P2", "EOL 爬取进度/质量摘要"),
        FileSpec(DATA / "processed/sdzk_enrollment_plan_rows.csv", OUT / "raw_validation/crawler_outputs/sdzk_enrollment_plan_rows.csv", "validation_source", "P1", "山东考试院补充计划结构化行"),
    ]


def main() -> None:
    ensure_dirs()
    specs = build_specs()
    manifest_rows: list[dict[str, str]] = []
    extraction_rows: list[dict[str, str]] = []

    for spec in specs:
        exists = spec.src.exists()
        if exists:
            copy_file(spec.src, spec.dst)
            size = spec.dst.stat().st_size
            digest = sha256(spec.dst)
        else:
            size = 0
            digest = ""

        manifest_rows.append(
            {
                "category": spec.category,
                "priority": spec.priority,
                "source_path": str(spec.src.relative_to(ROOT)) if spec.src.is_absolute() else str(spec.src),
                "organized_path": str(spec.dst.relative_to(ROOT)),
                "exists": str(exists),
                "size_bytes": str(size),
                "sha256": digest,
                "note": spec.note,
            }
        )

        if exists and spec.extract and spec.dst.suffix.lower() in {".xlsx", ".xls"}:
            csv_name = spec.dst.stem + ".csv"
            csv_path = OUT / "modeling_inputs" / "extracted_csv" / csv_name
            try:
                rows, cols = extract_excel(spec.dst, csv_path, spec.header)
                status = "ok"
                error = ""
            except Exception as exc:  # Keep organizing even if one workbook is odd.
                rows, cols = 0, 0
                status = "failed"
                error = repr(exc)
            extraction_rows.append(
                {
                    "source": str(spec.dst.relative_to(ROOT)),
                    "csv": str(csv_path.relative_to(ROOT)),
                    "status": status,
                    "rows": str(rows),
                    "cols": str(cols),
                    "error": error,
                }
            )

    score_rank_files = [
        (2022, SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/一分一段/山东2022年的一分一段表.xlsx"),
        (2023, SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/一分一段/山东2023年的一分一段表.xlsx"),
        (2024, SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/一分一段/山东2024年的一分一段表.xlsx"),
        (2025, SRC_PACK / "3-山东高考录取数据2022-2025年【持续更新】/一分一段/山东2025年的一分一段表.xlsx"),
    ]
    for year, src in score_rank_files:
        dst = OUT / "raw_core" / "score_rank" / f"shandong_score_rank_{year}.xlsx"
        copy_file(src, dst)
        manifest_rows.append(
            {
                "category": "score_rank",
                "priority": "P0",
                "source_path": str(src.relative_to(ROOT)),
                "organized_path": str(dst.relative_to(ROOT)),
                "exists": str(src.exists()),
                "size_bytes": str(dst.stat().st_size if dst.exists() else 0),
                "sha256": sha256(dst) if dst.exists() else "",
                "note": f"{year} 一分一段表",
            }
        )

    combined_score_rank = OUT / "modeling_inputs" / "extracted_csv" / "shandong_score_rank_2022_2025.csv"
    rows, cols = combine_score_rank(score_rank_files, combined_score_rank)
    extraction_rows.append(
        {
            "source": "data/organized/raw_core/score_rank/shandong_score_rank_2022_2025.xlsx files",
            "csv": str(combined_score_rank.relative_to(ROOT)),
            "status": "ok" if rows else "failed",
            "rows": str(rows),
            "cols": str(cols),
            "error": "",
        }
    )

    write_assumptions()
    write_subject_changes()

    manifest_path = OUT / "manifests" / "data_inventory.csv"
    with manifest_path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(manifest_rows[0].keys()))
        writer.writeheader()
        writer.writerows(manifest_rows)

    extraction_path = OUT / "manifests" / "extraction_report.csv"
    with extraction_path.open("w", newline="", encoding="utf-8-sig") as f:
        writer = csv.DictWriter(f, fieldnames=list(extraction_rows[0].keys()))
        writer.writeheader()
        writer.writerows(extraction_rows)

    print(f"organized_root={OUT}")
    print(f"manifest={manifest_path}")
    print(f"extraction_report={extraction_path}")
    print(f"files_indexed={len(manifest_rows)}")
    print(f"extractions={len(extraction_rows)}")


if __name__ == "__main__":
    main()
