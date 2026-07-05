#!/usr/bin/env python3
"""Crawl public SDZK enrollment-plan notices and attachments.

The Shandong Education Admission Examination Authority publishes plan-related
files in the "夏季高考 / 院校计划" column. This script downloads official
notice pages and their attachments for selected years, then extracts tabular
content from Excel and DOCX files into auditable CSV files.
"""

from __future__ import annotations

import argparse
import csv
import os
import re
import sys
import time
import zipfile
from dataclasses import dataclass
from html.parser import HTMLParser
from pathlib import Path
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urljoin, urlparse
from urllib.request import Request, urlopen
from xml.etree import ElementTree as ET

import pandas as pd


BASE_URL = "https://www.sdzk.cn/"
LIST_URL = urljoin(BASE_URL, "NewsList.aspx?BCID=1198&CID=46")
RAW_DIR = Path("data/raw/sdzk_plans")
PROCESSED_DIR = Path("data/processed")
USER_AGENT = "Mozilla/5.0 (compatible; gaokao-plan-crawler/1.0)"
ATTACHMENT_EXTS = {".xls", ".xlsx", ".doc", ".docx", ".pdf", ".zip", ".rar"}


@dataclass(frozen=True)
class Notice:
    news_id: str
    title: str
    date: str
    url: str


@dataclass(frozen=True)
class Attachment:
    year: int
    news_id: str
    notice_title: str
    notice_date: str
    notice_url: str
    file_url: str
    title: str
    local_path: Path


class NoticeListParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.notices: list[Notice] = []
        self._in_link = False
        self._current_href = ""
        self._current_title = ""
        self._current_date = ""
        self._capture_span = False
        self._capture_i = False
        self._span_parts: list[str] = []
        self._i_parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attr = dict(attrs)
        if tag == "a" and attr.get("href", "").startswith("NewsInfo.aspx?NewsID="):
            self._in_link = True
            self._current_href = attr.get("href", "")
            self._current_title = attr.get("title", "")
            self._current_date = ""
            self._span_parts = []
            self._i_parts = []
        elif self._in_link and tag == "span":
            self._capture_span = True
        elif self._in_link and tag == "i":
            self._capture_i = True

    def handle_endtag(self, tag: str) -> None:
        if self._in_link and tag == "span":
            self._capture_span = False
        elif self._in_link and tag == "i":
            self._capture_i = False
        elif self._in_link and tag == "a":
            title = clean_text(self._current_title or "".join(self._span_parts))
            date = clean_text(self._current_date or "".join(self._i_parts))
            news_id = parse_news_id(self._current_href)
            if news_id and title:
                self.notices.append(
                    Notice(
                        news_id=news_id,
                        title=title,
                        date=date,
                        url=urljoin(BASE_URL, self._current_href),
                    )
                )
            self._in_link = False

    def handle_data(self, data: str) -> None:
        if self._capture_span:
            self._span_parts.append(data)
        elif self._capture_i:
            self._i_parts.append(data)
            self._current_date = "".join(self._i_parts)


class AttachmentParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.links: list[tuple[str, str]] = []
        self._href: str | None = None
        self._title = ""
        self._parts: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attr = dict(attrs)
        if tag == "a":
            href = attr.get("href", "")
            suffix = Path(urlparse(href).path).suffix.lower()
            if "/Floadup/file/" in href or suffix in ATTACHMENT_EXTS:
                self._href = href
                self._title = attr.get("title", "")
                self._parts = []

    def handle_endtag(self, tag: str) -> None:
        if tag == "a" and self._href:
            title = clean_text(self._title or "".join(self._parts))
            self.links.append((self._href, title))
            self._href = None

    def handle_data(self, data: str) -> None:
        if self._href:
            self._parts.append(data)


def clean_text(value: str) -> str:
    return re.sub(r"\s+", " ", value or "").strip()


def parse_news_id(href: str) -> str:
    match = re.search(r"NewsID=(\d+)", href)
    return match.group(1) if match else ""


def request_url(url: str, timeout: int = 30) -> bytes:
    req = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(req, timeout=timeout) as resp:
        return resp.read()


def fetch_text(url: str) -> str:
    raw = request_url(url)
    return raw.decode("utf-8", errors="replace")


def safe_filename(name: str, fallback: str) -> str:
    name = clean_text(name) or fallback
    name = re.sub(r'[\\/:*?"<>|]+', "_", name)
    name = name.strip(" .")
    return name[:180] or fallback


def find_notices(years: set[int]) -> list[Notice]:
    html = fetch_text(LIST_URL)
    parser = NoticeListParser()
    parser.feed(html)
    result = []
    for notice in parser.notices:
        year_match = re.search(r"(20\d{2})", notice.title + " " + notice.date)
        if year_match and int(year_match.group(1)) in years:
            result.append(notice)
    return result


def find_attachments(notice: Notice, raw_root: Path) -> list[Attachment]:
    html = fetch_text(notice.url)
    parser = AttachmentParser()
    parser.feed(html)
    year_match = re.search(r"(20\d{2})", notice.title + " " + notice.date)
    year = int(year_match.group(1)) if year_match else int(notice.date[:4])

    attachments = []
    for idx, (href, title) in enumerate(parser.links, start=1):
        file_url = urljoin(BASE_URL, href)
        suffix = Path(urlparse(file_url).path).suffix
        if not suffix:
            suffix = Path(title).suffix or ".bin"
        filename = safe_filename(title, f"{notice.news_id}_{idx}{suffix}")
        if not Path(filename).suffix and suffix:
            filename = f"{filename}{suffix}"
        local_dir = raw_root / str(year) / notice.news_id
        attachments.append(
            Attachment(
                year=year,
                news_id=notice.news_id,
                notice_title=notice.title,
                notice_date=notice.date,
                notice_url=notice.url,
                file_url=file_url,
                title=title or filename,
                local_path=local_dir / filename,
            )
        )
    return attachments


def download_attachment(att: Attachment, overwrite: bool = False) -> None:
    att.local_path.parent.mkdir(parents=True, exist_ok=True)
    if att.local_path.exists() and not overwrite:
        return
    data = request_url(att.file_url)
    att.local_path.write_bytes(data)


def write_manifest(notices: list[Notice], attachments: list[Attachment], out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "year",
                "news_id",
                "notice_date",
                "notice_title",
                "notice_url",
                "attachment_title",
                "file_url",
                "local_path",
            ],
        )
        writer.writeheader()
        for att in attachments:
            writer.writerow(
                {
                    "year": att.year,
                    "news_id": att.news_id,
                    "notice_date": att.notice_date,
                    "notice_title": att.notice_title,
                    "notice_url": att.notice_url,
                    "attachment_title": att.title,
                    "file_url": att.file_url,
                    "local_path": str(att.local_path),
                }
            )


def extract_excel(att: Attachment) -> list[dict[str, str]]:
    try:
        sheets = pd.read_excel(att.local_path, sheet_name=None, header=None, dtype=str)
    except Exception as exc:
        return [
            base_row(att)
            | {"sheet": "", "row_index": "", "col_index": "", "cell_value": f"READ_ERROR: {exc}"}
        ]

    rows: list[dict[str, str]] = []
    for sheet_name, df in sheets.items():
        df = df.fillna("")
        for row_idx, row in df.iterrows():
            values = [clean_text(str(v)) for v in row.tolist()]
            if not any(values):
                continue
            rows.append(
                base_row(att)
                | {
                    "sheet": str(sheet_name),
                    "row_index": str(row_idx + 1),
                    "col_index": "",
                    "cell_value": " | ".join(values),
                }
            )
    return rows


def extract_docx(att: Attachment) -> list[dict[str, str]]:
    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    rows: list[dict[str, str]] = []
    try:
        with zipfile.ZipFile(att.local_path) as zf:
            xml = zf.read("word/document.xml")
    except Exception as exc:
        return [
            base_row(att)
            | {"sheet": "", "row_index": "", "col_index": "", "cell_value": f"READ_ERROR: {exc}"}
        ]

    root = ET.fromstring(xml)
    table_no = 0
    for tbl in root.findall(".//w:tbl", ns):
        table_no += 1
        for row_idx, tr in enumerate(tbl.findall("./w:tr", ns), start=1):
            values = []
            for tc in tr.findall("./w:tc", ns):
                texts = [node.text or "" for node in tc.findall(".//w:t", ns)]
                values.append(clean_text("".join(texts)))
            if any(values):
                rows.append(
                    base_row(att)
                    | {
                        "sheet": f"table_{table_no}",
                        "row_index": str(row_idx),
                        "col_index": "",
                        "cell_value": " | ".join(values),
                    }
                )
    return rows


def docx_tables(path: Path) -> list[list[list[str]]]:
    ns = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}
    with zipfile.ZipFile(path) as zf:
        xml = zf.read("word/document.xml")
    root = ET.fromstring(xml)
    tables: list[list[list[str]]] = []
    for tbl in root.findall(".//w:tbl", ns):
        table: list[list[str]] = []
        for tr in tbl.findall("./w:tr", ns):
            row: list[str] = []
            for tc in tr.findall("./w:tc", ns):
                texts = [node.text or "" for node in tc.findall(".//w:t", ns)]
                row.append(clean_text("".join(texts)))
            if any(row):
                table.append(row)
        if table:
            tables.append(table)
    return tables


def classify_notice(title: str) -> tuple[str, str]:
    category = "普通类"
    if "艺术" in title:
        category = "艺术类"
    elif "体育" in title:
        category = "体育类"
    elif "春季" in title:
        category = "春季高考"

    batch = ""
    for key in [
        "注册入学",
        "普通类常规批第3次志愿",
        "普通类常规批第2次志愿",
        "艺术类专科批第2次志愿",
        "艺术类本科批第2次志愿",
        "体育类常规批第3次志愿",
        "体育类常规批第2次志愿",
        "普通类提前批第2次志愿",
        "专科层次分专业招生计划补充信息",
        "分专业招生计划补充信息",
    ]:
        if key in title:
            batch = key
            break
    return category, batch


def get_int(value: str) -> str:
    value = clean_text(value)
    if not value:
        return ""
    match = re.search(r"\d+", value)
    return match.group(0) if match else ""


def split_major(value: str) -> tuple[str, str]:
    value = clean_text(value)
    match = re.match(r"^([0-9A-Za-z]{2})\s+(.+)$", value)
    if not match:
        return "", value
    return match.group(1), clean_text(match.group(2))


def header_map(values: list[str]) -> dict[str, int]:
    mapping: dict[str, int] = {}
    for idx, value in enumerate(values):
        if "院校代号" in value:
            mapping["school_code"] = idx
        elif "院校、专业" in value:
            mapping["name"] = idx
        elif "考试科目要求" in value:
            mapping["subject_requirement"] = idx
        elif "专业类别" in value:
            mapping["major_category"] = idx
        elif "学制" in value:
            mapping["duration"] = idx
        elif "计划数" in value:
            mapping["plan_count"] = idx
        elif "年收费" in value:
            mapping["tuition"] = idx
    return mapping


def value_at(values: list[str], idx: int | None) -> str:
    if idx is None or idx >= len(values):
        return ""
    return clean_text(values[idx])


def base_plan_row(att: Attachment, row_type: str) -> dict[str, str]:
    category, batch = classify_notice(att.notice_title)
    return {
        "year": str(att.year),
        "news_id": att.news_id,
        "notice_date": att.notice_date,
        "notice_title": att.notice_title,
        "notice_url": att.notice_url,
        "attachment_title": att.title,
        "file_url": att.file_url,
        "local_path": str(att.local_path),
        "row_type": row_type,
        "category": category,
        "batch": batch,
        "school_code": "",
        "school_name": "",
        "education_level": "",
        "major_code": "",
        "major_name": "",
        "subject_requirement": "",
        "major_category": "",
        "duration": "",
        "plan_count": "",
        "tuition": "",
        "remark": "",
        "raw_row": "",
    }


def normalize_excel_plan_rows(att: Attachment) -> list[dict[str, str]]:
    try:
        sheets = pd.read_excel(att.local_path, sheet_name=None, header=None, dtype=str)
    except Exception as exc:
        row = base_plan_row(att, "read_error")
        row["remark"] = str(exc)
        return [row]

    result: list[dict[str, str]] = []
    for _, df in sheets.items():
        df = df.fillna("")
        rows = [[clean_text(str(v)) for v in row.tolist()] for _, row in df.iterrows()]
        header_idx = None
        mapping: dict[str, int] = {}
        for idx, values in enumerate(rows):
            if any("院校代号" in v for v in values) and any("院校、专业" in v for v in values):
                header_idx = idx
                mapping = header_map(values)
                break
        if header_idx is None or "school_code" not in mapping or "name" not in mapping:
            continue

        current_school_code = ""
        current_school_name = ""
        for values in rows[header_idx + 1 :]:
            if not any(values):
                continue
            school_code = value_at(values, mapping.get("school_code"))
            name = value_at(values, mapping.get("name"))
            plan_count = get_int(value_at(values, mapping.get("plan_count")))
            raw_row = " | ".join(v for v in values if v)

            if re.match(r"^[A-Z]\d{3}$", school_code) and name and not split_major(name)[0]:
                current_school_code = school_code
                current_school_name = name
                continue

            major_code, major_name = split_major(name)
            if not major_code or not major_name:
                continue

            row = base_plan_row(att, "plan")
            row.update(
                {
                    "school_code": current_school_code,
                    "school_name": current_school_name,
                    "major_code": major_code,
                    "major_name": major_name,
                    "subject_requirement": value_at(values, mapping.get("subject_requirement")),
                    "major_category": value_at(values, mapping.get("major_category")),
                    "duration": get_int(value_at(values, mapping.get("duration"))),
                    "plan_count": plan_count,
                    "tuition": get_int(value_at(values, mapping.get("tuition"))),
                    "raw_row": raw_row,
                }
            )
            result.append(row)
    return result


def normalize_docx_supplement_rows(att: Attachment) -> list[dict[str, str]]:
    try:
        tables = docx_tables(att.local_path)
    except Exception as exc:
        row = base_plan_row(att, "read_error")
        row["remark"] = str(exc)
        return [row]

    result: list[dict[str, str]] = []
    for table in tables:
        if not table:
            continue
        headers = table[0]
        mapping = {clean_text(name): idx for idx, name in enumerate(headers)}
        if "院校代号" not in mapping or "专业名称" not in mapping:
            continue

        last_values = {
            "科类": "",
            "批次": "",
            "院校代号": "",
            "院校名称": "",
            "层次": "",
        }
        for values in table[1:]:
            padded = values + [""] * (len(headers) - len(values))
            for key in list(last_values):
                val = value_at(padded, mapping.get(key))
                if val:
                    last_values[key] = val

            row = base_plan_row(att, "supplement")
            major_code = value_at(padded, mapping.get("专业代号"))
            major_name = value_at(padded, mapping.get("专业名称"))
            remark = value_at(padded, mapping.get("主要内容"))
            if not (major_code or major_name or remark):
                continue
            row.update(
                {
                    "category": last_values["科类"] or row["category"],
                    "batch": last_values["批次"] or row["batch"],
                    "school_code": last_values["院校代号"],
                    "school_name": last_values["院校名称"],
                    "education_level": last_values["层次"],
                    "major_code": major_code,
                    "major_name": major_name,
                    "remark": remark,
                    "raw_row": " | ".join(padded),
                }
            )
            plan_match = re.search(r"计划数调整为[“\"]?(\d+)", remark)
            if plan_match:
                row["plan_count"] = plan_match.group(1)
            tuition_match = re.search(r"(?:年收费|学费).*?[“\"]?(\d+)", remark)
            if tuition_match:
                row["tuition"] = tuition_match.group(1)
            result.append(row)
    return result


def write_normalized_plan_rows(attachments: Iterable[Attachment], out_path: Path) -> int:
    rows: list[dict[str, str]] = []
    for att in attachments:
        if not att.local_path.exists():
            continue
        suffix = att.local_path.suffix.lower()
        if suffix in {".xls", ".xlsx"}:
            rows.extend(normalize_excel_plan_rows(att))
        elif suffix == ".docx":
            rows.extend(normalize_docx_supplement_rows(att))

    fields = list(base_plan_row(Attachment(0, "", "", "", "", "", "", Path("")), "plan").keys())
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    return len(rows)


def base_row(att: Attachment) -> dict[str, str]:
    return {
        "year": str(att.year),
        "news_id": att.news_id,
        "notice_date": att.notice_date,
        "notice_title": att.notice_title,
        "notice_url": att.notice_url,
        "attachment_title": att.title,
        "file_url": att.file_url,
        "local_path": str(att.local_path),
    }


def extract_rows(attachments: Iterable[Attachment]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for att in attachments:
        suffix = att.local_path.suffix.lower()
        if suffix in {".xls", ".xlsx"}:
            rows.extend(extract_excel(att))
        elif suffix == ".docx":
            rows.extend(extract_docx(att))
    return rows


def write_extract(rows: list[dict[str, str]], out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "year",
                "news_id",
                "notice_date",
                "notice_title",
                "notice_url",
                "attachment_title",
                "file_url",
                "local_path",
                "sheet",
                "row_index",
                "col_index",
                "cell_value",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--years", nargs="+", type=int, default=[2025, 2026])
    parser.add_argument("--raw-dir", type=Path, default=RAW_DIR)
    parser.add_argument("--processed-dir", type=Path, default=PROCESSED_DIR)
    parser.add_argument("--overwrite", action="store_true")
    parser.add_argument("--sleep", type=float, default=0.3)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    years = set(args.years)
    notices = find_notices(years)
    if not notices:
        print(f"No notices found for years: {sorted(years)}", file=sys.stderr)
        return 1

    attachments: list[Attachment] = []
    for notice in notices:
        found = find_attachments(notice, args.raw_dir)
        attachments.extend(found)
        print(f"{notice.date} {notice.news_id} {notice.title}: {len(found)} attachment(s)")
        time.sleep(args.sleep)

    for att in attachments:
        print(f"Downloading {att.file_url} -> {att.local_path}")
        try:
            download_attachment(att, overwrite=args.overwrite)
        except (HTTPError, URLError, TimeoutError) as exc:
            print(f"  WARN: download failed: {exc}", file=sys.stderr)
        time.sleep(args.sleep)

    manifest_path = args.processed_dir / "sdzk_plan_official_files.csv"
    write_manifest(notices, attachments, manifest_path)

    extract_path = args.processed_dir / "sdzk_plan_extracted_rows.csv"
    extracted = extract_rows([att for att in attachments if att.local_path.exists()])
    write_extract(extracted, extract_path)

    normalized_path = args.processed_dir / "sdzk_enrollment_plan_rows.csv"
    normalized_count = write_normalized_plan_rows(attachments, normalized_path)

    print(f"Manifest: {manifest_path} ({len(attachments)} attachment rows)")
    print(f"Extracted rows: {extract_path} ({len(extracted)} rows)")
    print(f"Normalized plan rows: {normalized_path} ({normalized_count} rows)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
