#!/usr/bin/env python3
"""Low-rate crawler for gaokao.pub Shandong plan/admission pages.

gaokao.pub exposes 2025 Shandong undergraduate rows in HTML. The page is not a
2026 full enrollment-plan source, but it carries school-major rows with plan
counts and is useful as an independent 2025 validation/supplement source.
"""

from __future__ import annotations

import argparse
import csv
import html
import random
import re
import time
from datetime import datetime
from itertools import combinations
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen


BASE_URL = "https://www.gaokao.pub/index.php"
RAW_DIR = Path("data/raw/gaokao_pub_plans")
OUT_PATH = Path("data/processed/gaokao_pub_2025_plan_rows.csv")
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)
SUBJECTS = ["物理", "化学", "生物", "历史", "政治", "地理"]


FIELDS = [
    "source",
    "crawl_time",
    "year",
    "query_subject_combo",
    "page",
    "school_code",
    "school_name",
    "school_province",
    "school_city",
    "school_attr",
    "school_url",
    "major_code",
    "major_name",
    "subject_requirement",
    "plan_count",
    "tuition",
    "score",
    "min_rank",
    "source_url",
]


def clean_text(value: str) -> str:
    value = html.unescape(re.sub(r"<[^>]+>", " ", value or ""))
    return re.sub(r"\s+", " ", value).strip()


def fetch(url: str, timeout: int = 30) -> str:
    req = Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
            "Referer": "https://www.gaokao.pub/plans.html",
        },
    )
    with urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="replace")


def make_url(combo: tuple[str, str, str], page: int) -> str:
    params = {
        "route": "common/plan",
        "ke_mu": "-".join(combo),
        "low_score": "0",
        "high_score": "750",
    }
    if page > 1:
        params["page"] = str(page)
    return f"{BASE_URL}?{urlencode(params)}"


def max_page(text: str) -> int:
    pages = [int(x) for x in re.findall(r"page=(\d+)", text)]
    return max(pages) if pages else 1


def parse_rows(text: str, combo: tuple[str, str, str], page: int, source_url: str, crawl_time: str) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    school_blocks = re.split(r'<tr class="college-info">', text)[1:]
    for block in school_blocks:
        school_part, _, major_part = block.partition('<tr id="')
        school_cells = re.findall(r"<td[^>]*>(.*?)</td>", school_part, flags=re.S)
        school_span = re.search(r'<span class="pr5 f12 fcolor999">(.*?)</span>', school_part, flags=re.S)
        school_text = clean_text(school_span.group(1) if school_span else "")
        match = re.match(r"([A-Z]\d{3})\s+(.+)", school_text)
        if not match:
            continue
        school_code, school_name = match.group(1), match.group(2)
        school_url_match = re.search(r'<a target="_blank" href="([^"]+)"', school_part)
        school_url = html.unescape(school_url_match.group(1)) if school_url_match else ""
        school_province = clean_text(school_cells[2]) if len(school_cells) > 2 else ""
        school_city = clean_text(school_cells[3]) if len(school_cells) > 3 else ""
        school_attr = clean_text(school_cells[4]) if len(school_cells) > 4 else ""

        for major_row in re.findall(r"<tr>\s*<td></td>(.*?)</tr>", major_part, flags=re.S):
            cells = re.findall(r"<td[^>]*>(.*?)</td>", major_row, flags=re.S)
            if len(cells) < 6:
                continue
            major_text = clean_text(cells[0])
            major_match = re.match(r"([0-9A-Za-z]{2})\s+(.+)", major_text)
            major_code = major_match.group(1) if major_match else ""
            major_name = major_match.group(2) if major_match else major_text
            rows.append(
                {
                    "source": "成均志愿填报助手 gaokao.pub",
                    "crawl_time": crawl_time,
                    "year": "2025",
                    "query_subject_combo": "-".join(combo),
                    "page": str(page),
                    "school_code": school_code,
                    "school_name": school_name,
                    "school_province": school_province,
                    "school_city": school_city,
                    "school_attr": school_attr,
                    "school_url": school_url,
                    "major_code": major_code,
                    "major_name": major_name,
                    "subject_requirement": clean_text(cells[1]),
                    "plan_count": clean_text(cells[2]),
                    "tuition": clean_text(cells[3]),
                    "score": clean_text(cells[4]).replace("分", ""),
                    "min_rank": clean_text(cells[5]),
                    "source_url": source_url,
                }
            )
    return rows


def write_rows(rows: list[dict[str, str]], out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    seen: set[tuple[str, str, str, str]] = set()
    unique = []
    for row in rows:
        key = (row["school_code"], row["major_code"], row["major_name"], row["subject_requirement"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(row)
    with out_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=FIELDS)
        writer.writeheader()
        writer.writerows(unique)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--raw-dir", type=Path, default=RAW_DIR)
    parser.add_argument("--out", type=Path, default=OUT_PATH)
    parser.add_argument("--min-sleep", type=float, default=2.5)
    parser.add_argument("--max-sleep", type=float, default=6.0)
    parser.add_argument("--limit-combos", type=int, default=0)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    crawl_time = datetime.now().isoformat(timespec="seconds")
    combos = list(combinations(SUBJECTS, 3))
    if args.limit_combos:
        combos = combos[: args.limit_combos]

    all_rows: list[dict[str, str]] = []
    args.raw_dir.mkdir(parents=True, exist_ok=True)
    for combo_idx, combo in enumerate(combos, start=1):
        page = 1
        combo_pages = 1
        combo_rows = 0
        while page <= combo_pages:
            url = make_url(combo, page)
            raw_path = args.raw_dir / ("_".join(combo)) / f"page_{page}.html"
            raw_path.parent.mkdir(parents=True, exist_ok=True)
            if raw_path.exists():
                text = raw_path.read_text(encoding="utf-8", errors="replace")
            else:
                time.sleep(random.uniform(args.min_sleep, args.max_sleep))
                text = fetch(url)
                raw_path.write_text(text, encoding="utf-8")
            if page == 1:
                combo_pages = max_page(text)
            rows = parse_rows(text, combo, page, url, crawl_time)
            all_rows.extend(rows)
            combo_rows += len(rows)
            if page % 10 == 0 or page == combo_pages:
                write_rows(all_rows, args.out)
                print(
                    f"combo {combo_idx}/{len(combos)} {'-'.join(combo)} "
                    f"page {page}/{combo_pages}, combo_rows={combo_rows}, total_raw_rows={len(all_rows)}",
                    flush=True,
                )
            page += 1

    write_rows(all_rows, args.out)
    print(f"Done. Output: {args.out}", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
