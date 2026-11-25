"""Astra offline desktop workbench, using only Python's standard library.

The single worker owns the controller and all database access. Tk widgets are
only updated on the main thread. There is no automatic persistence of chats.
"""
import queue
import threading
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

from astra_workbench import Workbench, format_result

BG = "#101722"
PANEL = "#1b2635"
INPUT = "#121c29"
FG = "#e5edf6"
MUTED = "#a7b7cc"
ACCENT = "#54d6c7"
BORDER = "#33455a"


class Worker:
    def __init__(self):
        self.requests = queue.Queue(maxsize=1)
        self.results = queue.Queue()
        self.thread = threading.Thread(target=self.run, daemon=False, name="astra-desktop-worker")
        self.thread.start()

    def run(self):
        workbench = Workbench()
        try:
            while True:
                job = self.requests.get()
                if job is None:
                    break
                name, method, args, kwargs = job
                try:
                    result = getattr(workbench, method)(*args, **kwargs)
                    self.results.put((name, True, format_result(result)))
                except (ValueError, OSError, RuntimeError, TypeError, RecursionError) as error:
                    # Validation errors have useful bounded messages, but native
                    # runtime paths or arbitrary exception text must not leak.
                    message = str(error)[:300] if isinstance(error, ValueError) else "Local operation failed. Check the selected files and available memory."
                    self.results.put((name, False, message))
                except Exception:
                    self.results.put((name, False, "Local operation failed. No result."))
        finally:
            try:
                workbench.close()
            finally:
                self.results.put(("closed", True, ""))


class AstraApp:
    def __init__(self, root):
        self.root = root
        root.title("Astra | Offline Workbench")
        root.geometry("1040x720")
        root.minsize(820, 580)
        root.configure(bg=BG)
        self.worker = Worker()
        self.pending = False
        self.closing = False
        self.buttons = []
        self.outputs = {}
        self.status = tk.StringVar(value="Ready · No model enabled")
        self.database = tk.StringVar(value="No database connected")
        self.models = tk.StringVar(value="Local generation disabled")
        self.generate = tk.BooleanVar(value=False)
        self.review = tk.BooleanVar(value=False)
        self.attest = tk.BooleanVar(value=False)
        self.model_approval = tk.BooleanVar(value=False)
        self.binary = tk.StringVar()
        self.primary = tk.StringVar()
        self.critic = tk.StringVar()
        self.timeout = tk.StringVar(value="180")
        self.setup_style()
        self.layout()
        root.protocol("WM_DELETE_WINDOW", self.close)
        root.after(80, self.poll)

    def setup_style(self):
        style = ttk.Style(self.root)
        style.theme_use("clam")
        style.configure(".", background=BG, foreground=FG, font=("Segoe UI", 10))
        style.configure("TFrame", background=BG)
        style.configure("Panel.TFrame", background=PANEL)
        style.configure("TLabel", background=BG, foreground=FG)
        style.configure("Muted.TLabel", background=BG, foreground=MUTED)
        style.configure("Heading.TLabel", background=BG, foreground=FG, font=("Segoe UI", 19, "bold"))
        style.configure("Brand.TLabel", background=BG, foreground=ACCENT, font=("Segoe UI", 24, "bold"))
        style.configure("TButton", background=PANEL, foreground=FG, padding=(12, 8), bordercolor=BORDER)
        style.map("TButton", background=[("active", BORDER)], foreground=[("disabled", MUTED)])
        style.configure("Accent.TButton", background=ACCENT, foreground=BG, font=("Segoe UI", 10, "bold"))
        style.map("Accent.TButton", background=[("active", "#83e8dd"), ("disabled", BORDER)])
        style.configure("TEntry", fieldbackground=INPUT, foreground=FG, insertcolor=FG, bordercolor=BORDER, padding=6)
        style.configure("TCheckbutton", background=BG, foreground=FG)
        style.map("TCheckbutton", background=[("active", BG)])
        style.configure("TNotebook", background=BG, borderwidth=0)
        style.configure("TNotebook.Tab", background=PANEL, foreground=MUTED, padding=(18, 11))
        style.map("TNotebook.Tab", background=[("selected", BORDER)], foreground=[("selected", FG)])
        style.configure("Vertical.TScrollbar", background=PANEL, troughcolor=BG, arrowcolor=MUTED)

    def button(self, parent, text, command, *, accent=False):
        widget = ttk.Button(parent, text=text, command=command,
                            style="Accent.TButton" if accent else "TButton")
        self.buttons.append(widget)
        return widget

    def layout(self):
        header = ttk.Frame(self.root, padding=(24, 14))
        header.pack(fill="x")
        ttk.Label(header, text="ASTRA", style="Brand.TLabel").pack(side="left")
        ttk.Label(header, text="  /  LOCAL INTELLIGENCE WORKBENCH", style="Muted.TLabel").pack(side="left", padx=12)
        ttk.Label(header, text="OFFLINE  ·  EXPERIMENTAL", style="Muted.TLabel").pack(side="right")
        tabs = ttk.Notebook(self.root)
        tabs.pack(fill="both", expand=True, padx=24, pady=(0, 10))
        self.finance_tab(tabs)
        self.knowledge_tab(tabs)
        self.evidence_tab(tabs)
        self.runtime_tab(tabs)
        ttk.Label(self.root, textvariable=self.status, style="Muted.TLabel",
                  padding=(24, 8)).pack(fill="x")

    def page(self, tabs, label, title, subtitle):
        frame = ttk.Frame(tabs, padding=18)
        tabs.add(frame, text=label)
        ttk.Label(frame, text=title, style="Heading.TLabel").pack(anchor="w")
        ttk.Label(frame, text=subtitle, style="Muted.TLabel", wraplength=740).pack(anchor="w", pady=(5, 15))
        return frame

    def text_widget(self, parent, height=6, readonly=False):
        holder = ttk.Frame(parent)
        holder.pack(fill="both", expand=True if readonly else False)
        text = tk.Text(holder, height=height, bg=INPUT, fg=FG, insertbackground=FG,
                       selectbackground=BORDER, font=("Segoe UI", 11), wrap="word",
                       padx=13, pady=10, relief="flat", highlightthickness=1,
                       highlightbackground=BORDER, highlightcolor=ACCENT, undo=not readonly)
        scrollbar = ttk.Scrollbar(holder, orient="vertical", command=text.yview)
        text.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")
        text.pack(side="left", fill="both", expand=True)
        if readonly:
            text.configure(state="disabled")
        return text

    def append(self, target, value, replace=False):
        field = self.outputs[target]
        field.configure(state="normal")
        if replace:
            field.delete("1.0", "end")
        # Limit display memory, not the underlying calculation precision.
        if len(field.get("1.0", "end-1c")) > 60000:
            field.delete("1.0", "end")
        field.insert("end", value + "\n\n")
        field.see("end")
        field.configure(state="disabled")

    def submit(self, name, method, *args, **kwargs):
        if self.pending or self.closing:
            return
        self.pending = True
        for button in self.buttons:
            button.state(["disabled"])
        self.status.set("Working locally · Wait for the current operation to finish")
        self.worker.requests.put_nowait((name, method, args, kwargs))

    def ask(self, target, widget, method, **kwargs):
        question = widget.get("1.0", "end-1c").strip()
        if not question:
            return
        if len(question) > 500:
            messagebox.showerror("Question too long", "Use at most 500 characters.")
            return
        self.append(target, "YOU\n" + question)
        widget.delete("1.0", "end")
        self.submit(target, method, question, **kwargs)

    def finance_tab(self, tabs):
        frame = self.page(tabs, "Finance", "Financial intelligence",
                          "Exact calculations and contextual scenarios. No model or internet required.")
        self.outputs["finance"] = self.text_widget(frame, readonly=True)
        self.append("finance", "Try: Calculate the present value of P10,000 at 8% over five years.\nThen: What if 10%?\nThen: Compare both results.")
        composer = self.text_widget(frame, height=3)
        actions = ttk.Frame(frame)
        actions.pack(fill="x", pady=(8, 0))
        self.button(actions, "Calculate", lambda: self.ask("finance", composer, "ask_finance"), accent=True).pack(side="right")
        self.button(actions, "Reset scenario", lambda: self.reset("finance")).pack(side="left")

    def knowledge_tab(self, tabs):
        frame = self.page(tabs, "Knowledge", "Knowledge and learning",
                          "Search reviewed local records, or explicitly enable tentative model-generated explanations.")
        self.outputs["knowledge"] = self.text_widget(frame, readonly=True)
        self.append("knowledge", "Connect a reviewed evidence database in the Evidence workspace. Search does not require a model.")
        options = ttk.Frame(frame)
        options.pack(fill="x", pady=8)
        ttk.Checkbutton(options, text="Generate local draft", variable=self.generate).pack(side="left")
        ttk.Checkbutton(options, text="Independent review", variable=self.review).pack(side="left", padx=18)
        composer = self.text_widget(frame, height=3)
        actions = ttk.Frame(frame)
        actions.pack(fill="x", pady=(8, 0))
        self.button(actions, "Ask Astra", lambda: self.ask("knowledge", composer, "ask_knowledge",
                    generate=self.generate.get(), review=self.review.get()), accent=True).pack(side="right")
        self.button(actions, "Reset context", lambda: self.reset("knowledge")).pack(side="left")

    def reset(self, section):
        if not self.pending:
            self.append(section, "", replace=True)
            self.submit(section, "reset", section)

    def evidence_tab(self, tabs):
        frame = self.page(tabs, "Evidence", "Reviewed knowledge library",
                          "Open or create a dedicated local database. Only owner-reviewed records can be saved.")
        bar = ttk.Frame(frame)
        bar.pack(fill="x", pady=(0, 12))
        ttk.Label(bar, textvariable=self.database).pack(side="left")
        self.button(bar, "Open", lambda: self.choose_database(False)).pack(side="right")
        self.button(bar, "Create", lambda: self.choose_database(True)).pack(side="right", padx=6)
        self.subject = tk.StringVar()
        self.source = tk.StringVar()
        self.supersedes = tk.StringVar()
        for label, value in (("Subject", self.subject), ("Source / provenance", self.source),
                             ("Corrects evidence ID (optional)", self.supersedes)):
            row = ttk.Frame(frame)
            row.pack(fill="x", pady=4)
            ttk.Label(row, text=label, width=29).pack(side="left")
            ttk.Entry(row, textvariable=value).pack(side="left", fill="x", expand=True)
        ttk.Label(frame, text="Evidence text").pack(anchor="w", pady=(8, 4))
        self.evidence_text = self.text_widget(frame, height=6)
        ttk.Checkbutton(frame, text="I reviewed this record and its provenance", variable=self.attest).pack(anchor="w", pady=9)
        self.button(frame, "Save reviewed record", self.save_evidence, accent=True).pack(anchor="e")
        self.outputs["evidence"] = self.text_widget(frame, height=3, readonly=True)

    def choose_database(self, create):
        types = [("SQLite evidence database", "*.sqlite *.sqlite3 *.db")]
        if create:
            path = filedialog.asksaveasfilename(defaultextension=".sqlite", filetypes=types)
        else:
            path = filedialog.askopenfilename(filetypes=types)
        if path:
            self.submit("database", "connect", path, create=create)

    def save_evidence(self):
        if not self.attest.get():
            messagebox.showerror("Review required", "Review the evidence and its source before saving.")
            return
        try:
            raw = self.supersedes.get().strip()
            supersedes = int(raw) if raw else None
            if supersedes is not None and supersedes <= 0:
                raise ValueError()
        except ValueError:
            messagebox.showerror("Invalid correction ID", "Enter a positive evidence ID or leave it blank.")
            return
        self.submit("evidence", "add_evidence", self.subject.get(),
                    self.evidence_text.get("1.0", "end-1c"), self.source.get(),
                    reviewed=True, supersedes=supersedes)

    def runtime_tab(self, tabs):
        frame = self.page(tabs, "Runtime", "Local model configuration",
                          "Optional CPU-only local inference. Select trusted compatible files; no automatic downloads.")
        ttk.Label(frame, textvariable=self.models, style="Muted.TLabel").pack(anchor="w", pady=(0, 12))
        for label, value, extension in (("llama-cli executable", self.binary, "*"),
                                        ("Primary GGUF", self.primary, "*.gguf"),
                                        ("Optional critic GGUF", self.critic, "*.gguf")):
            row = ttk.Frame(frame)
            row.pack(fill="x", pady=6)
            ttk.Label(row, text=label, width=24).pack(side="left")
            ttk.Entry(row, textvariable=value).pack(side="left", fill="x", expand=True)
            self.button(row, "Browse", lambda v=value, e=extension: self.pick_file(v, e)).pack(side="left", padx=6)
        row = ttk.Frame(frame)
        row.pack(fill="x", pady=8)
        ttk.Label(row, text="Timeout (seconds, 30–300)", width=28).pack(side="left")
        ttk.Entry(row, textvariable=self.timeout, width=10).pack(side="left")
        ttk.Checkbutton(frame, text="I approve local inference using these trusted model and runtime files",
                        variable=self.model_approval).pack(anchor="w", pady=10)
        actions = ttk.Frame(frame)
        actions.pack(fill="x")
        self.button(actions, "Configure models", self.configure, accent=True).pack(side="right")
        self.button(actions, "Disable generation", lambda: self.submit("runtime", "disable_models")).pack(side="left")
        self.outputs["runtime"] = self.text_widget(frame, readonly=True)
        self.append("runtime", "Models are optional. Configuration checks do not establish native compatibility or answer accuracy. Inference may take several minutes on a CPU. Initial testing should use synthetic, non-confidential inputs.")

    def pick_file(self, variable, pattern):
        path = filedialog.askopenfilename(filetypes=[("Local files", pattern)])
        if path:
            variable.set(path)

    def configure(self):
        if not self.model_approval.get():
            messagebox.showerror("Approval required", "Approve trusted local model files first.")
            return
        try:
            timeout = int(self.timeout.get())
            if not 30 <= timeout <= 300:
                raise ValueError()
        except ValueError:
            messagebox.showerror("Invalid timeout", "Choose 30–300 seconds.")
            return
        if not self.binary.get().strip() or not self.primary.get().strip():
            messagebox.showerror("Missing model files", "Select a runtime and primary model.")
            return
        self.submit("runtime", "configure_models", self.binary.get(), self.primary.get(),
                    self.critic.get() or None, approved=True, timeout=timeout)

    def poll(self):
        while True:
            try:
                name, ok, message = self.worker.results.get_nowait()
            except queue.Empty:
                break
            if name == "closed":
                self.root.destroy()
                return
            self.pending = False
            if name == "database":
                self.append("evidence", message)
                if ok:
                    self.database.set(message)
                    self.append("knowledge", "Evidence database changed; context reset.", replace=True)
            else:
                self.append(name, ("ASTRA\n" if ok else "UNAVAILABLE\n") + message)
            if name == "runtime" and ok:
                self.models.set(message)
                self.generate.set(False)
                self.review.set(False)
                self.model_approval.set(False)
            if name == "evidence" and ok:
                self.attest.set(False)
                self.supersedes.set("")
            self.status.set("Ready · Local operation completed" if ok else "Ready · Operation unavailable")
            for button in self.buttons:
                button.state(["!disabled"])
        if not self.closing or self.worker.thread.is_alive():
            self.root.after(80, self.poll)

    def close(self):
        if self.pending:
            messagebox.showinfo("Operation active", "Finish the current bounded local operation before closing. Cancellation is not yet implemented.")
            return
        if not self.closing:
            self.closing = True
            self.status.set("Closing local workspace")
            self.worker.requests.put_nowait(None)


def main():
    root = tk.Tk()
    AstraApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
