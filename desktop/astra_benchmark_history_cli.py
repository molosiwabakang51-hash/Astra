"""Explicit local benchmark report import, inspection and confirmed clearing."""
import argparse
import json
from pathlib import Path
import sys

from astra_benchmark_history import BenchmarkHistory, identity, observations
from astra_verified_finance import read_request


def main(argv=None):
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode",choices=("record","inspect","clear"))
    parser.add_argument("--database",required=True)
    parser.add_argument("--report")
    parser.add_argument("--primary-sha")
    parser.add_argument("--critic-sha")
    parser.add_argument("--configuration-sha")
    parser.add_argument("--approve-storage",action="store_true")
    parser.add_argument("--confirm-clear",action="store_true")
    args=parser.parse_args(argv)
    path=Path(args.database)
    try:
        ids=(args.primary_sha,args.critic_sha,args.configuration_sha)
        if args.mode=="record":
            if not args.approve_storage or not args.report or args.confirm_clear:
                raise ValueError("Report import requires explicit storage approval.")
            identity(*ids)
            report=read_request(args.report)
            observations(report)
            with BenchmarkHistory(path) as history:
                result=history.record(report,*ids,approved=True)
        elif args.mode=="inspect":
            if not path.is_file() or args.report or args.approve_storage or args.confirm_clear:
                raise ValueError("Inspection requires an existing history database.")
            identity(*ids)
            with BenchmarkHistory(path) as history:
                result=history.report(*ids)
        else:
            if not path.is_file() or not args.confirm_clear or args.report or args.approve_storage or any(ids):
                raise ValueError("Clearing requires explicit confirmation and an existing history database.")
            with BenchmarkHistory(path) as history:
                history.clear(confirmed=True)
            result={"status":"cleared"}
        print(json.dumps(result,indent=2,allow_nan=False))
        return 0
    except (OSError,ValueError,RuntimeError,TypeError,RecursionError):
        print("Invalid or unavailable benchmark history request.",file=sys.stderr)
        return 1


if __name__=="__main__":
    sys.exit(main())
