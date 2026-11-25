# Professional local analytical reports

The reporting layer provides self-contained HTML documents with print styles, accessible tables and result-driven charts. It executes no model or script and loads no external assets. The command-line export requires the unified analytics registry introduced in PR #377; both components must be present.

From the repository root:

```powershell
python desktop/astra_report_export.py portfolio desktop/examples/portfolio_analysis.json --output portfolio-report.html
```

Open the resulting local HTML document in a browser. Use the browser's Print command and select Save as PDF (or Microsoft Print to PDF). The report uses A4 landscape print styles, repeated table headers and page-break controls. Browser rendering and physical PDF pagination need visual validation. This is a browser-based PDF workflow, not a native PDF generator.

CSV input can be combined with metadata using --metadata. All analytical assumptions, results and input traceability supplied by the unified runner are preserved in the report. Portfolio allocation, bond yield sensitivity, continuous zero curves, efficient allocation grids and historical backtest wealth have dedicated visuals. Backtest visuals sample at most 120 observation points per strategy; complete tables remain authoritative. Missing or unplottable values do not produce invented charts.

Export an individual displayed table for spreadsheet review:

```powershell
python desktop/astra_report_export.py portfolio desktop/examples/portfolio_analysis.json --list-tables
python desktop/astra_report_export.py portfolio desktop/examples/portfolio_analysis.json --format csv --table 1 --output portfolio-summary.csv
```

CSV uses UTF-8 with BOM and escapes formula-like text while retaining ordinary signed numbers. It contains the displayed, rounded table strings, not full-precision machine-readable data or workbook formulas. Use the analytics JSON export for authoritative numerical detail. Excel may interpret some formatted values as text; no XLSX workbook is created.

HTML rendering supports only the deterministic reporters' Markdown subset and escapes raw markup. A restrictive content security policy blocks external resources and script execution. Exports never overwrite an existing file. The charts and reports are descriptive, not investment recommendations, validated market data or regulatory/commercial certification. No new software purchase or external service is required. GUI integration and Windows print-preview testing remain outstanding.
