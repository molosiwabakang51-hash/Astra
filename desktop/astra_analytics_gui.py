"""Standalone offline analytics window; no model or database required.

The two basic CSV forms collect conventions explicitly without hand-written
JSON. Advanced modes accept previously prepared JSON or CSV+metadata files.
Analysis and file writing run on one worker; Tk is main-thread only.
"""
import queue
import threading
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

from astra_analytics import available, from_files, run_analysis, render, write_new
from astra_analytics_csv import ingest
from astra_quantitative_verify import number

BASIS={
    "portfolio":"unchanged_holdings_no_flows_income_excluded",
    "cashflow":"pre_flow_valuations_total_portfolio_value",
}
LABELS={
    "portfolio":"Portfolio attribution",
    "cashflow":"Cash-flow performance",
    "bond":"Fixed-income valuation",
    "curve":"Yield-curve analytics",
    "optimization":"Portfolio optimization",
    "historical-risk":"Historical risk",
    "simulation":"Bootstrap simulation",
    "backtest":"Strategy backtest",
}


def simple_request(mode,path,currency,start,end,*,confirmed=False,
                   opening="",closing="",benchmark=""):
    if confirmed is not True or mode not in BASIS:
        raise ValueError("Explicit confirmation of the analysis basis is required.")
    metadata={"currency":currency,"start":start,"end":end,"basis":BASIS[mode]}
    if mode=="portfolio":
        if benchmark:
            number(benchmark)
            metadata["benchmark_total_return"]=benchmark
    else:
        number(opening)
        number(closing)
        metadata.update(opening_value=opening,closing_value=closing)
    return ingest(mode,path,metadata)


class AnalyticsWindow:
    def __init__(self,root):
        self.root=root
        root.title("Astra | Financial Analytics")
        root.geometry("1000x720")
        root.minsize(760,560)
        self.modes=[name for name,ready in available().items() if ready]
        if not self.modes:
            raise RuntimeError("No analytics modules installed.")
        self.mode=tk.StringVar(value=self.modes[0])
        self.kind=tk.StringVar(value="Basic CSV form")
        self.source=tk.StringVar()
        self.metadata=tk.StringVar()
        self.currency=tk.StringVar(value="BWP")
        self.start=tk.StringVar()
        self.end=tk.StringVar()
        self.opening=tk.StringVar()
        self.closing=tk.StringVar()
        self.benchmark=tk.StringVar()
        self.confirmed=tk.BooleanVar(value=False)
        self.status=tk.StringVar(value="Ready · Offline")
        self.results=queue.Queue()
        self.busy=False
        self.report=None
        self.buttons=[]
        self.build()
        root.protocol("WM_DELETE_WINDOW",self.close)

    def button(self,parent,label,command):
        button=ttk.Button(parent,text=label,command=command)
        self.buttons.append(button)
        return button

    def field(self,parent,label,var,browse=False):
        row=ttk.Frame(parent)
        row.pack(fill="x",pady=3)
        ttk.Label(row,text=label,width=27).pack(side="left")
        ttk.Entry(row,textvariable=var).pack(side="left",fill="x",expand=True)
        if browse:
            self.button(row,"Browse",lambda:self.pick(var)).pack(side="left",padx=5)
        return row

    def build(self):
        outer=ttk.Frame(self.root,padding=18)
        outer.pack(fill="both",expand=True)
        ttk.Label(outer,text="Astra Financial Analytics",font=("Segoe UI",20,"bold")).pack(anchor="w")
        ttk.Label(outer,text="Local analytical reports · Explicit inputs · No model required").pack(anchor="w",pady=(0,10))
        controls=ttk.Frame(outer)
        controls.pack(fill="x")
        ttk.Label(controls,text="Analysis").pack(side="left")
        selector=ttk.Combobox(controls,values=self.modes,textvariable=self.mode,state="readonly",width=22)
        selector.pack(side="left",padx=6)
        selector.bind("<<ComboboxSelected>>",self.refresh)
        kind=ttk.Combobox(controls,values=("Basic CSV form","Advanced JSON","CSV + metadata JSON"),
                          textvariable=self.kind,state="readonly",width=23)
        kind.pack(side="left",padx=6)
        kind.bind("<<ComboboxSelected>>",self.refresh)
        self.field(outer,"Input file",self.source,browse=True)
        self.metadata_row=self.field(outer,"Metadata JSON",self.metadata,browse=True)
        self.basic=ttk.Frame(outer)
        self.field(self.basic,"Reporting currency",self.currency)
        self.field(self.basic,"Start date (YYYY-MM-DD)",self.start)
        self.field(self.basic,"End date (YYYY-MM-DD)",self.end)
        self.open_row=self.field(self.basic,"Opening value",self.opening)
        self.close_row=self.field(self.basic,"Closing value",self.closing)
        self.bench_row=self.field(self.basic,"Benchmark return (decimal)",self.benchmark)
        self.basis_text=tk.StringVar()
        ttk.Label(self.basic,textvariable=self.basis_text,wraplength=650).pack(anchor="w",pady=4)
        ttk.Checkbutton(self.basic,text="I confirm the input basis and reporting conventions",
                        variable=self.confirmed).pack(anchor="w")
        self.basic.pack(fill="x",pady=5)
        actions=ttk.Frame(outer)
        self.actions=actions
        actions.pack(fill="x",pady=10)
        self.button(actions,"Analyze",self.analyze).pack(side="left")
        self.save_md=self.button(actions,"Save Markdown",lambda:self.save("markdown"))
        self.save_md.pack(side="right")
        self.save_json=self.button(actions,"Save JSON",lambda:self.save("json"))
        self.save_json.pack(side="right",padx=5)
        self.output=tk.Text(outer,wrap="none",font=("Consolas",10),state="disabled")
        self.output.pack(fill="both",expand=True)
        ttk.Label(outer,textvariable=self.status).pack(anchor="w",pady=(6,0))
        self.refresh()

    def refresh(self,event=None):
        self.confirmed.set(False)
        mode=self.mode.get()
        if mode not in BASIS and self.kind.get()=="Basic CSV form":
            self.kind.set("Advanced JSON")
        if self.kind.get()=="Basic CSV form":
            self.basic.pack(fill="x",before=self.actions)
            self.metadata_row.pack_forget()
            if mode=="portfolio":
                self.open_row.pack_forget()
                self.close_row.pack_forget()
                self.bench_row.pack(fill="x",pady=3,before=self.basic.winfo_children()[-2])
                self.basis_text.set("CSV: symbol,opening_value,closing_value,cash_income. Unchanged holdings; no flows; cash income excluded from closing values.")
            else:
                self.bench_row.pack_forget()
                self.open_row.pack(fill="x",pady=3,before=self.basic.winfo_children()[-2])
                self.close_row.pack(fill="x",pady=3,before=self.basic.winfo_children()[-2])
                self.basis_text.set("CSV: date,value_before_flow,external_flow. Immediately pre-flow total valuations; positive flows are contributions.")
        else:
            self.basic.pack_forget()
            if self.kind.get()=="CSV + metadata JSON":
                self.metadata_row.pack(fill="x",pady=3,before=self.actions)
            else:
                self.metadata_row.pack_forget()

    def pick(self,var):
        path=filedialog.askopenfilename(filetypes=[("Financial input","*.csv *.json"),("All files","*")])
        if path:
            var.set(path)

    def submit(self,fn):
        if self.busy:
            return
        self.busy=True
        for b in self.buttons:
            b.state(["disabled"])
        self.status.set("Working locally · Current operation must finish before closing")
        def worker():
            try:
                self.results.put((True,fn()))
            except (ValueError,OSError,RuntimeError,ArithmeticError,TypeError,RecursionError):
                self.results.put((False,None))
            except Exception:
                self.results.put((False,None))
        threading.Thread(target=worker,daemon=False).start()
        self.root.after(80,self.poll)

    def analyze(self):
        if self.busy:
            return
        self.report=None
        mode,kind,path=self.mode.get(),self.kind.get(),self.source.get()
        metadata=self.metadata.get()
        if kind=="Basic CSV form":
            kwargs=dict(confirmed=self.confirmed.get(),opening=self.opening.get(),
                        closing=self.closing.get(),benchmark=self.benchmark.get())
            args=(mode,path,self.currency.get(),self.start.get(),self.end.get())
            def task():
                request,receipt=simple_request(*args,**kwargs)
                return "report",run_analysis(mode,request,receipt=receipt)
        else:
            def task():
                return "report",from_files(mode,path,metadata_path=metadata if kind=="CSV + metadata JSON" else None)
        self.submit(task)

    def poll(self):
        try:
            ok,result=self.results.get_nowait()
        except queue.Empty:
            self.root.after(80,self.poll)
            return
        self.busy=False
        for b in self.buttons:
            b.state(["!disabled"])
        if not ok:
            self.status.set("Unavailable · Check input format, conventions or output path")
            return
        kind,value=result
        if kind=="report":
            self.report=value
            envelope,markdown=value
            self.output.configure(state="normal")
            self.output.delete("1.0","end")
            self.output.insert("end",render(envelope,markdown,"markdown"))
            self.output.configure(state="disabled")
            self.status.set("Ready · Report generated")
        else:
            self.status.set("Ready · Report saved")

    def save(self,format):
        if self.report is None or self.busy:
            return
        path=filedialog.asksaveasfilename(defaultextension=".md" if format=="markdown" else ".json")
        if not path:
            return
        envelope,markdown=self.report
        def task():
            write_new(path,render(envelope,markdown,format))
            return "saved",None
        self.submit(task)

    def close(self):
        if self.busy:
            messagebox.showinfo("Operation active","Finish the current operation before closing.")
            return
        self.root.destroy()


def main():
    root=tk.Tk()
    AnalyticsWindow(root)
    root.mainloop()


if __name__=="__main__":
    main()
