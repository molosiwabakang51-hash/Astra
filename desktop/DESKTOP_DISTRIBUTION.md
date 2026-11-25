# Astra Desktop source distribution

Astra Desktop has one local launcher for the Assistant and Financial Analytics
workspaces. Each workspace uses an independent Python process; only one can
be active at a time through that launcher. Close it to return to the launcher.
This isolates Tk event loops and database connections and limits concurrent
model/resource use. It is not a system-wide process lock, installer, or a
single native Windows executable.

## Windows prerequisites

- Windows with an existing trusted Python 3.10+ installation and Tkinter.
- No additional Python packages, model weights or network services are needed
  for deterministic financial calculations and reviewed evidence search.
- Local model generation is optional and still requires compatible local
  llama-cli and GGUF files and explicit approval in the Assistant workspace.

From the repository:

~~~powershell
python desktop/astra_launcher.py
python desktop/astra_launcher.py --diagnostics
~~~

Build the source ZIP from an approved repository checkout:

~~~powershell
python desktop/astra_desktop_distribution.py build AstraDesktop.zip
python desktop/astra_desktop_distribution.py verify AstraDesktop.zip
~~~

Extract the verified ZIP into a new directory. Double-click ASTRA_START.cmd
inside AstraDesktop, or run python astra_launcher.py in that directory.

The builder includes explicitly listed application source, documentation and
synthetic examples, and includes optional analytical groups only when complete.
It excludes personal data, user databases, arbitrary files, models, Python
installers and native executables. The archive is deterministic for the same
source bytes and ZIP implementation. The manifest records file sizes and SHA-256
digests. Verification checks completeness and archive contents without
extracting them. Digests detect inconsistency, not publisher authenticity:
obtain the ZIP from a trusted source. No digital signature is provided.

The launcher's diagnostics check presence, not actual graphical functionality
or inference. Professional HTML reports use the separate local
astra_report_export.py command when installed. Browser print-to-PDF remains
available through that workflow. Existing user evidence databases and model
files are selected manually; nothing is automatically copied or downloaded.
Application state is not transferred between the separate workspaces. Keep
personal data outside the distribution.

The Windows workflow builds and verifies the source ZIP and tests the
extracted application's imports and diagnostics. A clean-machine visual
acceptance test, native model inference, an independently signed executable,
and actual performance on the target computer remain outstanding.
