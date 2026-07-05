#!/usr/bin/env python3
"""Crawl Shandong enrollment plans from China Education Online / 掌上高考 API.

The public API used here is the same data family exposed by gaokao.cn/eol.cn:
`apidata/api/gkv3/plan/school`. It returns plan rows by school, target province,
and year. Raw JSON responses are cached so interrupted runs can be resumed.
"""

from __future__ import annotations

import argparse
import csv
import json
import random
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlencode, urlparse
from urllib.request import Request, urlopen


SCHOOL_NAME_URL = "https://static-data.gaokao.cn/www/2.0/school/name.json"
API_URL = "https://api.eol.cn/gkcx/api/"
LOCAL_PROVINCE_ID = "37"
LOCAL_PROVINCE_NAME = "山东"
RAW_DIR = Path("data/raw/eol_plans")
PROCESSED_DIR = Path("data/processed")
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
)


CSV_FIELDS = [
    "source",
    "source_api",
    "crawl_time",
    "year",
    "local_province_id",
    "local_province_name",
    "school_id",
    "school_name",
    "school_short",
    "school_province_id",
    "school_type",
    "local_batch_name",
    "local_type_name",
    "zslx_name",
    "special_group",
    "sg_name",
    "sg_info",
    "major_code",
    "major_name",
    "standard_major_code",
    "level2_name",
    "level3_name",
    "subject_requirement",
    "duration",
    "plan_count",
    "tuition",
    "tuition_unit",
    "remark",
    "raw_sp_xuanke",
    "raw_json",
]


_RATE_LOCK = threading.Lock()
_LAST_REQUEST_AT = 0.0


def request_json(url: str, timeout: int = 30, min_interval: float = 0.0) -> dict[str, Any]:
    global _LAST_REQUEST_AT
    if min_interval > 0:
        with _RATE_LOCK:
            wait = min_interval - (time.monotonic() - _LAST_REQUEST_AT)
            if wait > 0:
                time.sleep(wait)
            _LAST_REQUEST_AT = time.monotonic()
    req = Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Referer": "https://www.gaokao.cn/",
            "Accept": "application/json,text/plain,*/*",
        },
    )
    with urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def load_schools(cache_path: Path) -> list[dict[str, Any]]:
    if cache_path.exists():
        data = json.loads(cache_path.read_text(encoding="utf-8"))
    else:
        data = request_json(SCHOOL_NAME_URL)
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return data.get("data", [])


def plan_url(school_id: str, year: int, size: int, page: int = 1) -> str:
    params = {
        "access_token": "",
        "local_province_id": LOCAL_PROVINCE_ID,
        "page": str(page),
        "school_id": str(school_id),
        "year": str(year),
        "signsafe": "",
        "size": str(size),
        "uri": "apidata/api/gkv3/plan/school",
    }
    return f"{API_URL}?{urlencode(params)}"


def is_legacy_empty_cache(data: dict[str, Any]) -> bool:
    """Return True for known-bad empty caches produced by earlier crawler settings."""
    payload = data.get("data")
    if isinstance(payload, dict) and payload.get("item"):
        return False
    request_url = str(data.get("_request_url") or "")
    if not request_url:
        return False
    if "api.eol.cn/web/api/" in request_url:
        return True
    params = parse_qs(urlparse(request_url).query)
    try:
        return int((params.get("size") or ["0"])[0]) > 20
    except ValueError:
        return False


def fetch_plan(
    school: dict[str, Any],
    year: int,
    raw_root: Path,
    size: int,
    overwrite: bool,
    retries: int,
    timeout: int,
    min_interval: float,
    max_interval: float,
    rate_limit_sleep: float,
) -> tuple[str, int, int, str]:
    school_id = str(school["school_id"])
    raw_path = raw_root / str(year) / f"{school_id}.json"
    if raw_path.exists() and not overwrite:
        try:
            data = json.loads(raw_path.read_text(encoding="utf-8"))
            if is_legacy_empty_cache(data):
                raise ValueError("legacy empty cache from unsupported endpoint/page size")
            payload = data.get("data") if isinstance(data.get("data"), dict) else {}
            count = len(payload.get("item", []))
            num_found = int(payload.get("numFound") or count)
            return school_id, count, num_found, "cached"
        except Exception:
            pass

    page_size = min(size, 20)
    last_error = ""
    for attempt in range(retries + 1):
        try:
            interval = random.uniform(min_interval, max_interval) if max_interval > min_interval else min_interval
            first_url = plan_url(school_id, year, page_size, page=1)
            data = request_json(first_url, timeout=timeout, min_interval=interval)
            if data.get("code") == "1069":
                time.sleep(rate_limit_sleep)
                last_error = str(data.get("message", "rate limited"))
                continue
            if data.get("code") != "0000":
                last_error = str(data.get("message", "unknown error"))
                time.sleep(0.5 + attempt * 0.5)
                continue
            payload = data.get("data") if isinstance(data.get("data"), dict) else {}
            items = list(payload.get("item", []) or [])
            num_found = int(payload.get("numFound") or len(items))
            pages = (num_found + page_size - 1) // page_size if num_found else 1
            page_urls = [first_url]
            for page in range(2, pages + 1):
                interval = random.uniform(min_interval, max_interval) if max_interval > min_interval else min_interval
                page_url = plan_url(school_id, year, page_size, page=page)
                page_data = request_json(page_url, timeout=timeout, min_interval=interval)
                if page_data.get("code") == "1069":
                    time.sleep(rate_limit_sleep)
                    last_error = str(page_data.get("message", "rate limited"))
                    raise RuntimeError(last_error)
                if page_data.get("code") != "0000":
                    last_error = str(page_data.get("message", "unknown error"))
                    raise RuntimeError(last_error)
                page_payload = page_data.get("data") if isinstance(page_data.get("data"), dict) else {}
                items.extend(page_payload.get("item", []) or [])
                page_urls.append(page_url)
            data["_request_url"] = first_url
            data["_request_urls"] = page_urls
            data["data"] = {"item": items, "numFound": num_found}
            raw_path.parent.mkdir(parents=True, exist_ok=True)
            raw_path.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            count = len(items)
            return school_id, count, num_found, "fetched"
        except Exception as exc:  # network and decode errors are retryable here.
            last_error = repr(exc)
            time.sleep(0.5 + attempt * 0.5)

    return school_id, 0, 0, f"failed: {last_error}"


def normalize_row(
    item: dict[str, Any],
    school: dict[str, Any],
    source_api: str,
    crawl_time: str,
) -> dict[str, str]:
    major_name = str(item.get("spname") or item.get("sp_name") or "")
    return {
        "source": "中国教育在线/掌上高考公开API",
        "source_api": source_api,
        "crawl_time": crawl_time,
        "year": str(item.get("year", "")),
        "local_province_id": LOCAL_PROVINCE_ID,
        "local_province_name": str(item.get("local_province_name") or LOCAL_PROVINCE_NAME),
        "school_id": str(item.get("school_id") or school.get("school_id") or ""),
        "school_name": str(item.get("name") or school.get("name") or ""),
        "school_short": str(school.get("short") or ""),
        "school_province_id": str(school.get("proid") or ""),
        "school_type": str(school.get("type") or ""),
        "local_batch_name": str(item.get("local_batch_name") or ""),
        "local_type_name": str(item.get("local_type_name") or ""),
        "zslx_name": str(item.get("zslx_name") or ""),
        "special_group": str(item.get("special_group") or ""),
        "sg_name": str(item.get("sg_name") or ""),
        "sg_info": str(item.get("sg_info") or ""),
        "major_code": str(item.get("spcode") or ""),
        "major_name": major_name,
        "standard_major_code": str(item.get("sp_type") or ""),
        "level2_name": str(item.get("level2_name") or ""),
        "level3_name": str(item.get("level3_name") or ""),
        "subject_requirement": str(item.get("sp_info") or ""),
        "duration": str(item.get("length") or ""),
        "plan_count": str(item.get("num") or ""),
        "tuition": str(item.get("tuition") or ""),
        "tuition_unit": str(item.get("tuition_unit") or ""),
        "remark": str(item.get("remark") or item.get("info") or ""),
        "raw_sp_xuanke": str(item.get("sp_xuanke") or ""),
        "raw_json": json.dumps(item, ensure_ascii=False, separators=(",", ":")),
    }


def write_csv(
    schools: list[dict[str, Any]],
    years: list[int],
    raw_root: Path,
    out_path: Path,
    crawl_time: str,
    size: int,
) -> int:
    school_by_id = {str(s["school_id"]): s for s in schools}
    out_path.parent.mkdir(parents=True, exist_ok=True)
    total = 0
    with out_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for year in years:
            for raw_path in sorted((raw_root / str(year)).glob("*.json")):
                data = json.loads(raw_path.read_text(encoding="utf-8"))
                payload = data.get("data") if isinstance(data.get("data"), dict) else {}
                items = payload.get("item", [])
                if not items:
                    continue
                school_id = raw_path.stem
                school = school_by_id.get(school_id, {"school_id": school_id})
                api = data.get("_request_url") or plan_url(school_id, year, size)
                for item in items:
                    writer.writerow(normalize_row(item, school, api, crawl_time))
                    total += 1
    return total


def write_summary(out_path: Path, rows: list[dict[str, str]]) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=["year", "school_id", "count", "num_found", "status"],
        )
        writer.writeheader()
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--years", nargs="+", type=int, default=[2025, 2026])
    parser.add_argument("--raw-dir", type=Path, default=RAW_DIR)
    parser.add_argument("--processed-dir", type=Path, default=PROCESSED_DIR)
    parser.add_argument("--size", type=int, default=500)
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--retries", type=int, default=2)
    parser.add_argument("--timeout", type=int, default=15)
    parser.add_argument("--min-interval", type=float, default=6.0)
    parser.add_argument("--max-interval", type=float, default=12.0)
    parser.add_argument("--rate-limit-sleep", type=float, default=60.0)
    parser.add_argument("--progress-every", type=int, default=100)
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--limit", type=int, default=0, help="debug: only crawl N schools")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    crawl_time = datetime.now().isoformat(timespec="seconds")
    schools = load_schools(args.raw_dir / "school_name.json")
    if args.limit:
        schools = schools[: args.limit]

    tasks = []
    for year in args.years:
        for school in schools:
            tasks.append((school, year))

    summary_rows: list[dict[str, str]] = []
    done = 0
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        future_map = {
            executor.submit(
                fetch_plan,
                school,
                year,
                args.raw_dir,
                args.size,
                args.overwrite,
                args.retries,
                args.timeout,
                args.min_interval,
                args.max_interval,
                args.rate_limit_sleep,
            ): (school, year)
            for school, year in tasks
        }
        for future in as_completed(future_map):
            school, year = future_map[future]
            school_id, count, num_found, status = future.result()
            summary_rows.append(
                {
                    "year": str(year),
                    "school_id": str(school_id),
                    "count": str(count),
                    "num_found": str(num_found),
                    "status": status,
                }
            )
            done += 1
            if done % args.progress_every == 0:
                nonempty = sum(1 for r in summary_rows if int(r["count"]) > 0)
                print(f"{done}/{len(tasks)} checked, nonempty={nonempty}")

    summary_path = args.processed_dir / "eol_sd_plan_crawl_summary.csv"
    write_summary(summary_path, summary_rows)

    out_path = args.processed_dir / "eol_sd_enrollment_plan_2025_2026.csv"
    total = write_csv(schools, args.years, args.raw_dir, out_path, crawl_time, args.size)

    nonempty = sum(1 for r in summary_rows if int(r["count"]) > 0)
    print(f"Schools checked: {len(tasks)}")
    print(f"Nonempty school-year responses: {nonempty}")
    print(f"Rows exported: {total}")
    print(f"CSV: {out_path}")
    print(f"Summary: {summary_path}")
    print(f"Raw cache: {args.raw_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
