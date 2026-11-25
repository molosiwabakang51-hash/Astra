"""Generate professional local reports through the unified analytics registry."""
import argparse
import json
import sys
from pathlib import Path

from astra_professional_report import render_html, tables, table_csv


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode")
    parser.add_argument("input")
    parser.add_argument("--metadata",help="Metadata JSON for CSV input")
    parser.add_argument("--format",choices=("html","csv"),default="html")
    parser.add_argument("--table",type=int,help="One-based report table index for CSV")
    parser.add_argument("--list-tables",action="store_true")
    parser.add_argument("--output",help="New output file; never overwrite")
    args=parser.parse_args(argv)
    try:
        if args.list_tables and (args.table is not None or args.output or args.format!="html"):
            raise ValueError("List tables separately from report exports.")
        if not args.list_tables and ((args.format=="csv")!=(args.table is not None)):
            raise ValueError("CSV export requires a table index; HTML does not.")
        try:
            from astra_analytics import from_files,render
        except ImportError:
            raise RuntimeError("Unified analytics module must be installed.") from None
        envelope,markdown=from_files(args.mode,args.input,metadata_path=args.metadata)
        markdown=render(envelope,markdown,"markdown")
        if args.list_tables:
            print(json.dumps([{"index":i,"section":section,"columns":header,"rows":len(rows)}
                              for i,(section,header,rows) in enumerate(tables(markdown),1)],
                             ensure_ascii=False,indent=2))
            return 0
        output=(render_html(markdown,args.mode,envelope["result"]) if args.format=="html"
                else table_csv(markdown,args.table))
        if args.output:
            with Path(args.output).open("x",encoding="utf-8",newline="") as dest:
                dest.write(output)
        else:
            sys.stdout.write(output)
        return 0
    except (ValueError,OSError,RuntimeError,TypeError,ArithmeticError,RecursionError):
        print("Professional report unavailable; check analytics installation, inputs and export options.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
