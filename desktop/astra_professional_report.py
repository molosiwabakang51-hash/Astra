"""Self-contained printable HTML and spreadsheet-safe display-table exports.

Render the restricted Markdown emitted by Astra's deterministic reporters.
No raw HTML, URL resolution, scripts or external assets. Charts derive from
structured results; they are descriptive illustrations, not new calculations.
"""
import csv
from decimal import Decimal, InvalidOperation, localcontext
from html import escape
import io
import re

MAX_MARKDOWN = 8 * 1024 * 1024
COLORS = ("#087e83", "#486cb4", "#a45d14", "#7849a5")
SEPARATOR = re.compile(r":?-{3,}:?\Z")
PLAIN_NUMBER = re.compile(r"[+-]?[0-9]+(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?%?\Z")


def cells(line):
    return [value.strip() for value in line.strip()[1:-1].split("|")]


def blocks(markdown):
    """Parse only the deterministic reporters' subset, never embedded HTML."""
    if not isinstance(markdown, str) or len(markdown.encode("utf-8")) > MAX_MARKDOWN:
        raise ValueError("Report text outside supported size.")
    lines = markdown.splitlines()
    result = []
    i = 0
    while i < len(lines):
        line = lines[i].strip()
        if not line:
            i += 1
            continue
        heading = re.fullmatch(r"(#{1,3})\s+(.+)", line)
        if heading:
            result.append(("heading", (len(heading[1]), heading[2])))
            i += 1
            continue
        if line.startswith("|") and line.endswith("|") and i+1<len(lines):
            next_line = lines[i+1].strip()
            if next_line.startswith("|") and next_line.endswith("|"):
                header, divider = cells(line), cells(next_line)
                if len(header)==len(divider) and all(SEPARATOR.fullmatch(x) for x in divider):
                    rows=[]
                    i+=2
                    while i<len(lines):
                        row=lines[i].strip()
                        if not row.startswith("|") or not row.endswith("|"):
                            break
                        values=cells(row)
                        if len(values)!=len(header):
                            raise ValueError("Malformed report table.")
                        rows.append(values)
                        i+=1
                    result.append(("table", (header, rows)))
                    continue
        if line.startswith("- "):
            items=[]
            while i<len(lines) and lines[i].strip().startswith("- "):
                items.append(lines[i].strip()[2:])
                i+=1
            result.append(("list",items))
            continue
        result.append(("paragraph",line))
        i+=1
    return result


def inline(value):
    # Escape before allowing the only supported inline markup: bold.
    return re.sub(r"\*\*([^*\n]+)\*\*",r"<strong>\1</strong>",escape(value,quote=True))


def spreadsheet_cell(value):
    # Preserve genuine signed numbers; prevent spreadsheet formula execution
    # from unexpected text, including leading whitespace/control characters.
    if value.lstrip().startswith(("=","+","-","@")) and not PLAIN_NUMBER.fullmatch(value):
        return "'"+value
    if value.startswith(("\t","\r","\n")):
        return "'"+value
    return value


def tables(markdown):
    section="Report"
    found=[]
    for kind,data in blocks(markdown):
        if kind=="heading":
            section=data[1]
        elif kind=="table":
            found.append((section,*data))
    return found


def table_csv(markdown,index):
    found=tables(markdown)
    if type(index) is not int or not 1<=index<=len(found):
        raise ValueError("Select a valid one-based table index.")
    _,header,rows=found[index-1]
    stream=io.StringIO(newline="")
    writer=csv.writer(stream,lineterminator="\r\n")
    for row in (header,*rows):
        writer.writerow([spreadsheet_cell(x) for x in row])
    # UTF-8 BOM assists desktop Excel with non-ASCII report labels.
    return "\ufeff"+stream.getvalue()


def numeric(value, multiplier=1):
    if isinstance(value,dict):
        value=value["decimal_approximation"]
    if not isinstance(value,(str,int)) or isinstance(value,bool):
        raise ValueError("Expected numerical report value.")
    with localcontext() as ctx:
        ctx.prec=50
        result=Decimal(value)*multiplier
    if not result.is_finite() or abs(result)>Decimal("1e100"):
        raise ValueError("Chart value outside plotting range.")
    return result


def label(value):
    if value and (abs(value)>=Decimal("1e7") or abs(value)<Decimal("0.001")):
        return f"{value:.3E}"
    return f"{value:,.3f}".rstrip("0").rstrip(".")


def sample(rows,limit=120):
    if len(rows)<=limit:
        return rows
    return [rows[i*(len(rows)-1)//(limit-1)] for i in range(limit)]


def plot(title,x_label,y_label,series,*,connect=True):
    """Inline SVG with finite bounded coordinates and accessible chart title."""
    if not series or not any(points for _,points in series):
        return ""
    points=[(x,y) for _,group in series for x,y in group]
    xmin,xmax=min(x for x,_ in points),max(x for x,_ in points)
    ymin,ymax=min(y for _,y in points),max(y for _,y in points)
    if xmin==xmax:
        xmin-=1
        xmax+=1
    if ymin==ymax:
        pad=max(abs(ymin)*Decimal("0.05"),Decimal("0.001"))
        ymin-=pad
        ymax+=pad
    with localcontext() as ctx:
        ctx.prec=50
        sx=lambda x: 83+float((x-xmin)/(xmax-xmin))*575
        sy=lambda y: 252-float((y-ymin)/(ymax-ymin))*216
        out=[f'<figure><figcaption>{escape(title)}</figcaption>',
             f'<svg viewBox="0 0 700 300" role="img" aria-label="{escape(title,quote=True)}" xmlns="http://www.w3.org/2000/svg">',
             f'<title>{escape(title)}</title>']
        for k in range(5):
            x=xmin+(xmax-xmin)*Decimal(k)/4
            y=ymin+(ymax-ymin)*Decimal(k)/4
            px,py=sx(x),sy(y)
            out.append(f'<path d="M83 {py:.3f} H658 M{px:.3f} 36 V252" stroke="#dbe3ed" fill="none"/>')
            out.append(f'<text x="75" y="{py+4:.3f}" text-anchor="end">{escape(label(y))}</text>')
            out.append(f'<text x="{px:.3f}" y="269" text-anchor="middle">{escape(label(x))}</text>')
        out.append('<path d="M83 36 V252 H658" stroke="#506177" fill="none"/>')
        out.append(f'<text x="370" y="289" text-anchor="middle">{escape(x_label)}</text>')
        out.append(f'<text x="16" y="144" transform="rotate(-90 16 144)" text-anchor="middle">{escape(y_label)}</text>')
        for i,(_,group) in enumerate(series):
            color=COLORS[i%len(COLORS)]
            coords=[(sx(x),sy(y)) for x,y in group]
            if connect and len(coords)>1:
                joined=" ".join(f"{x:.3f},{y:.3f}" for x,y in coords)
                out.append(f'<polyline points="{joined}" fill="none" stroke="{color}" stroke-width="2.5"/>')
            if not connect or len(coords)<25:
                for x,y in coords:
                    out.append(f'<circle cx="{x:.3f}" cy="{y:.3f}" r="3" fill="{color}"/>')
        out.append("</svg><div class=\"legend\">")
        for i,(name,_) in enumerate(series):
            out.append(f'<span><i style="background:{COLORS[i%len(COLORS)]}"></i>{escape(name)}</span>')
        out.append("</div></figure>")
    return "".join(out)


def allocation_chart(result):
    values = [(row["symbol"],numeric(row["closing_weight"],100))
              for row in result["positions"] if row["closing_weight"] is not None]
    if not values:
        return ""
    if any(not 0<=v<=100 for _,v in values):
        raise ValueError("Invalid allocation weight.")
    values.sort(key=lambda row:(-row[1],row[0]))
    if len(values)>10:
        values=values[:10]+[("Other holdings",sum(v for _,v in values[10:]))]
    out=['<figure><figcaption>Closing market-value allocation</figcaption>',
         '<div class="allocation-bars">']
    for name,value in values:
        out.append(f'<div class="bar-row"><span>{escape(name)}</span><div class="bar-track"><div class="bar-fill" style="width:{float(value):.6f}%"></div></div><span>{escape(label(value))}%</span></div>')
    out.append("</div></figure>")
    return "".join(out)


def charts(mode,result):
    """Use only declared result fields, never infer chart data from prose."""
    try:
        if mode=="portfolio":
            return allocation_chart(result)
        if mode=="curve":
            points=[(numeric(r["months"]),numeric(r["continuous_zero"],100)) for r in result["nodes"]]
            return plot("Continuous zero curve","Maturity (months)","Annual spot rate (%)",[("Supplied curve nodes",points)])
        if mode=="optimization":
            points=[(numeric(r["volatility"],100),numeric(r["expected_return"],100))
                    for r in result["efficient_frontier"]]
            return plot("Nondominated allocation grid","Annual volatility (%)","Expected annual return (%)",
                        [("Reported efficient allocations",points)],connect=False)
        if mode=="bond":
            rows=sorted(result["scenarios"],key=lambda r:r["shock_bps"])
            points=[(numeric(r["shock_bps"]),numeric(r["exact_price_change"],100)) for r in rows]
            estimate=[(numeric(r["shock_bps"]),numeric(r["duration_convexity_estimate"],100)) for r in rows]
            return plot("Bond yield sensitivity","Yield shock (bp)","Price change (%)",
                        [("Modeled repricing",points),("Duration-convexity estimate",estimate)])
        if mode=="backtest":
            series=[]
            for s in result["strategies"]:
                rows=sample(s["series"])
                points=[(Decimal(0),numeric(result["initial_wealth"]))]+[
                    (numeric(r["period"]),numeric(r["closing_wealth"])) for r in rows]
                series.append((s["name"],points))
            return plot("Historical strategy wealth (sampled display)","Observation","Wealth ("+result["currency"]+")",series)
    except (KeyError,TypeError,ValueError,InvalidOperation,ArithmeticError):
        # Do not invent substitutes for missing or unplottable values.
        return '<p class="notice">Chart unavailable for this result. Consult the complete numerical tables.</p>'
    return ""


CSS = """
:root{color-scheme:light;--ink:#182b42;--muted:#52657a;--accent:#087e83}
*{box-sizing:border-box}body{margin:0;background:#edf2f6;color:var(--ink);
font:14px/1.55 "Segoe UI",Arial,sans-serif}main{max-width:1120px;margin:32px auto;
padding:44px 54px;background:white;box-shadow:0 2px 18px #10243b15}
.mast{border-bottom:3px solid var(--accent);padding:0 0 15px;margin-bottom:24px;
display:flex;justify-content:space-between;align-items:baseline;gap:12px}
.brand{font-size:23px;font-weight:750;letter-spacing:3px;color:var(--accent)}
.tag{font-size:11px;text-transform:uppercase;letter-spacing:1px;color:var(--muted)}
h1{font-size:28px;line-height:1.2;margin:0 0 22px}
h2{font-size:19px;margin:26px 0 12px;border-bottom:1px solid #dbe3ed;padding-bottom:5px}
h3{font-size:15px;margin:18px 0 8px}p{margin:8px 0;overflow-wrap:anywhere}
ul{padding-left:22px}li{margin:4px 0;overflow-wrap:anywhere}
.table-wrap{overflow-x:auto;margin:12px 0 18px}
table{border-collapse:collapse;width:100%;font-size:12px;line-height:1.4;font-variant-numeric:tabular-nums}
th,td{padding:8px 9px;border-bottom:1px solid #dbe3ed;text-align:right;vertical-align:top;overflow-wrap:anywhere}
th{background:#e7eff4;color:#203951;font-weight:650}td:first-child,th:first-child{text-align:left}
tbody tr:nth-child(even){background:#f6f8fa}
figure{border:1px solid #dbe3ed;margin:20px 0;padding:14px 16px;break-inside:avoid}
figcaption{font-weight:650;font-size:15px;margin-bottom:8px}svg{display:block;width:100%;height:auto}
svg text{font:11px Arial,sans-serif;fill:#42566d}.legend{display:flex;flex-wrap:wrap;gap:14px;font-size:12px}
.legend i{display:inline-block;width:10px;height:10px;margin-right:6px}.notice{color:var(--muted)}
.bar-row{display:grid;grid-template-columns:150px 1fr 80px;gap:12px;align-items:center;margin:6px 0}
.bar-row span{overflow-wrap:anywhere}.bar-row span:last-child{text-align:right;font-variant-numeric:tabular-nums}
.bar-track{height:12px;background:#e7eff4}.bar-fill{background:var(--accent);height:100%}
footer{border-top:1px solid #dbe3ed;margin-top:30px;padding-top:10px;color:var(--muted);font-size:11px}
@page{size:A4 landscape;margin:13mm}
@media print{body{background:white;font-size:10pt}main{max-width:none;margin:0;padding:0;box-shadow:none}
h1,h2,h3,figcaption{break-after:avoid}thead{display:table-header-group}
tr{break-inside:avoid}table{font-size:8.5pt;table-layout:fixed}.table-wrap{overflow:visible}
th,td{padding:5px;overflow-wrap:anywhere}figure{max-width:190mm}
*{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
"""


def render_html(markdown,mode,result):
    parsed=blocks(markdown)
    title=next((data[1] for kind,data in parsed if kind=="heading" and data[0]==1),"Astra analytical report")
    visual=charts(mode,result)
    out=["<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">",
         '<meta name="viewport" content="width=device-width, initial-scale=1">',
         '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; base-uri \'none\'; form-action \'none\'">',
         f"<title>{escape(title)}</title><style>{CSS}</style></head><body><main>",
         '<div class="mast"><span class="brand">ASTRA</span><span class="tag">Financial analytics · Local report</span></div>']
    inserted=False
    for kind,data in parsed:
        if kind=="heading":
            level,value=data
            if level==2 and visual and not inserted:
                out.append(visual)
                inserted=True
            out.append(f"<h{level}>{inline(value)}</h{level}>")
        elif kind=="paragraph":
            out.append("<p>"+inline(data)+"</p>")
        elif kind=="list":
            out.append("<ul>"+"".join("<li>"+inline(v)+"</li>" for v in data)+"</ul>")
        else:
            header,rows=data
            out.append('<div class="table-wrap"><table><thead><tr>'+''.join(
                '<th scope="col">'+inline(v)+'</th>' for v in header)+'</tr></thead><tbody>')
            for row in rows:
                out.append("<tr>"+''.join("<td>"+inline(v)+"</td>" for v in row)+"</tr>")
            out.append("</tbody></table></div>")
    if visual and not inserted:
        out.append(visual)
    out.append("<footer>Generated locally from supplied analytical inputs. Review assumptions, limitations and source traceability before use.</footer></main></body></html>")
    return "\n".join(out)+"\n"
