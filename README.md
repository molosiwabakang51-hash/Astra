# Astra — Android personal assistant (v0.12.1 · safer offline chat recovery)

Astra is a **local-first Android/Kotlin Jetpack Compose assistant** with a futuristic dark interface. It is being developed directly in this repository. The original **Astra-AI** repository is kept separately as a backup.

**Release status: EXPERIMENTAL.** Compilation and APK checks pass, but stable status requires on-phone offline inference, one-time model import, chat/Stop usability, data-preserving same-key updates and privacy checks. See the [stable-release acceptance checklist](docs/stable-release-acceptance.md). Owner-signed builds remain GitHub **pre-releases** until those mandatory checks pass.

## One-time private library folder authorization (experimental)

In **Settings → Your private book library**, choose **Authorize library folder once**
using Android's system folder picker. Astra retains a read-only
Storage Access Framework (SAF) grant to that selected folder across restarts
and correctly signed in-place updates; no permanent all-files permission is
requested. On a later visit, tap **Import books from authorized folder** to
index supported TXT, Markdown and EPUB files in the folder, without another
file-selection prompt. Importing is explicit, never an automatic/background
scan, and the original files remain untouched. Up to 30 books can be stored
privately; input size and directory enumeration are bounded.

**This does not provide blanket access to your entire phone.** Android 11+
restricts choosing certain storage roots and protected folders. A grant may
stop working if its backing folder is moved, deleted or revoked. Use
**Forget folder authorization** to release the stored grant without deleting
books already imported into Astra's private index. One-off model and
vocabulary import retain their existing file-picker paths. The Android
permission and provider behavior still need physical-device verification.

## Zero-fee offline model strategy

**No mandatory paid AI API, subscription, remote model server, cloud GPU or paid hosting.**
On supported Android phones, Astra uses the strongest verified locally available model.
Qwen3 1.7B Q4_K_M is the preferred conversation model; the existing Qwen3 0.6B
continues to work as an offline fallback. The 1.7B GGUF can be obtained from
[ggml-org on Hugging Face](https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/blob/main/Qwen3-1.7B-Q4_K_M.gguf)
under Apache 2.0 and imported once through Settings. The application never
sends chat prompts to Hugging Face for inference.

Astra's low-data APK updates reuse verified model weights already stored in
app-private no-backup storage (with the same signing key). Neither every build
nor every update needs to host another 1.28 GB copy. The GitHub-hosted build
may still bundle compact GGUF weights in the FULL artifact; **installing that
artifact alone does not mean the 1.7B model is installed**. The 1.7B model
becomes the default automatically once imported.

**Zero-fee refers to software subscriptions and metered inference/hosting fees.**
Initial downloads, mobile data charges, power consumption and hardware are
physical costs, not included in the claim. Public GitHub Actions standard
runners are normally free; avoid charging for oversized artifact storage
or paid runner tiers. On-device speed, accuracy, memory pressure and battery
use still require tests on a physical phone before stable release.

## One-time free advanced-model download

In **Settings → Offline AI models → Get free Qwen3 1.7B model**, Astra asks
for explicit permission before downloading approximately **1.28 GB**.
The download is HTTPS-only from the published ggml-org Hugging Face model,
with a pinned SHA-256 checksum. It may use mobile data; Wi-Fi is recommended.

Downloads are saved in app-private storage and can be **paused and resumed**.
If the server does not support byte-range requests, the transfer restarts
safely rather than appending duplicate bytes. Astra verifies the full model
before replacing any working local copy. A completed corrupt download is
discarded. Subsequent same-key Android updates reuse the stored GGUF; chat
inference itself uses no network or paid API.

The original **Import offline model once** option remains for users who already
have the exact GGUF or want to transfer it offline from another device.
Physical-phone tests remain necessary before this is considered production-ready.

## Open Russian library and continuous neural topic adaptation (experimental)

**Offline reference context:** Astra now includes a small, attributed reference
pack covering Russian Cyrillic, first greetings, grammatical cases and verbal
aspect. The Android language model can retrieve up to three relevant excerpts
and cite their page links in a reply. This is **retrieval-augmented generation
(RAG)**: book knowledge is supplied as evidence, not installed as Qwen weights.
The pack has its own CC BY-SA 4.0 license and attribution notice in
`android/app/src/main/assets/knowledge/SOURCES.md`.

**Free public textbook updates:** In Settings → Local knowledge & continual
neural learning, select **Update free Russian library** to fetch short extracts
from a fixed list of Wikibooks and Wiktionary pages using Wikimedia's public
read-only HTTPS API. No private chat, tasks, notes or device data are uploaded.
A validated snapshot is saved locally and preserved across same-signing-key
updates. This network action is voluntary; conversations and retrieval remain
fully on-device after download. Full copyrighted paid textbooks are not silently
scraped.

**Actual local neural training, with a precise limit:** Users may turn on
**Learn topics from conversations** in Settings, or type
`start neural learning`. The app then performs one backpropagation/SGD update
to a **128-input, 24-hidden-unit, 3-topic neural network** for each eligible
user turn. The numeric weights are persisted to app-private no-backup storage,
and can be cleared with **Clear learned neural weights** or
`reset neural learning`. `learning status` reports whether training is
enabled and the number of weight updates. Sensitive or private-action commands
are excluded. This training is **off by default**, and no raw transcript is
saved inside the neural adapter. Its predictions can influence which licensed
reference excerpts Astra retrieves.

**Limitations: This is NOT fine-tuning Qwen3.** The Qwen3 GGUF inference
weights remain unchanged. The small model adapts topic/context selection only;
it does not acquire general language comprehension or comprehensive lifelong
knowledge. True on-device continual LLM fine-tuning would require a separate
LoRA/training backend, quality controls, feedback labels and a verified path
to applying trained adapters to the Android inference engine. The current
llama.cpp binding does not provide that training path.

Existing FULL and LITE distributions remain free of paid inference APIs.
Android CI/unit tests and physical phone testing are required before this
feature can be treated as stable.

## Better relevance in longer offline books

Private-book retrieval now ranks passages using **content evidence rather
than book-title words alone**. A textbook named *Russian Grammar* no longer
causes unrelated early chapters to be treated as evidence for genitive
grammar; the requested subject must occur in the passage itself.
Common English plural forms are normalized, with smaller weights for
generic language words. Search retains bounded top candidates per book,
so one large document cannot suppress every result from another useful
source.

Astra also selects a **question-relevant window inside each passage**
instead of blindly quoting its first 320 characters. The private index now
accommodates more passages per imported book (5,200 rather than 2,400)
without unbounded in-memory search results. Import limits and per-book
storage quotas remain in place; books may still be partially indexed.

This is a deterministic lexical-relevance improvement, **not semantic
embedding search, training of Qwen weights or a guarantee that a retrieved
passage contains a correct answer**. Private document contents remain
on-device and the existing opt-in retrieval permission is unchanged.

## Optional one-time shared-storage access (experimental)

Astra can optionally search **filenames** across accessible shared phone storage
without asking you to select every file repeatedly. In Astra Settings,
choose **Grant one-time shared-file access** and explicitly approve Android's
**All files access** special permission. Once enabled, type `search phone report`
or `search my phone for lecture`. Permission remains in force until you revoke
it through Android Settings or it is reset. The search scans only on your
explicit request, limits scan time and output, displays only filenames and
sizes, and does not copy, upload, alter, transcribe or read file contents.

**This is broad and sensitive permission.** Android grants the app read AND
write capability across accessible shared storage, even though Astra's current
search implementation reads filenames only and never modifies original files.
Use it only if you need cross-folder filename search. The feature never scans in the background, excludes hidden
and `Android/` directories, and cannot access another app's private files,
messages, databases, or cloud accounts. It does not automatically understand
PDFs, audio, videos, photos or documents: these require separate local import
and analysis engines. Existing private-book imports remain a separate opt-in
control. Revoke All files access at any time through Android Settings →
Special app access.

Google Play tightly restricts `MANAGE_EXTERNAL_STORAGE` to eligible app
categories and may reject apps that do not justify broad access. GitHub
distribution does not bypass Android's required user-grant dialog, and this
capability is not production-verified on an actual phone.

## Your private offline book library

Astra can now index your **own downloaded UTF-8 TXT, Markdown and unencrypted
EPUB** books on the phone. Open **Settings → Your private book library →
Import book** and select a file through Android's system document picker.
Book text stays under Astra's private no-backup storage; no book content,
search terms or citations are sent to any cloud AI or external website.

Astra divides imported books into bounded passages, builds an on-device
keyword index and retrieves the most relevant matches for a question. Type
`ask book Russian grammar` to explicitly query your imported books. For
ordinary free-form AI conversations, the owner can separately enable
**Use imported books in normal AI chat**, which is **off by default**. The
assistant labels retrieved passages with the source book title and passage
number so answers can be checked against the original.

The file importer enforces input and decompressed EPUB limits, rejects
unsupported formats and encrypted EPUB packages, bounds the on-disk index,
and refuses unsafe archive paths. Selecting a book is a local read-only
operation; the original downloaded file is not moved or deleted. The
**Remove imported books** button requires confirmation and deletes only
Astra's private indexed copy. If a large book exceeds indexing limits,
the app reports that the index is partial rather than falsely claiming
comprehensive coverage.

**Not a model fine-tuner:** Imported paragraphs are transient reference
context for Qwen3 and are not used to modify the GGUF's pretrained weights.
The separate opt-in neural topic adapter still only trains on eligible
conversation turns, not on the contents of imported books. The owner is
responsible for obtaining legal access to files and respecting their
licenses. Astra does not redistribute imported books, mirror copyrighted
libraries or bypass DRM. No paid inference endpoint is required.

## Adaptive on-device explanation depth

Astra's local Qwen3 inference no longer hard-limits every answer to 384
tokens. The token allowance now reflects the **request's desired depth**
and the **local model actually loaded**:

| Response request | Qwen3 1.7B | Qwen3 0.6B fallback |
| --- | ---: | ---: |
| Brief | 256 tokens | 192 tokens |
| Ordinary | 768 tokens | 448 tokens |
| Detailed tutorial or grounded book response | 1,152 tokens | 672 tokens |

An explicit request for brevity takes precedence, even when Astra
retrieves passages from the user's private book library or free Russian
reference material. These are strict caps enforced by the existing
native inference backend. **This improves maximum answer completeness,
not the underlying model's reasoning or linguistic understanding.**

No paid AI service, online model or new large download is required.
Longer responses can take more time and battery. The capability requires
Android build verification and real-device inference tests before it can
be considered stable.

## Real on-device language spot checks (experimental)

Under **Settings → Conversation quality checks**, the user can run a
short, reproducible four-part benchmark using the same native on-device
Qwen3 engine as normal Astra chat. This is more informative than the
existing two-word smoke test:

- Arithmetic: `19 + 23`, expecting `42`.
- Russian translation: `спасибо`, expecting *thank you* or *thanks*.
- **Two-turn context:** teach an invented codeword, then ask Astra to recall
  it in a follow-up using the same native model session.
- Russian grammar: name at least two of the six principal cases.

Astra reports each check's score, inference time, actual loaded model and
a short excerpt when a probe fails. The owner can stop a running test and
copy the result for bug reports. Fixed test prompts contain no private
notes, saved chats or passwords; no paid AI service or network request
is introduced. Previously enabled local knowledge may still affect a
response, so these are **end-to-end Astra checks**, not an isolated
scientific comparison of language-model weights.

**Limitations:** four deterministic checks cannot establish open-ended
understanding, comprehensive linguistic ability, continual training,
or freedom from hallucinations. For an offline proof, put the phone in
airplane mode before starting. Passing JVM tests or compiling the APK
is not equivalent to running these checks on a physical phone.

## Offline Russian learning course (23 lessons, 96 cards)

Even without Qwen3 loaded, Astra's deterministic tutor now offers **23
local lessons** covering greetings, everyday phrases, Cyrillic, numbers,
grammatical cases, verb conjugation/aspect, family, places, food, nature,
animals, the body, work, actions and colors. The
`russian` command still opens lesson one. Type `russian course` for the
syllabus or `next russian lesson` to advance. Direct commands such as
`russian alphabet`, `russian grammar`, `russian numbers` and
`russian verbs` open the relevant material.

Vocabulary practice now includes **96 distinct cards** rather than only six,
with early quiz order and existing correct-answer counts preserved.
Astra saves the current lesson and quiz progress locally. Manual JSON
backups include the additional lesson number, while older backups
without that field remain importable.

The lessons are original short examples and a structured curriculum,
not copyrighted textbooks or an LLM trained on external dictionaries.
Real Qwen conversation quality and pronunciation remain separate
physical-device tests.

## Preferred language for open-ended Qwen3 conversations

Select **Settings → Preferred AI response language** or type
`reply in Setswana`, `reply in French`, `reply in Japanese`,
`reply in Mandarin` or `reply in auto` in chat. The app offers
**automatic language matching, English, Russian and all eight added
language courses** as response preferences. This applies to open-ended
conversations generated by the installed local Qwen3 model; deterministic
calculations, notes, tasks and course cards keep their existing behavior.

The preference is saved locally and included in the manual backup.
Switching languages invalidates the previous native inference session
to avoid stale language instructions. The latest question remains in
the prompt even when earlier chat or private-book excerpts fill the
available context. The user can explicitly request a different answer
language in a particular question.

This is an instruction to the **existing** Qwen3 weights, not an
additional downloadable language model, a new translation engine,
fine-tuning, or guaranteed fluency. In particular, the compact 0.6B
model may be unreliable in lower-resource languages such as Setswana.
Use the existing Android speech language settings independently to
select installed recognition or synthesis packs. No cloud service,
paid inference, or new GB-scale APK download is required.

### Larger vocabulary without larger model downloads

The upgraded courses cover nine themed groups: people and family, places
and travel, food, nature, animals, body parts, work and time, everyday
verbs, and dates. All lessons and all flashcards work entirely on the
device without a GGUF model or mobile data. The Setswana word list was
cross-checked against the
[University of Botswana](https://www.ub.bw/study/international/learn-setswana),
[Botswana Tourism](https://botswanatourism.co.bw/index.php/language) and
[an independently published Setswana common-words glossary](https://bhala.app/languages/setswana).
Entries are concise vocabulary equivalents, not copied paragraphs
from books.

The expanded content adds only compact source strings rather than
pretrained model weights. Lesson numbers are real available content,
not an unlimited lesson counter; Astra should report the actual
maximum for each course if a larger lesson number is requested.

## Expand the curriculum without increasing the APK

The built-in nine-language catalogue contains **191 actual offline lessons**
and **768 vocabulary cards**. Astra now also accepts vocabulary supplied
by the owner, using **Settings → Expand offline language lessons → Import
extra vocabulary (TSV)**.

Choose one of the nine languages, then select a locally saved **UTF-8 TSV**
file with one foreign-language phrase and its English meaning per line:

```tsv
phrase	translation
Maru	Clouds
Pula	Rain
Ke kopa thuso	I need help
```

The header row is optional. The same format works with any supported
language; the owner must check spelling and translations before importing.
Astra does not translate or verify imported terms automatically. The
document picker can select a .tsv or .txt file that contains actual TAB
separators, **not spaces or commas**.

Each import is processed entirely on the device, accepts a bounded
6 MB file, checks every line before changing saved data, and skips repeated
terms (the first translation wins). Up to **10,000 custom cards per
language** can be stored privately, on top of the built-in deck.
Four-card lessons are then available automatically using existing
`setswana lesson 22`, `russian lesson 24`, etc. commands. No arbitrary
three-digit lesson ceiling prevents progress beyond lesson 999.

At the per-language imported limit, the theoretical **combined capacity**
is up to **90,768 vocabulary cards** across nine languages, corresponding
to up to **22,691 lessons**. The actual count is lower if terms duplicate
the built-in dictionary or one another. These are capacity limits,
**not** a claim that 90,768 reviewed translations ship in the APK.

Imported vocabulary resides in Astra's private **no-backup** app storage,
remains available after correctly signed app upgrades, and is deleted on
app uninstall or by the owner through the per-language removal control.
The manual JSON conversation backup deliberately does **not** include
these extra words. Instead, **Settings → Expand offline language lessons →
Export all language vocabulary (TSV)** saves every imported language deck
into one portable, checksum-verified UTF-8 file. Keep this file alongside
the ordinary JSON backup when changing devices or reinstalling.

Use **Restore all language vocabulary (TSV)** to load the combined archive
offline. It validates every row and the final SHA-256 integrity marker
before changing any saved vocabulary, rejects incomplete or modified files,
and merges cards without overwriting previous translations or resetting
lesson progress. An interrupted storage write can be retried safely;
restoration never deletes an existing deck. These exported TSV files are
not encrypted, so store them privately. Existing progress counters and
built-in card indices are retained, but manually clearing a custom deck
removes its custom questions.

Nothing is uploaded or used to train Qwen3. This feature needs no paid
model API, network connection, or new large model download.

## Multilingual learning without paid services

In addition to the expanded **96-card Russian course**, Astra contains
eight more entirely local starter courses:

| Language | Chat command | Android speech locale |
| --- | --- | --- |
| Setswana (Botswana) | `learn setswana` | `tn-BW` |
| French | `learn french` | `fr-FR` |
| Spanish | `learn spanish` | `es-ES` |
| Portuguese | `learn portuguese` | `pt-PT` |
| German | `learn german` | `de-DE` |
| Swahili | `learn swahili` | `sw-KE` |
| Japanese | `learn japanese` | `ja-JP` |
| Mandarin Chinese | `learn mandarin` | `zh-CN` |

Each additional language now includes **21 themed lessons and 84 distinct
vocabulary questions**, resulting in **672 cards across the eight added
languages, plus 96 Russian cards = 768 cards total**. That is **191
available offline lessons** across the nine courses. The original 12 cards
in each language and original 30 Russian cards retain their positions so
existing quiz progress remains valid. The commands `languages`, `<language> quiz`,
`<language> progress`, `<language> lesson 21`,
`next language lesson`, `language progress`,
`answer <English translation>`, and `skip language question` work
directly in chat with no model or internet requirement. Setswana's starter
greetings and usage were cross-checked against the
[University of Botswana's learning guide](https://www.ub.bw/study/international/learn-setswana)
and [Botswana Tourism](https://botswanatourism.co.bw/index.php/language).
The packs are original short teaching examples, not copies of entire books
or general-purpose language models.

Correct answers, current quiz position and last lesson are stored
**independently per language**. The existing manual JSON backup includes
the new progress keys while old backups remain importable. No chat
transcript is uploaded to train a multilingual model.

**Voice is separate from these lessons.** Settings → Recognition language
now exposes eight additional BCP-47 locales. A locale entry does not install
a speech recognizer or TTS voice; Android's available language packs and the
selected speech engine determine actual support. The app prefers a matching
offline TTS voice, and reports missing data rather than pronouncing a
different language using an English voice. On devices without a particular
offline pack, that language still works in text-only lessons and quizzes.
Network-capable speech fallback remains subject to existing user settings.

Astra's on-device Qwen3 engine may answer open-ended questions in other
languages, but quality varies by language and device. Adding deterministic
courses or speech tags is **not** Qwen3 weight training or verified full
multilingual fluency.

## Working capabilities

- Chat interface with **multiple persistent conversations**, an animated compact navigation dock, notes, tasks and completion tracking.
- **Mission Control**: progress summaries, explicit task priorities and next-task suggestions based on saved information.
- **Local memory recall**: search saved notes/tasks with commands such as `recall bond duration`. This is lexical retrieval, not generative AI. Message text can also be searched privately through the chat menus.
- Mathematics, finance calculations, unit conversions, text utilities and random tools.
- Shopping lists, habits, daily overviews and introductory Russian lessons/quizzes.
- Android speech recognition and text-to-speech, depending on installed system services and language packs.
- Explicit opt-in Wikipedia research, which sends the requested public topic to Wikipedia.
- Local JSON backup and confirmed restore under **Settings → Data backup & recovery**. Backups are **not encrypted** and should be kept private. Automatic Android cloud/device-transfer backups are intentionally disabled for conversation privacy; use manual export for migration.
- Dark adaptive launcher artwork, animated Astra branding and a live command-deck HUD.
- **Fully offline conversational AI** with Qwen3 0.6B Q4_K_M and native llama.cpp. A full APK includes the model; a small APK can import the exact same weights **once** from a saved GGUF or earlier Astra APK/ZIP, avoiding repeated cellular downloads.
- Bond Analytics Lab: fixed-coupon price, current yield, modified and Macaulay duration, convexity and DV01.

**Important:** Astra's tasks, notes, calculators and local commands remain deterministic. Unhandled free-form questions now go to a **model running on the Android phone**. A small model may hallucinate or make calculation errors; Astra does not provide live financial prices or unrestricted device automation.

## Isolated conversation restoration after native reloads (v0.12.1)

When native Qwen3 reloads after an interrupted response, switching conversations, or a recreated Activity, Astra now reconstructs a **short, labelled excerpt of the previous chat as untrusted user-level reference text**. Previous messages are no longer copied into the higher-priority system instruction, where a pasted instruction or malicious quoted assistant reply might otherwise be promoted to model policy.

The system instruction remains a **fixed offline assistant policy** with an explicit boundary between the current request and earlier quoted data. Restored excerpts preserve roles and prefer recent turns, escape quotes and line breaks, and share a strict ~3,000-character budget with the new question. The current question always takes priority; long questions omit prior context rather than truncating the user's request. Ordinary follow-up requests in an already loaded session rely on native chat history without needlessly duplicating earlier messages.

This is a **prompt-boundary and context-quality improvement**, not a guarantee that a 0.6B model cannot be confused by malicious text. Eight new JVM tests cover system isolation, exact question preservation, oversized chat history, role filtering and multiline prompt spoofing. Both FULL and LITE use the same implementation and unchanged model weights.

## Offline follow-up continuity at the generation limit (v0.12.0)

The pinned llama.cpp Android JNI binding previously returned from generation when its positional output limit was reached **without adding the assistant's final reply to the native chat template history**. That could cause later questions to lose relevant context about Astra's own preceding answer. A context-window shift could also move the native token position backwards and silently extend the number of generated tokens beyond the requested budget.

The native runtime now tracks the **remaining assistant tokens directly**, independent of context-window shifts, and records a bounded answer in the local chat history when it reaches its token limit. The normal end-of-generation path is preserved, and cancellation still invalidates incomplete model state. Six new deterministic regression tests verify both completion paths, count-based stopping, source drift checks, and idempotent patching. FULL and LITE compile the same patched native engine.

This fixes the implementation rather than establishing real-device conversational quality. Before stable release, repeated follow-up questions and long chats must be tested on an ARM64 phone in airplane mode. The low-data APK remains available without model weights.

## Accurate native token generation budget (v0.11.9)

The pinned llama.cpp Android JNI binding previously **double-counted user prompt tokens** when calculating the response stop position: it added the prompt length once to the current context position and again to the generation limit. On a long question, a request for 384 output tokens could produce significantly more, slowing responses and consuming unnecessary battery and memory.

A fail-closed build-time patch now limits generation to the requested **assistant** token count, and updates the native context position using only the prompt tokens actually decoded after any size truncation. FULL and LITE both use the corrected native engine. Six regression tests cover idempotence, prompt truncation and detecting upstream source drift. This is a code-level fix: on-device timing and battery improvements still require measurement on a phone.

## Deterministic finance arithmetic in natural chat (v0.11.8)

Astra can answer ordinary investment growth questions without guessing through its small language model. It parses a **single lump-sum amount, nominal annual rate, duration and compounding frequency**, then uses deterministic numerical formulas and displays both the result and its assumptions:

- `What will P50,000 grow to at 10% over 7 years?` → **P97,435.86**, assuming annual compounding.
- `Calculate compound interest on P10,000 at 10% for 2 years compounded monthly` → monthly-compounded final value.
- `How much should I invest today to reach P100,000 at 10% in 7 years?` → **P51,315.81** present value.
- `How long to double P10,000 at 10% compounded monthly?` → approximately **6.96 years**.

The calculator recognises BWP, rand, dollars, pounds and euro amounts, without converting between currencies. It rejects ambiguous inputs and periodic contributions rather than treating them as lump-sum calculations. These projections exclude taxes, fees, additional cash flows and inflation, and do not guarantee realised returns.

These calculations and ten new regression tests require no Qwen3 weights, account or network connection. The FULL and LITE distributions and native prompt-error recovery remain unchanged.

## Recover after native prompt-processing errors (v0.11.7)

The pinned llama.cpp Android binding previously **returned silently on a native prompt-processing failure** without resetting its `ProcessingUserPrompt` state. Subsequent chat requests could time out waiting for an inference engine that never became ready. The build now patches that path to throw a structured error and enter `Error` state.

When clearing an error, the native wrapper now unloads a fully prepared model context and clears its conversation cache before reloading. It avoids calling `unload()` when preparation never completed. Both FULL and LITE include this fix without adding GGUF weights to LITE. The patch fails closed on source drift and has automated regression tests for state transition, cleanup, idempotence and error paths.

**Limitations:** A native crash or partially initialised allocation may still require a full app restart. Phone-based failures and inference latency remain to be verified; this does not make the application a stable release.

## No private prompts or generated tokens in Android inference logs (v0.11.6)

The pinned llama.cpp Android native binding previously logged raw system prompts, user prompts, assistant messages, individual tokens, and model file paths during diagnostics. Those logs were not sent to an AI server, but they should not be exposed through Android diagnostic logs.

The build now **redacts those sensitive log sites in both the C++ JNI code and upstream Kotlin wrapper before compiling**. Ordinary diagnostics retain generic status and error categories. The hardening is deterministic, idempotent and **fails the build** if the pinned upstream logging statements drift or could not be redacted; the FULL, LITE and protected owner-signed workflows verify it. Python regression tests also check fail-closed behaviour.

This protects the known llama.cpp Android binding log statements; it is **not a blanket guarantee** that every Android/OS logger, crash report or installed app is incapable of logging information. No network endpoint or remote analytics is introduced.

## Resilient native AI and safe model replacement (v0.11.5)

The on-device llama.cpp session now **invalidates native conversation state after a cancelled request, JNI error, startup failure or empty generation**. The next request reloads its local model rather than reusing a potentially incomplete response in the native KV-cache. This avoids context contamination following a Stop action; model weights are not downloaded again.

Importing a replacement GGUF now preserves the previous copy until the new file has passed its size and SHA-256 verification. Replacements use a recoverable backup rename: if Android kills the process between renames, Astra restores the previous private model file on the next launch. FULL-bundled model preparation uses the same mechanism. Additional JVM tests cover cancellation, reloading, replacement and interruption recovery.

**This is crash-recovery engineering, not proof of physical-device token generation.** The JNI engine, memory use, Stop latency and upgrade behaviour still require phone testing. App size stays small for LITE.

## Ask your private memory, with original evidence (v0.11.4)

Astra can now produce **source-backed answers from your own saved information** without an internet connection or separate PC. This is **explicitly opt-in**: type `ask my memory why did I choose bonds?` or `analyse my notes about risk`. Alternatively tap the Astra icon → **Search all local memory** → **Ask with evidence**. Regular chat and Wikipedia searches do not silently load saved notes or archived conversations.

The retrieval engine ranks up to four short excerpts from your notes, open tasks and **only your own** messages in current or archived chats. Assistant-generated replies are excluded. If nothing relevant is found, Astra says so rather than fabricating a personal memory. If Qwen3 is installed, it can attempt an interpretation using **only those selected excerpts**, through an isolated native on-phone inference context. The final response attaches original labelled source excerpts [S1]–[S4] for verification. If no model is installed, the excerpts remain available without a model download.

**Limitations:** Retrieval is lexical, not neural semantic search. The small 0.6B model can misunderstand evidence or produce inaccurate inferences; attached excerpts are the verification reference, not guaranteed model citations. Local storage and existing conversation backups are unchanged. No new model weights or network dependencies were added, so the LITE APK remains the low-data update option.

## Chat that stays readable while AI is generating (v0.11.3)

Astra now follows the latest reply only until you deliberately scroll upward.
During on-device Qwen3 streaming, you can review older messages without Astra
dragging you back to the bottom on every token batch. A compact **↓ Latest
messages** action appears only while you are reading older chat content.
Tapping it, sending another message, or manually scrolling to the end resumes
following. Choosing a **Find in chat** search result pauses auto-follow too.
This change runs entirely within the Android UI, needs no network, and
does not alter existing saved conversations or model weights.

## Private, source-grounded recall across chats (v0.11.2)

Astra can now answer explicit questions about **what you previously wrote** without using mobile data, the 462 MiB model, or any external AI service. Its memory search indexes saved notes, open tasks, user-written messages in the active chat, and user-written messages from archived conversations. AI-generated replies are **excluded** so Astra does not misrepresent model output as something you said.

Try `memory overview`, `recall bond duration`, `what did I say about dividends?`, or `find my notes about risk`. Results include the **original source** (note number, task number, active message or archived chat title) and a short excerpt. If nothing matches, Astra explicitly states no evidence was found instead of inventing a memory.

To search without typing commands, tap **Astra icon → Search all local memory**. The search runs in a background coroutine, with bounded result sizes, and never transmits user data. It is a **literal token-based retrieval engine**, not semantic embeddings; wording differences may require a refined search. The full text remains in your private local backup/history. A user must explicitly initiate retrieval; there is no automatic model-memory injection.

## Offline finance education and verified AI diagnostics

**v0.11.1** adds deterministic offline finance explanations for common natural-language questions, including `explain compound interest`, bond duration, convexity, yield to maturity, diversification, present value, annuities and dividend yield. These are available **without Qwen3** or mobile data. Unsupported open-ended questions still use the locally installed Qwen3 model.

The **Test on-device AI** control now requires the model to generate the expected `ASTRA OFFLINE` phrase. Blank output, fallback errors and incorrect responses are never recorded as a successful test. The shareable diagnostic report distinguishes VERIFIED, UNVERIFIED and FAILED statuses. This is a local challenge test, **not** a remote verification or a guarantee of model accuracy.

## Avoid downloading a 539 MiB APK after every change

**Astra v0.12.1** has two installable Android options built automatically on GitHub:

| Variant | What you download | Best for |
| --- | --- | --- |
| **LITE** | Android UI, tasks and llama.cpp native inference, **without** GGUF model weights (enforced under 150 MiB) | Regular app updates over mobile data. |
| **FULL** | The same Android app with Qwen3 Q4_K_M included (~539 MiB) | First installation when no model file is available. |

Open [GitHub Actions](https://github.com/molosiwabakang51-hash/Astra/actions), choose a **successful v0.12.1 build**, and under Artifacts select **`Astra-v0.12.1-LITE-Offline-APK`** for the much smaller download. The full alternative is **`Astra-v0.12.1-FULL-Offline-APK`**. Extract the downloaded artifact ZIP; inside is the versioned APK. Both have the same app ID and UI but do **not** have the same bundled assets.

**Import the model once without using internet data:** In the LITE app, tap **Astra icon → Settings → Import offline model once**. Choose a GGUF file already saved on the phone, a **previously downloaded large Astra APK**, or its original **GitHub artifact ZIP containing that APK**. Astra extracts the Qwen3 weights locally, checks their exact SHA-256, and stores the model privately. The original selected file is not modified. Once imported, airplane-mode chat works entirely on-device, subject to real-phone performance.

If you already downloaded a 500+ MB Astra APK, **retain the APK or artifact ZIP in Downloads** instead of downloading another copy of Qwen3. The import feature can reuse it directly. If you do not have any old model source saved, use Wi-Fi once to get the GGUF or FULL variant rather than repeatedly using cellular bundles.

**Signing is essential for preserving data and the imported model across app updates.** GitHub Actions test/debug APKs often use **different signing keys** across workflow runs. Android will not install one as an update over a differently signed version. Export your chat/task backup **before uninstalling** a debug build, and retain the GGUF/APK/ZIP in Downloads for offline re-import. After a one-time migration, use the **protected owner-signed LITE release workflow** with the same permanent keystore for every new version; the signed installers are published to [GitHub Releases](https://github.com/molosiwabakang51-hash/Astra/releases) and support in-place upgrades. This workflow cannot publish until the owner privately configures the signing environment in [release-signing.md](docs/release-signing.md).

**Important limitation:** GitHub Pages serves static websites. It cannot execute this native Android APK or host a full llama.cpp inference server. GitHub Actions builds the app, GitHub Releases distributes signed updates, and AI inference runs on the Android phone.

### Confirming the installed version

The chat header displays `v0.12.1 · OFFLINE AI` (or the full signed release build version, such as `0.12.1.123`). The Astra icon opens the menu, the optional mission deck starts hidden and there is no old permanent bottom navigation bar. Settings clearly distinguishes a model **bundled in FULL**, **imported on the phone**, or **missing on LITE**. A missing model does not silently revert to unreliable canned answers; it prompts local import.

## Offline model and low-data builds

**v0.11.0 data-saving release:** The new LITE APK carries all Kotlin UI and llama.cpp native inference code but excludes the ~462 MiB model file. The model is imported once from Downloads, a prior APK or a GitHub artifact ZIP. GitHub Actions checks that model weights are **absent** from LITE and **present/verified** in FULL, and reports both download sizes. The owner-signed workflow can publish persistent LITE releases with the verified certificate.

**v0.10.4 install clarity:** The chat header displays the installed build version and offline AI identity. GitHub publishes one versioned APK file per workflow, and CI rejects a version or package-name mismatch. A regression guard ensures the icon-tap navigation, initially collapsed mission deck, and on-device model fallback remain implemented.

**v0.10.3 diagnostics:** Settings → Offline AI now offers **Copy diagnostics** and **Share report**. Reports are generated locally and show device model, Android version, supported ABIs, free app storage, available RAM, the bundled/extracted model sizes and the offline self-test result. They exclude conversations, tasks, notes, device identifiers, secrets, access tokens and raw model output; there is no automatic upload. Review before sharing.

**v0.10.2 first-run resilience:** When a model is first verified, extracted or loaded, Astra reports the current phase in chat. The Stop control now cancels disk copying/checksumming cooperatively, cleans temporary files, and avoids leaving a stalled model preparation. Any saved "THINKING" placeholder left by an Android process restart is displayed as an interrupted response.

**v0.10.1 user experience:** Text generation now appears progressively in the chat bubble, and the Send button becomes a **Stop** control during inference. Stopping a response preserves the text already generated. Switching or clearing a conversation cancels its inference request so an old response cannot overwrite a different chat.


Qwen3 0.6B (Q4_K_M) occupies roughly **462 MiB**. In the FULL variant, weights are downloaded and verified on the **build machine**, then packaged inside the APK. In the LITE variant, **no weights are packaged**; the owner must import a previously saved GGUF/APK/ZIP (or obtain model weights just once). **The app never downloads a language model automatically**.

- **Model:** QuantFactory's Qwen3-0.6B Q4_K_M GGUF on Hugging Face: https://huggingface.co/QuantFactory/Qwen3-0.6B-GGUF. SHA-256: `7af3fdf842f87b24672f8a7f1dd50404043f0bfb71093ff91c31d2b49df4631d`. Base Qwen3 model license: Apache 2.0.
- **Engine:** Open-source llama.cpp's Android JNI binding, pinned to `b12248` (`3837bf0`). Documentation: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md.
- **Compatibility:** ARM64 (`arm64-v8a`) devices running Android 13 / API 33 or newer. App ID remains `com.astra.agent`. The native binding raises the former API 26 minimum. RAM usage and token speed vary by device.
- **First message:** FULL verifies and extracts its bundled GGUF once. LITE uses the same private verified model file after the owner imports it. Keep ~665 MB free for import and retain your selected GGUF/APK/ZIP outside the app in case you must uninstall a differently signed debug build. A same-signature upgrade preserves the private model and chats.
- **Privacy:** The model runs on the same phone even in airplane mode. Explicit Wikipedia research and Android's optional speech services are separate, and may use network access. No AI chat text is transmitted to an external model service.
- **Quality:** A 0.6B model is substantially less capable than large hosted models; local command handling and deterministic calculators retain priority.
- **On-phone self-test:** Open the Astra-logo menu → Settings → **Test on-device AI**. With airplane mode enabled, the test prompts the packaged Qwen3 model without using the network and shows a response or the actual error and elapsed time. This is a practical device-level acceptance check, not a benchmark.
- **Open-source notices:** Model Apache 2.0 license and native inference MIT/dependency licenses are bundled inside the APK under `assets/open_source_licenses/`.


### Reproducible Android builds

On the **build machine only**, install Java 17, Android SDK platform 36, NDK `29.0.13113456`, CMake `3.31.6`, Gradle `8.13`, Git and Python 3. Run:

```sh
python3 scripts/prepare_offline_android.py
cd android
gradle --no-daemon testDebugUnitTest assembleDebug
cd ..
python3 scripts/verify_offline_apk.py
```

For a **small APK** build, run the following instead (requires the same Android toolchain):

```sh
python3 scripts/prepare_offline_android.py --lite
cd android
ASTRA_BUNDLE_MODEL=false gradle --no-daemon testDebugUnitTest assembleDebug
cd ..
python3 scripts/verify_lite_apk.py
```

GitHub Actions performs both builds and verifies their contents. The GGUF stays **outside Git history**. FULL includes verified GGUF in the APK; LITE explicitly rejects GGUF inside the APK, includes all native inference libraries and remains below the size gate. Neither build uploads private chat data.

The previous `gateway/` server remains as historical code but is **not invoked by Astra v0.10.0**. It is not required for offline conversation.
## Builds

The workflow at `.github/workflows/android.yml` builds **FULL and LITE** on pushes, PRs, manual dispatch and weekly schedule. Successful [GitHub Actions runs](https://github.com/molosiwabakang51-hash/Astra/actions) publish separate versioned APK artifacts and size reports. LITE must be under 150 MiB and exclude the model, while FULL must pass Qwen3 checksum verification.

The Android project is in `android/`, with application ID `com.astra.agent`, Android API 33 minimum, Java 17 and Gradle 8.13 on CI.

The **FULL debug APK** includes model weights and is ~539 MiB. The **LITE debug APK** contains only the app plus native inference and must be under 150 MiB. Both debug APKs are for testing only. GitHub runners may use different debug signing keys, preventing in-place updates. **Before uninstalling any older debug build, export your data.** An owner-signed release workflow is now provided but cannot produce durable APKs until the owner privately configures a permanent keystore and signing secrets. See **[Owner-signed release setup](docs/release-signing.md)**. Its first install over a debug build still requires export → uninstall → install → restore; later releases signed by the same owner key can update without uninstalling. Stable private release signing and device verification are tracked in [issue #14](https://github.com/molosiwabakang51-hash/Astra/issues/14). Never put keystores or API keys in this public repository.

## Conversation management and privacy

- Tap the **ASTRA** logo for navigation and all chat options in one menu. Mission Control is shown only when requested; microphone dictation is beside the message field, so the chat takes more screen space.
- In the Astra-logo menu, choose **Find in chat** to search the current conversation locally and jump directly to matching messages.
- In the Astra-logo menu, choose **New conversation** to archive the current thread without deleting it, **Conversations** to find and switch between previous threads, or **Rename current chat** to give the active thread a custom title. In the conversation picker, search **saved chat titles and message text** with local-only matching excerpts, rename saved chats, or individually delete unwanted conversations with confirmation.
- Typed or spoken `new chat` / `new conversation` now opens another conversation **without deleting the old one**. `switch chat` opens the conversation picker. `clear chat` still requires confirmation before it deletes the active history.
- Astra keeps local conversations in app-private storage. Automatic Android system backup and device-transfer restore are disabled to reduce unintended copies of private data.
- Astra's current local conversation archive supports **up to 40 saved threads**. When it is full, Astra does **not** delete old chats automatically. It shows a storage-management prompt instead. Switching between existing chats continues to work at capacity without dropping an unrelated conversation.
- **Before uninstalling or changing devices**, export a JSON backup through Settings, keep it somewhere private, and restore it manually after installing Astra. Deleting the app without exporting can permanently lose its local notes, chats, and tasks.

## Quick command examples

- `Mission control`, `What's next?`, `Set task 2 priority high`
- `Hey Astra, please show me my tasks`
- `Remember that bond prices usually move inversely to yields`
- `What did I say about bond duration?`
- `Teach me Russian`, `Russian quiz`
- `Plan my day`, `Add milk to shopping list`
- `Calculate 12 * 5`, `Compound interest 10000 10 2 12`
- `Research the solar system`, `Help`

See the repository [issues](https://github.com/molosiwabakang51-hash/Astra/issues) for outstanding model integration, quality testing, and interface development.
