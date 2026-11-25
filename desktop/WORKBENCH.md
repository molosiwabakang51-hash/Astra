# Astra Desktop workbench (experimental)

The workbench combines four local workspaces: Finance, Knowledge, Evidence and Runtime. It uses Python 3.10+ and the standard-library Tkinter interface (included in a typical Windows Python installation). No pip dependencies or network services are required for model-free operation.

From the repository root:

```powershell
python desktop/astra_desktop_gui.py
```

Finance supports the existing bounded English calculation and scenario patterns. Try a present value question, change the rate, and compare both results. The calculation result is exact under the supplied assumptions; displayed currency values are rounded. No database or model is necessary.

Create a new dedicated evidence database or open an existing one in Evidence. Review each record and its source before saving. To correct a record, enter its active evidence ID and the same subject. This is owner-reviewed knowledge, not independently verified information.

Knowledge searches the database without a model by default. To enable a tentative generated answer, configure trusted compatible local llama-cli and GGUF files in Runtime, explicitly approve them, then select Generate local draft in Knowledge. Independent review requires a distinct critic. No model can write evidence automatically. Drafts are clearly labelled and contextual follow-ups are bounded.

One worker serializes operations and owns SQLite access; the GUI main thread polls results instead of blocking during native inference. Concurrent generation is not supported. The window cannot be closed during an active operation; cancellation is not implemented. Model paths, settings and conversations are not persisted by the workbench. The selected evidence database remains on disk.

This first GUI does not expose semantic indexing, adaptive deep mode, task planning, benchmark management, export, or unrestricted conversation. Those remain separate experimental interfaces. Headless controller tests cover the integration, but the Windows GUI layout, native model compatibility and physical-device performance require direct testing. Avoid confidential inputs until native runtime privacy and reliability have been evaluated.
