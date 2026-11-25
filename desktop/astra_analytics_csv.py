"""Bounded spreadsheet CSV ingestion for Astra's offline analytical engines.

No formulas, implicit conversions, inferred dates, missing-value imputation
or column-name guessing. The analyst explicitly supplies conventions.
"""
import csv
from datetime import date
import hashlib
import io
from pathlib import Path
import re

from astra_quantitative_verify import number

MAX_BYTES = 524288
MAX_ROWS = 2520
MAX_FIELD = 100
NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9 ._-]{0,39}\Z")
HOLDINGS = ("symbol","opening_value","closing_value","cash_income")
EVENTS = ("date","value_before_flow","external_flow")
RETURN_MODES = ("historical-risk","simulation","backtest")


def read_csv(path):
    with Path(path).open("rb") as source:
        raw = source.read(MAX_BYTES + 1)
    if len(raw)>MAX_BYTES:
        raise ValueError("CSV exceeds 512 KiB.")
    data=raw.decode("utf-8-sig",errors="strict")
    if "\x00" in data:
        raise ValueError("CSV contains invalid characters.")
    reader=csv.reader(io.StringIO(data,newline=""),strict=True)
    try:
        header=next(reader,None)
        if not header or len(header)>8 or len(set(header))!=len(header) or any(
                not x or len(x)>MAX_FIELD or x!=x.strip() for x in header):
            raise ValueError("Invalid or duplicate CSV headers.")
        rows=[]
        for row in reader:
            if len(rows)>=MAX_ROWS:
                raise ValueError("CSV exceeds 2520 rows.")
            if len(row)!=len(header) or any(len(x)>MAX_FIELD or not x or x!=x.strip() for x in row):
                raise ValueError("Missing, malformed or padded CSV cell at row "+str(reader.line_num)+".")
            rows.append(row)
    except csv.Error:
        raise ValueError("Malformed CSV.") from None
    return header,rows,hashlib.sha256(raw).hexdigest()


def iso(value):
    if not re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}",value):
        raise ValueError("Dates must use YYYY-MM-DD.")
    return date.fromisoformat(value)


def chronological(rows):
    dates=[iso(row[0]) for row in rows]
    if any(a>=b for a,b in zip(dates,dates[1:])):
        raise ValueError("Dates must be strictly increasing, with no duplicates.")
    return dates


def ingest(mode,path,metadata):
    if type(metadata) is not dict:
        raise ValueError("Metadata must be a JSON object.")
    request=dict(metadata)
    header,rows,digest=read_csv(path)
    receipt={"source_sha256":digest,"rows":len(rows),"format":"utf-8 comma-separated CSV"}
    if mode=="portfolio":
        if tuple(header)!=HOLDINGS or "holdings" in request or not 1<=len(rows)<=50:
            raise ValueError("Portfolio CSV requires the four exact holdings columns and 1–50 rows.")
        for row in rows:
            if not NAME.fullmatch(row[0]):
                raise ValueError("Invalid holding identifier.")
            for x in row[1:]:
                number(x)
        request["holdings"]=[dict(zip(header,row)) for row in rows]
    elif mode=="cashflow":
        if tuple(header)!=EVENTS or "events" in request or len(rows)>60:
            raise ValueError("Cash-flow CSV requires the three exact event columns and at most 60 rows.")
        dates=chronological(rows)
        for row in rows:
            for x in row[1:]:
                number(x)
        request["events"]=[dict(zip(header,row)) for row in rows]
        if dates:
            receipt["first_date"]=dates[0].isoformat()
            receipt["last_date"]=dates[-1].isoformat()
    elif mode in RETURN_MODES:
        assets=request.get("assets")
        if type(assets) is not list or not 1<=len(assets)<=6 or any(
                not isinstance(a,str) or not NAME.fullmatch(a) for a in assets):
            raise ValueError("Explicit asset identifiers required.")
        if len({a.casefold() for a in assets})!=len(assets) or any(
                a.casefold() in ("date","benchmark") for a in assets):
            raise ValueError("Asset identifiers must be unique and not reserved headers.")
        if "returns" in request or "benchmark_returns" in request:
            raise ValueError("CSV cannot replace existing return observations.")
        expected=["date",*assets]
        has_benchmark=header==expected+["benchmark"]
        if header!=expected and not has_benchmark:
            raise ValueError("Headers must match date, ordered asset names and optional benchmark.")
        if has_benchmark and mode=="simulation":
            raise ValueError("Simulation does not consume benchmark observations.")
        if len(rows)<2:
            raise ValueError("At least two chronological observations required.")
        dates=chronological(rows)
        for row in rows:
            for value in row[1:]:
                if not -1<=number(value)<=10:
                    raise ValueError("Return outside -100% to +1000%.")
        request["returns"]=[row[1:1+len(assets)] for row in rows]
        if has_benchmark:
            request["benchmark_returns"]=[row[-1] for row in rows]
        receipt.update(first_date=dates[0].isoformat(),last_date=dates[-1].isoformat(),
                       alignment="same-row values; observation frequency and completeness not independently verified")
    else:
        raise ValueError("This analysis does not support CSV ingestion.")
    return request,receipt
