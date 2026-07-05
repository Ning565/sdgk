# 山东高考建模数据整理目录

> 生成脚本：`scripts/organize_gaokao_data.py`  
> 整理原则：只复制和派生，不移动、不删除原始 `data/` 文件。

## 目录结构

| 目录 | 用途 |
| --- | --- |
| `raw_core/2026_plan/` | 2026 招生计划核心原始表，包括全批次计划、专科三表合一、本科三表合一 |
| `raw_core/history_admission/` | 2022-2025 专业级/院校级录取结果原始表 |
| `raw_core/history_plan/` | 2022-2025 专业级招生计划原始表 |
| `raw_core/score_rank/` | 2022-2026 一分一段表原始文件 |
| `raw_core/toudang_lines/` | 仓库原有投档线和结构化投档线 |
| `raw_validation/` | EOL、山东考试院等校验来源，不作为 MVP 阻塞项 |
| `reference/school/` | 院校属性、部标代码、公民办、本专科、保研率等参考数据 |
| `reference/major/` | 专业门类、专业满意度、就业信息等参考数据 |
| `reference/rankings/` | 软科等院校层次弱特征 |
| `reference/rules/` | 招生章程链接、体检限制、平行志愿、双高计划等规则来源 |
| `reference/templates/` | 山东 2026 志愿填报样表 |
| `modeling_inputs/extracted_csv/` | 从 Excel 提取出的可直接读取 CSV |
| `modeling_inputs/assumptions/` | 当前无法确定但必须显式进入模型的 2026 假设数据 |
| `manifests/` | 文件清单、hash、提取报告 |

## MVP 主输入

第一版模型优先读取这些文件：

| 文件 | 作用 |
| --- | --- |
| `modeling_inputs/extracted_csv/shandong_2026_specialist_3in1.csv` | 2026 专科计划主表，含 2025/2024/2023 历史字段 |
| `modeling_inputs/extracted_csv/shandong_2026_enrollment_plan_all_batches.csv` | 2026 全批次计划，用于校验三表合一和识别特殊计划 |
| `modeling_inputs/extracted_csv/shandong_major_admission_2022_2025.csv` | 2022-2025 专业级最低分、最低位次、录取人数 |
| `modeling_inputs/extracted_csv/shandong_major_plan_2022_2025.csv` | 2022-2025 专业级计划人数、学制、学费、选科 |
| `modeling_inputs/extracted_csv/shandong_score_rank_2022_2025.csv` | 2022-2025 一分一段合并表 |
| `modeling_inputs/extracted_csv/shandong_score_rank_2026.csv` | 2026 一分一段提取表 |
| `raw_core/score_rank/shandong_score_rank_2026.xls` | 2026 一分一段原始表 |
| `modeling_inputs/assumptions/2026_undergraduate_market_assumptions.csv` | 本科计划、一段线人数、本科扩招等市场假设 |
| `modeling_inputs/assumptions/2026_subject_plan_changes.csv` | 2026 vs 2025 选科计划变化 |

## 关于本科剩余考生流

当前还没有“本科第 1 次录取后各位次段剩余考生流”和“2026 第 2 次志愿正式剩余本科计划”的实际数据。模型不要把这部分假装成真实观测值。

第一版处理方式：

1. 把 `2026_undergraduate_market_assumptions.csv` 和 `2026_subject_plan_changes.csv` 作为情景参数。
2. 在 `rank_segment_flow` 中只做预估，不输出过度精确概率。
3. 本科第 1 次录取结束、官方第 2 次志愿计划发布后，再生成 post 版本数据。

## 质量检查

检查文件：

| 文件 | 内容 |
| --- | --- |
| `manifests/data_inventory.csv` | 已整理文件、原路径、整理路径、优先级、sha256 |
| `manifests/extraction_report.csv` | Excel 提取到 CSV 的行列数和错误信息 |

当前核心提取结果：

| CSV | 行数 | 列数 |
| --- | ---: | ---: |
| `shandong_2026_specialist_3in1.csv` | 13,910 | 64 |
| `shandong_2026_enrollment_plan_all_batches.csv` | 27,610 | 19 |
| `shandong_2026_bachelor_3in1.csv` | 25,655 | 67 |
| `shandong_major_admission_2022_2025.csv` | 124,870 | 17 |
| `shandong_major_plan_2022_2025.csv` | 130,747 | 14 |
| `shandong_school_admission_2022_2025.csv` | 11,687 | 16 |
| `shandong_score_rank_2022_2025.csv` | 2,173 | 9 |
| `shandong_score_rank_2026.csv` | 550 | 15 |

## 重新整理

如果原始资料继续补充到 `data/`，重新运行：

```bash
python3 scripts/organize_gaokao_data.py
```

脚本会覆盖 `data/organized/` 下的同名派生文件，但不会删除或移动原始资料。
