# Astra financial analytics workspace

Use Python 3.10+ with the standard-library Tkinter installation to launch the separate analytics window:

```powershell
python desktop/astra_analytics_gui.py
```

Choose Portfolio attribution or Cash-flow performance and Basic CSV form. Export the corresponding spreadsheet table as UTF-8 comma-separated CSV, choose it, enter the reporting dates and currency, and explicitly confirm the stated valuation basis. For cash-flow performance also supply opening and closing total values. A portfolio benchmark return is optional and expressed as a decimal ratio.

Example CSV files are in desktop/examples. Portfolio headers must be exactly symbol,opening_value,closing_value,cash_income. Cash-flow headers must be exactly date,value_before_flow,external_flow. Historical risk, backtest and bootstrap simulation can import aligned dated returns using date followed by the explicitly ordered asset identifiers, and an optional benchmark column (not for simulation). Dates must be strictly increasing. Return and amount cells must be plain decimal strings, not percentages, currency symbols, formulas, missing cells or padded values. Dates do not verify the sampling interval or absence of missing observations. No Excel formula execution, direct XLSX parsing, automatic conversions or guessed header mappings.

Other installed engines can use Advanced JSON or supported CSV plus metadata JSON. The registry lists only modules present in the local installation; optional modes may become available after pending integrations merge. The window is separate from the original desktop workbench and has not been visually tested on Windows.

For the unified command line:

```powershell
python desktop/astra_analytics.py --list
python desktop/astra_analytics.py portfolio desktop/examples/portfolio_holdings.csv --metadata desktop/examples/portfolio_metadata.json --output portfolio.md
python desktop/astra_analytics.py cashflow desktop/examples/cashflow_events.csv --metadata desktop/examples/cashflow_metadata.json
```

Use --format json to export a structured result with provenance. The report records input-file SHA-256 and canonical request identity; CSV imports also report the observation count and applicable date range. These receipts establish input identity, not market data accuracy, independent source validation or execution attestation. All exports create new files rather than overwriting. The per-engine validation, limitations and assumptions remain authoritative. Analysis uses no model or external service. Windows interface testing and commercial validation remain outstanding.

The analytics window invalidates a completed report when an input field changes, requires renewed basis confirmation, and disables input edits during operations. Save controls are enabled only for a current completed report. When the optional professional renderer is installed, Save printable HTML exports that same result with its input traceability; open the file locally and use browser printing to create a PDF. No recalculation is performed on export. Windows visual testing remains pending.
