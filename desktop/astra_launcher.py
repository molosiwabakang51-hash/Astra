"""One local entry point for Astra's desktop workspaces.

One child workspace at a time, no shell, no automatic model loading.
Separate processes isolate Tk event loops and SQLite lifetimes. Closing a
workspace releases its session state; persistent evidence remains on disk.
"""
import importlib.util
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
WORKSPACES = {
    "assistant": ("astra_desktop_gui.py", "Astra Assistant",
                  "Conversation, contextual finance, reviewed knowledge and local models."),
    "analytics": ("astra_analytics_gui.py", "Financial Analytics",
                  "Import financial inputs, run deterministic analytics and export reports."),
}


def installed(workspace, root=ROOT):
    if workspace not in WORKSPACES:
        raise ValueError("Unknown workspace.")
    path=Path(root)/WORKSPACES[workspace][0]
    return path.is_file() and not path.is_symlink()


class WorkspaceProcess:
    def __init__(self, root=ROOT, *, executable=None, spawn=subprocess.Popen):
        self.root=Path(root).resolve()
        self.executable=executable or sys.executable
        self.spawn=spawn
        self.process=None
        self.name=None

    def active(self):
        return self.process is not None and self.process.poll() is None

    def launch(self, name):
        if self.active():
            raise RuntimeError("Close the active workspace before starting another.")
        if not installed(name,self.root):
            raise ValueError("Workspace is not installed.")
        script=self.root/WORKSPACES[name][0]
        # A fixed executable and script, no user command or shell expansion.
        process=self.spawn([self.executable,"-E","-s",str(script)],
                           cwd=str(self.root),shell=False)
        self.process,self.name=process,name

    def collect(self):
        if self.process is None:
            return None
        code=self.process.poll()
        if code is None:
            return None
        result=(self.name,code)
        self.process=self.name=None
        return result


def diagnostics(root=ROOT):
    return {
        "python": ".".join(map(str,sys.version_info[:3])),
        "python_supported": sys.version_info>=(3,10),
        "tkinter_available":importlib.util.find_spec("tkinter") is not None,
        "assistant_installed":installed("assistant",root),
        "analytics_installed":installed("analytics",root),
        "professional_export_installed":(Path(root)/"astra_report_export.py").is_file(),
        "scope":"Presence checks only; not GUI, model or hardware validation.",
    }


def main(argv=None):
    import argparse
    import json
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--diagnostics",action="store_true")
    args=parser.parse_args(argv)
    if args.diagnostics:
        print(json.dumps(diagnostics(),indent=2))
        return 0
    if sys.version_info<(3,10):
        print("Python 3.10 or later required.",file=sys.stderr)
        return 1
    import tkinter as tk
    from tkinter import ttk,messagebox
    root=tk.Tk()
    root.title("Astra Desktop")
    root.geometry("820x500")
    root.minsize(680,440)
    bg="#101722"
    root.configure(bg=bg)
    style=ttk.Style(root)
    style.theme_use("clam")
    style.configure("TFrame",background=bg)
    style.configure("TLabel",background=bg,foreground="#e5edf6",font=("Segoe UI",11))
    style.configure("Brand.TLabel",foreground="#54d6c7",font=("Segoe UI",28,"bold"))
    style.configure("Muted.TLabel",foreground="#a7b7cc")
    style.configure("TButton",background="#26374a",foreground="#e5edf6",
                    font=("Segoe UI",11,"bold"),padding=(14,10))
    outer=ttk.Frame(root,padding=30)
    outer.pack(fill="both",expand=True)
    ttk.Label(outer,text="ASTRA",style="Brand.TLabel").pack(anchor="w")
    ttk.Label(outer,text="Offline desktop intelligence",style="Muted.TLabel").pack(anchor="w",pady=(0,24))
    controller=WorkspaceProcess()
    status=tk.StringVar(value="Ready · Local processing")
    buttons={}
    def sync():
        active=controller.active()
        for name,button in buttons.items():
            button.state(["!disabled"] if not active and installed(name) else ["disabled"])
    def start(name):
        try:
            controller.launch(name)
            status.set(WORKSPACES[name][1]+" is open. Close it to switch workspaces.")
            sync()
        except (OSError,ValueError,RuntimeError):
            status.set("Workspace unavailable. Check the installation.")
    for name,(_,title,description) in WORKSPACES.items():
        row=ttk.Frame(outer,padding=(0,8))
        row.pack(fill="x")
        button=ttk.Button(row,text=title,command=lambda n=name:start(n))
        button.pack(side="left",padx=(0,16))
        buttons[name]=button
        ttk.Label(row,text=description,wraplength=400).pack(side="left",fill="x",expand=True)
    ttk.Separator(outer).pack(fill="x",pady=14)
    ttk.Label(outer,text="Professional reports are available through the local report-export command when installed.",wraplength=670,style="Muted.TLabel").pack(anchor="w")
    ttk.Label(outer,textvariable=status,wraplength=670).pack(side="bottom",anchor="w",pady=8)
    def poll():
        finished=controller.collect()
        if finished:
            name,code=finished
            status.set(WORKSPACES[name][1]+(" closed." if code==0 else " exited with an error."))
            sync()
        root.after(200,poll)
    def close():
        if controller.active():
            messagebox.showinfo("Workspace active","Close the current workspace before closing Astra.")
            return
        root.destroy()
    sync()
    root.protocol("WM_DELETE_WINDOW",close)
    root.after(200,poll)
    root.mainloop()
    return 0


if __name__=="__main__":
    sys.exit(main())
