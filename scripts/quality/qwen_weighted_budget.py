"""Pin actual trained artifacts and compare a projected short trajectory to its archived generation."""
import json
import hashlib
from pathlib import Path

TRAINING_SHA="5728756f65f8ce31876b89a3605d9498e33b3312ace1b209d825c5735923372c"
REPORT_SHA="d8523bafe653a0b0de5e2eb35062e395e8db0b691db28b33fed7e89604510a15"
PINS={
    "ordinary":("d8268cef593e7edd9b3959dffd77f0939258c18dc63c16d2424c078c5d74a487","d223d83e6422b68201e5bfe789b67203909135fe242e1ec7aeb3a7ee4b86654f"),
    "weighted":("d9631b257db27f3f56564a6ba5c0187ce368c7fc60459bd298fa0caef8d0a264","2ccebf9e2b3c14b5f66898313ce0b2709e015fe82610660c6bbfe146ff5a81e7"),
}
SHORT=128
LONG=256

def pinned_json(path,expected,limit=2*1024*1024):
    path=Path(path)
    if not path.is_file() or not 0<path.stat().st_size<=limit:
        raise ValueError("Missing or oversized report")
    raw=path.read_bytes()
    if hashlib.sha256(raw).hexdigest()!=expected:
        raise ValueError("Artifact digest mismatch")
    return json.loads(raw)

def projected_short(tokens,done,eos):
    if not tokens or len(tokens)>LONG or type(done) is not bool or (not done and len(tokens)!=LONG):
        raise ValueError("Invalid long trajectory")
    if done!=(tokens[-1]==eos) or eos in tokens[:-1]:
        raise ValueError("Invalid EOS boundary")
    return tokens[:SHORT],done and len(tokens)<=SHORT

def compare_archive(projected,archived):
    fields=("ident","domain","prompt_sha256","expected")
    keys=lambda r:tuple(r[f] for f in fields)
    a={keys(r):r for r in projected}
    b={keys(r):r for r in archived}
    if not a or len(a)!=len(projected) or len(b)!=len(archived) or set(a)!=set(b):
        raise ValueError("Archive identity mismatch")
    metrics=("complete","correct","terminal_format","tokens","output_sha256")
    matches=[all(a[k][f]==b[k][f] for f in metrics) for k in a]
    return dict(attempted=len(matches),output_and_metrics_reproduced=sum(matches),
                all_reproduced=all(matches))
