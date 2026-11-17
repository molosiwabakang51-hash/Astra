# Astra visual design playbook
**Research and working standard — 18 November 2026**

This is a reusable **graphic-design discipline**, not a moodboard, one-off UI paint job, a new font download, or a claim that an AI model has permanently retrained itself. It applies to app UI, diagrams, marketing graphics, posters, study cards, presentations, icons and data visualization. It accompanies the owner's requirements ledger **#166** and the ongoing Aurora design PR. Never represent a design sketch or unmerged Compose code as a finished, installed app.

## 1. Design reasoning before drawing
For every graphic, write a one-sentence **message**, identify the **audience** and **context**, choose **one main action or takeaway**, and specify the medium/viewport. Define a measurable success test. Compare at least three concepts (quiet, expressive, editorial) *before* selecting a visual direction; don't choose the busiest composition by default.

Create focal hierarchy: **primary / secondary / tertiary**. Arrange components on a grid; use scale, weight, placement, contrast, whitespace and containment to indicate importance. Make unrelated components visually distinct and group related things by proximity and consistent styling (Gestalt). Avoid showing every function at equal prominence.

Composition audit:
- Read the design at thumbnail size or while squinting: is the most important element obvious in under three seconds?
- Follow a consistent baseline/grid; align edges deliberately rather than visually approximating positions.
- Check visual balance (symmetrical or intentional asymmetrical), margins, rhythm and negative space.
- Prevent one screen from having multiple competing bright cards, glows, badges, headlines or motion effects.
- Present meaningful content first; ornamental illustration must not obscure controls or copy.

Research basis: Adobe's graphic-design fundamentals; Nielsen Norman Group's visual hierarchy, Gestalt, balance, contrast and scale work; IBM Carbon's design tokens.

## 2. Typography is part of the interface architecture
Select a robust type family with sufficient glyphs for English, Cyrillic, Setswana and French; retain Android's system fallback for missing scripts. Never depend on a font file or decorative display type that is not bundled and licensed. Use **one body family** and at most one contrasting display treatment for exceptional identity work. Test real Cyrillic, diacritics, numbers and mathematical notation.

Working mobile scale, to refine after physical-device tests:
| Role | Target | Weight | Use |
| --- | --- | --- | --- |
| Display/hero | 25–30 sp | SemiBold/Bold | One short new-chat introduction only |
| Section heading | 20–24 sp | SemiBold | Study, privacy, notes, settings sections |
| Card heading | 16–18 sp | SemiBold | Subjects, actions, important cards |
| Chat and body | 16–17 sp | Regular/Medium | Conversational text and teaching material |
| Supporting text | 14–15 sp | Medium | Descriptions, notes, timestamps if consequential |
| Eyebrow/metadata | 12–13 sp | Medium/SemiBold | Clearly secondary; not the only route to a function |

Use font size in **sp** and let font scaling adjust. Body leading around **1.4–1.55x** the body font is a starting point, not a fixed standard. Avoid thin weights for core information, all-uppercase long paragraphs, dense letter spacing in body copy, text over unshielded gradients, and ambiguous text clipping. Keep labels short, readable and descriptive.

Check whether a "stylish" font hurts word recognition at 360dp width, 200% Android font scale, and in Russian/French. A typeface with high x-height, clear open counters and sensible stroke weight is usually more valuable than novelty. Typography includes the hierarchy of **spacing around text**, not only font choice.

Research basis: Apple Human Interface Guidelines / Typography; IBM Carbon typography; USWDS Typography; Nielsen Norman Group visual hierarchy.

## 3. Color is a semantic system
Choose palette roles before individual hex values: canvas, raised surface, input surface, primary text, secondary text, focus/action, tertiary action, warning, error, success, selection and disabled. The identity palette should remain consistent across every screen. Avoid using color as the only status indicator.

**Astra Aurora proposal (source of truth: \`AstraAurora.kt\` in the active branch):**
| Role | Hex | Purpose |
| --- | --- | --- |
| Background | \`#080C18\` | Quiet near-black blue canvas |
| Surface | \`#121C2D\` | Assistant content and inputs |
| Raised surface | \`#1B2940\` | Menus and support cards |
| Text | \`#EAF5FF\` | High-contrast main information |
| Secondary text | \`#B3C5DD\` | Still readable, never intentionally faint |
| Cyan | \`#7CE6F6\` | Primary interactive accent |
| Violet | \`#B8A4FF\` | Secondary identity highlight |
| Mint | \`#9CF5D4\` | Verified local state, selectively |

Using the WCAG 2 relative-luminance formula, representative **opaque foreground-on-opaque-background** ratios are:
- Main \`#EAF5FF\` on surface \`#121C2D\`: **15.45:1**
- Secondary \`#B3C5DD\` on raised \`#1B2940\`: **8.31:1**
- Cyan \`#7CE6F6\` on surface \`#121C2D\`: **11.80:1**
- Violet \`#B8A4FF\` on raised \`#1B2940\`: **6.81:1**

These calculated pairs **do not prove the entire app passes contrast**. Gradients, alpha blending, disabled states, text fields, tiny icon strokes and overlays require independent checks at the actual rendered surface. Treat **4.5:1 for normal text**, **3:1 for qualifying large text**, and **3:1 for essential non-text controls** as minimum WCAG-aligned gates, preferably exceed them. Verify in grayscale and on device. Contrast must survive both bright and dim phone screens.

Use glow only for a focal hero or meaningful active status; never behind lengthy paragraphs. Reserve saturated accents for actions and restrained identity moments; most surfaces should be quiet. Avoid "neon everywhere."

Research basis: W3C WCAG 2.2; Apple HIG Color; IBM Carbon color semantics.

## 4. Shape, grid, spacing and surfaces
Base spacing rhythm: **4, 8, 12, 16, 24, 32dp**. Reuse fixed tokens rather than inventing margins on each screen. Set outer chat margins around 12–16dp, with 12–16dp internal card padding; use actual previews for small screens. Prefer a few corner-radius families (12, 18, 24–28dp) instead of a different roundness per element. Use borders to separate related surfaces subtly; keep shadows/glow restrained.

Design for a 360dp-wide Android screen *first*, then expand. No important control should require horizontal scrolling unless the content is explicitly a carousel. The message composer is a persistent anchor and should remain easy to reach. Content needs safe inset handling around the status bar, navigation gestures and keyboard. Avoid giant fixed-height brand panels that crowd the conversation.

Touch targets: design at least **48 × 48dp** for interactive elements and expose content descriptions for icon-only actions. Larger icons are not the same as larger accessible touch targets. UI should not lose function at text magnification.

Research basis: Android Developer accessibility; Carbon tokens; Gestalt/proximity work.

## 5. Motion and graphic illustration
Motion must communicate: (a) entering/returning from a view, (b) input and system status, (c) confirming an action, or (d) pointing to the next step. Keep animations subtle and interruptible; avoid continuously moving headers and large fields behind a chat. The "orb" is **Astra identity**, not evidence the AI has loaded, is listening, or is thinking.

Proposed visible states:
- Idle: mostly static, minimal breath/ambient light only on welcome.
- Listening: explicit microphone indicator and controlled pulse, only after microphone permission.
- Generating: separate factual progress indicator; never falsely imply internet or system readiness.
- Speaking: brief state animation synchronized with the *actual* TTS playback status if available.
- Error/locked: explicit state and explanatory text; visuals alone are insufficient.

Respect Android "remove animations" preference. Favor vector/canvas shapes and gradient composables over downloaded raster wallpaper, battery-heavy blur/shader passes or bundled high-res images. Verify 60Hz/low-RAM responsiveness on the owner's phone; don't assert 60fps without measurements.

Research basis: Google Material 3 Expressive research and Android accessibility guidance.

## 6. Icons and imagery
Build a coherent icon family: identical optical weight, consistent caps/joins, bounded size, common geometry and active/inactive states. A 24dp icon drawn within a ≥48dp target is better than a giant glyph. Do not mix unrelated emoji, Unicode pictograms, filled clip art and thin outline icons in the same navigation cluster. Give every icon an accessible text label or content description.

For any future static graphic or illustration:
1. Define a visual concept with a message and a recognizable silhouette.
2. Explore composition thumbnails before refining textures.
3. Choose a limited visual language (geometry, lighting, edge/shadow behavior, palette).
4. Ensure readable text is added in layout software when exact wording and typography matter; do not trust text embedded in generated imagery blindly.
5. Check high-DPI rendering, dark/light backgrounds, mobile cropping and attribution/licensing.
6. Never imitate another brand's trademark, screenshot, or copyrighted imagery as though it belongs to Astra.

Use imagery **only where it teaches, establishes identity or explains**. A finance formula, progress timeline or chart can be more effective than abstract particles.

## 7. IA and interaction — expressive does not mean cluttered
The owner requires **Study to open a subject chooser**. The navigation sequence is **Study → subject (Finance, Mathematics, English, Day Trading, Russian, French, etc.) → topic → lesson/practice**. Study must never automatically submit \`Teach me finance\` on the first tap. Show clearly which material is available deterministically offline vs requires an imported local Qwen model.

Keep conversation itself the primary screen. Primary action is "Send" (or "Stop" while streaming). Secondary controls (memory, tools, settings, archive) live behind an obvious, accessible menu. Preserve existing chat history, copy/share, and voice/microphone controls while redesigning. Never replace functional interaction with an attractive inert mockup.

Current user feedback points: fonts too thin; labels fade into lighter backgrounds; too much content in header/dock; Study is misrouted. These are **release-blocking UI defects** until tested.

User-reported UI complaints in public AI apps include navigation buried too deeply, inconsistent UI versions and chat continuity problems; these are qualitative signals, not representative polling.

## 8. Graphic-design checklist for every deliverable
**Before starting:** brief, target audience, output medium, key action, brand identity, constraints, source/licensing provenance.

**Prototype:** at least three visual approaches; one dominant focal point; design tokens defined; clear alignment and spacing; copy fit; complete user journey with back navigation.

**Check:** contrast and text scaling; low-brightness visibility; grayscale; RTL/Cyrillic/diacritics when relevant; legible secondary text; 48dp targets; keyboard/safe insets; dark and light scenarios; TalkBack and disabled animation; input/recording indicators; no misleading visuals; no nonfunctional controls.

**Ship:** link code commit and CI result; actual handset screenshot; report open issues. "Looks good in a generated mockup" is NOT equivalent to "works on the device."

## 9. Iteration scorecard (diagnostic, not a validated user rating)
Rate each category 0–5 from observed evidence:
- **Hierarchy:** first-read focal point, clear next action.
- **Readability:** visible labels, text scaling and contrast.
- **Cohesion:** consistent fonts, shapes, spacing and colors.
- **Function:** navigation and controls behave as promised.
- **Delight:** expressive identity without clutter or battery cost.
- **Robustness:** compact screens, error states, low-end phones, reduced motion.
No claim of "8.5/10 AI intelligence" is justified from design score alone. Note concrete failures and correct them in the next PR.

## 10. Reference library reviewed for this playbook
**Professional and research-based**
- Adobe — Graphic design guide: https://www.adobe.com/uk/creativecloud/design/discover/what-is-graphic-design.html
- Adobe — Basic graphic design principles: https://helpx.adobe.com/ph_fil/express/how-to/graphic-design-basics.html
- Adobe — Layout basics: https://www.adobe.com/learn/illustrator/web/layout-basics
- NNGroup — Five visual-design principles: https://www.nngroup.com/articles/principles-visual-design/
- NNGroup — Why a design looks good: https://www.nngroup.com/articles/why-does-design-look-good/
- NNGroup — Gestalt proximity: https://www.nngroup.com/articles/gestalt-proximity/
- NNGroup — Legibility/readability: https://www.nngroup.com/articles/legibility-readability-comprehension/
- Google Design — Material 3 Expressive research (46 studies / 18,000+ participants): https://design.google/library/expressive-material-design-google-research
- Google Research — CHI 2026 Expressive Design: https://research.google/pubs/usability-hasnt-peaked-exploring-how-expressive-design-overcomes-the-usability-plateau/
- W3C — WCAG 2.2: https://www.w3.org/TR/WCAG22/
- Android Developers — Accessibility apps: https://developer.android.com/guide/topics/ui/accessibility/apps
- Apple — HIG Typography: https://developer.apple.com/design/human-interface-guidelines/typography
- Apple — HIG Color: https://developer.apple.com/design/human-interface-guidelines/color
- IBM Carbon — Typography: https://www.carbondesignsystem.com/building-blocks/foundations/typography/overview
- IBM Carbon — Color: https://www.carbondesignsystem.com/building-blocks/foundations/color/guidelines
- USWDS — Typography: https://designsystem.digital.gov/components/typography/

**Qualitative user feedback, not a systematic representative sample**
- Reddit 2026 complaints about buried chat navigation: https://www.reddit.com/r/ChatGPT/comments/1wtzwn9/updated_gui_navigation_sucks_hard/
- Reddit 2026 complaint about inconsistent interface: https://www.reddit.com/r/ChatGPT/comments/1s46ft7/chatgpt_ios_ui_is_a_complete_mess_for_me_mixed/
- Reddit 2026 complaint about fractured app modes: https://www.reddit.com/r/ChatGPT/comments/1wsbj98/the_chatgpt_appweb_experience/

This document is a **reasoned synthesis with project-specific design choices**, not a claim that these organizations endorse Astra's specific palette or typography sizes. Last reviewed 2026-11-18.
