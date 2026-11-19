package com.astra.agent

/** Original offline English research, communication, language and literacy workshops. */
internal object AstraMasterEnglish {
    val units: List<AstraMasterclass.Unit> = listOf(
        AstraMasterclass.Unit(
            "Clause structure and sentence clarity", "Intermediate",
            "A sentence typically includes an independent clause with a subject and finite verb. Dependent clauses require a main clause in conventional academic writing. Coordinating conjunctions link units of equal status; subordinators explain conditions, causes, contrasts, or timing. Dangling modifiers appear when an introductory description attaches to the wrong grammatical subject. A complex sentence is not automatically more sophisticated than a clear simple one. Readers should always be able to identify the actor, action and relationship between ideas.",
            "Compare 'While reviewing the report, the market closed' with 'While the analyst reviewed the report, the market closed'. The revised sentence clearly attributes the reviewing action to the analyst rather than the market.",
            "Rewrite 'Because the calculations were wrong' as a complete sentence and correct two dangling modifiers in an imagined financial report. Identify the main clause and every dependent clause in the corrected version.",
            "Which corrects the dangling modifier 'After reviewing the figures, the error was found'?",
            listOf("After reviewing the figures, the analyst found the error", "After reviewing the figures, the error reviewed itself", "The error found reviewing figures"), 'A',
            "Identify the agent performing the review and give it the right subject position."
        ),
        AstraMasterclass.Unit(
            "Tense, aspect and narrative sequence", "Intermediate",
            "English tense helps locate events in time, while aspect indicates whether events are continuing, completed, or connected to another reference point. Present perfect often expresses a past event with present relevance; past perfect locates an earlier event before another past time. Continuous forms emphasize ongoing action. Time markers such as 'by the time', 'since' and 'yesterday' help readers interpret the relation. Avoid switching tense without reason, and remember that dialect preferences for some perfect constructions vary.",
            "'I submitted the application yesterday' describes a completed dated event; 'I have submitted the application' highlights its current relevance; 'I had submitted it before the deadline' shows a sequence of two past events.",
            "Write six sentences describing a missed train using simple past, past perfect and past continuous. Underline each verb phrase, draw a timeline, and revise one sentence whose verb form makes its sequence ambiguous.",
            "Which indicates completion before a second past event?",
            listOf("I had finished before she arrived", "I will finished tomorrow", "I finishing before arrival"), 'A',
            "Past perfect locates completion before another past reference point."
        ),
        AstraMasterclass.Unit(
            "Agreement and clear pronoun reference", "Intermediate",
            "Subjects control agreement by their grammatical head, not the closest noun. Expressions such as 'a list of', 'each of' and 'the number of' can contain nearby plural nouns but have singular subjects. Pronouns need unambiguous antecedents and should preserve person and number. Dialect and context affect collective nouns and singular 'they', which can be grammatical and useful. In legal, medical, and financial communication, ambiguity about who 'it' or 'they' refers to can materially change a reader's understanding.",
            "'The collection of books is valuable' takes the singular verb 'is'. By contrast, 'The books in the collection are valuable' has a plural head. 'Lebo told Amina that she won' is ambiguous unless the intended referent is specified.",
            "Correct five agreement errors and rewrite a paragraph about a client, trader and analyst so every pronoun clearly refers to the intended party. Explain why the nearest noun is not always the subject.",
            "Which is correct?",
            listOf("The list of transactions are missing", "The list of transactions is missing", "The list of transactions be missing"), 'B',
            "The singular head noun 'list' takes 'is'."
        ),
        AstraMasterclass.Unit(
            "Punctuation, lists and quotation conventions", "Intermediate",
            "Punctuation reflects grammatical relationships. A semicolon can join closely related independent clauses, whereas a comma alone creates a comma splice. Colons can introduce explanations or lists after a complete introductory clause. Apostrophes mark contractions or possession, not ordinary plurals. Quotation-mark and punctuation placement conventions differ between British and American styles; consistency matters. When punctuation makes a clause or list ambiguous, recast the sentence instead of merely adding marks. Proofreading should verify meaning as well as mechanical correctness.",
            "'The figures are incomplete, we cannot decide' is a comma splice. 'The figures are incomplete; we cannot decide' joins the clauses correctly. 'It's' means 'it is' or 'it has'; the possessive determiner 'its' has no apostrophe.",
            "Edit a paragraph containing three comma splices, two incorrect apostrophes and an ambiguous list of people and roles. Explain why your changes improve interpretation, not just typographic appearance.",
            "Which can join two closely related independent clauses without a coordinating conjunction?",
            listOf("A semicolon", "An apostrophe", "An exclamation mark inside a word"), 'A',
            "A semicolon links independent clauses while preserving their separate structures."
        ),
        AstraMasterclass.Unit(
            "Register, vocabulary and exact meanings", "Intermediate",
            "The most effective vocabulary is precise for its audience and task, not merely formal. Denotation is a word's direct meaning, whereas connotation includes emotional or social associations. Technical language can be necessary when distinctions matter, but it should be defined when the reader is unfamiliar with it. Overstatement can make a conditional projection sound like a guarantee. Context clues and morphological parts can help interpret unfamiliar words, though specialized definitions still need verification. Avoid replacing a correct technical term with a misleading synonym for stylistic variety.",
            "'The simulation guarantees future profit' is stronger than the evidence permits. 'The simulation projects a gain under stated assumptions' correctly reports a conditional estimate. 'Use' may be clearer than 'utilize' without sacrificing precision.",
            "Rewrite a 200-word finance paragraph twice: first for a student unfamiliar with bonds and then for a professional audience. Preserve distinctions among coupon, yield, capital gain and expected return. Justify your vocabulary choices.",
            "Which version responsibly describes an uncertain forecast?",
            listOf("The model guarantees the outcome", "The model estimates the outcome under stated assumptions", "The model is infallible"), 'B',
            "A model estimate should distinguish assumptions and uncertainty from verified outcomes."
        ),
        AstraMasterclass.Unit(
            "Critical reading, summarizing and inference", "Intermediate",
            "A sound summary preserves a source's argument, supporting evidence and qualifications without inventing conclusions. An inference is a plausible implication from evidence, not necessarily a fact stated in the text. Identify sample size, measurement method, comparison group and time frame where evidence depends on research. A confident or authoritative writing style cannot substitute for traceable sources. Careful reading also distinguishes an author's claim from a quoted opponent's position. Check whether a paraphrase silently converts association into causation or possibility into certainty.",
            "A study says commuting times were lower in a small observed participant sample. 'The participants reported shorter commutes' is a defensible summary; 'The intervention always reduces travel time in every country' exceeds the evidence.",
            "Take a short article and label its thesis, three supporting facts, two inferences and one limitation. Produce a neutral 120-word summary and compare every sentence to the original source for overstatement.",
            "Which inference is justified by a small observational association?",
            listOf("It proves global causation", "It can suggest an association subject to important limits", "It establishes certainty for all future cases"), 'B',
            "Observational association alone does not prove general or causal conclusions."
        ),
        AstraMasterclass.Unit(
            "Paragraph coherence and evidence explanation", "Intermediate",
            "A focused paragraph advances one controlling idea supported by explanation and evidence. Topic sentences can establish direction, but coherence depends on sentence order and explicit logical links. Transitions should accurately express contrast, cause, sequence or example; inserting 'therefore' does not create causation where none exists. A statistic or quotation without explanation leaves the reader to infer its relevance. Paragraph length should follow the complexity of the point and the needs of the reader rather than a rigid word count. Qualification often makes a claim more persuasive, not weaker.",
            "A clear risk paragraph says: 'Concentration exposes the portfolio to company-specific losses.' It then illustrates a large holding's price decline and explains why its portfolio weight magnifies the result. It qualifies that diversification cannot remove every market risk.",
            "Write a 150-word paragraph with a central claim, an invented numeric example marked hypothetical, analysis, limitation and conclusion. Check whether every transition reflects the actual logical connection between sentences.",
            "What primarily connects a source or statistic to a paragraph's thesis?",
            listOf("Explanation of its relevance", "Decorative quotations", "Unrelated examples"), 'A',
            "Interpretation shows why the evidence supports the controlling claim."
        ),
        AstraMasterclass.Unit(
            "Logic, reasoning and fair counterarguments", "Advanced",
            "An argument contains premises offered in support of a conclusion. Deductive validity concerns structure, while soundness also requires true premises. Inductive reasoning provides degrees of confidence under uncertainty. Hasty generalization, equivocation, false dilemmas, ad hominem attacks and confusing correlation with causation can distort analysis. A strong writer fairly states the best opposing argument rather than an easy caricature. Formal reasoning is useful for evaluating model outputs that sound confident but have unsupported assumptions. Revising a conclusion when evidence changes is a sign of intellectual discipline.",
            "'A strategy produced one winning trade; therefore it will always succeed' generalizes far beyond the evidence. A defensible alternative says 'One result is insufficient to estimate persistent performance, especially after trading costs and tail risks.'",
            "Construct an argument on a public policy topic with a claim, three premises, one serious counterargument and the conditions that would change the conclusion. Label which parts are facts, assumptions or predictions.",
            "Why is a single successful trade insufficient to prove durable profitability?",
            listOf("It supports a hasty generalization from inadequate evidence", "Successful trades are mathematically impossible", "Only axioms matter in investing"), 'A',
            "One observation cannot establish reliable expectancy across changing conditions."
        ),
        AstraMasterclass.Unit(
            "Research methods, citations and bibliographic honesty", "Advanced",
            "Research writing begins with a well-defined question and a method for gathering evidence. Primary publications, original datasets and legal texts typically deserve greater weight for their specific claims than unsourced commentary, though methodology still matters. A reference style such as APA 7 organizes author, date, title and publication details; it does not make invented sources real. Paraphrasing should preserve both meaning and qualification while using original phrasing. Evidence logs record exact claims and page locations, limiting accidental misattribution. AI systems should not fabricate references to fill gaps in a bibliography.",
            "A paper observing a correlation in one sample cannot be paraphrased as proving a universal causal mechanism. Even when a citation is perfectly formatted, the sentence remains inaccurate if it exaggerates what the paper reports.",
            "Select two authentic research publications and make a claim-evidence table containing page, quotation, paraphrase and limitation. Check every bibliographic entry against its publication and report any missing data instead of guessing.",
            "Which is the best defense against invented bibliographic references?",
            listOf("Confirm metadata against actual published sources", "Invent a plausible author and year", "Reuse unattributed AI summaries"), 'A',
            "Verifying the primary publication helps confirm that the cited work and claim exist."
        ),
        AstraMasterclass.Unit(
            "Report writing and decision-ready recommendations", "Advanced",
            "Reports should help a reader act on information. State purpose, method, assumptions, findings, options, risks and recommendation in a structure appropriate to the audience. An executive summary should include the actual recommendation and principal qualification, not just a list of topics. Numerical consistency matters across tables, figures and prose. Distinguish proposed steps from completed actions, and describe what evidence would cause a recommendation to change. A concise report can be rigorous when it includes the information required for judgment and avoids unsupported precision.",
            "'Modelled forward portfolio yield is 5.1% if dividends are maintained' is more defensible than 'A guaranteed income yield of 5.1%'. The first identifies an assumption; the second misrepresents uncertainty.",
            "Draft a two-page investment review with objective, two alternatives, quantified hypothetical scenarios and a clear choice. Add an audit checklist for all numbers and claims. Rewrite the executive summary for a nontechnical reader.",
            "What makes a recommendation decision-ready?",
            listOf("An explicit action supported by evidence, risks and assumptions", "A visually attractive cover page alone", "Unqualified certainty"), 'A',
            "Readers need to understand the proposed choice and its factual and conditional basis."
        ),
        AstraMasterclass.Unit(
            "Professional email and negotiation language", "Intermediate",
            "Business messages benefit from a clear subject, concise context, explicit request and accurate deadline. Tone should reflect the relationship without relying on empty formal phrases. Good negotiation separates underlying interests from initial positions and distinguishes nonnegotiable constraints from preferences. Disagreement is easier to resolve when writers specify observable facts and offer a concrete next step. Never invent an authorization, deadline or legal obligation to pressure a recipient. Confidential data and passwords should not be included in routine correspondence unless a secure process is required.",
            "'Send it soon' leaves the recipient uncertain. 'Please provide the revised cash-flow workbook by 14:00 Thursday so the review can be completed' specifies the deliverable, timing and purpose without unnecessary pressure.",
            "Draft a courteous request for data, an overdue follow-up and a correction to a misquoted figure. For each, identify the single action you want the reader to take and remove sentences that do not serve it.",
            "Which makes a workplace message more actionable?",
            listOf("A concrete request and a realistic deadline", "More complicated adjectives", "Invented approval from senior management"), 'A',
            "A clear deliverable and timing allow the recipient to act effectively."
        ),
        AstraMasterclass.Unit(
            "Pronunciation, dialect, stress and listening", "Intermediate",
            "Pronunciation depends on phonemes, syllable stress, connected speech and intonation. Written English often has multiple valid pronunciations, and differences between British, American and other English varieties are not signs that a speaker is wrong. A listening model should use the chosen accent consistently rather than quietly substituting another locale. Spoken practice requires comprehensible input and feedback, whereas text-to-speech alone cannot reliably score pronunciation. In technical speech, careful contrasts among numerals and units help prevent misunderstanding.",
            "In many English varieties, the noun 'record' stresses the first syllable while the verb 'record' stresses the second. Saying 'fifteen, not fifty' with clear stress and pacing can reduce a costly numerical misunderstanding.",
            "Record ten sentences containing dates, currency amounts and consonant contrasts. Practise with the regional voice you selected, compare transcripts when offline recognition is available, and keep track of recognition errors independently from speaking errors.",
            "Why should a language tutor avoid silently changing a learner's requested accent?",
            listOf("It improves consistency for the targeted pronunciation system", "Every accent has identical stress and vowels", "All listeners prefer an unrelated accent"), 'A',
            "Regional pronunciation choices affect sound patterns, rhythm and useful practice."
        ),
        AstraMasterclass.Unit(
            "Literature, imagery and narrative analysis", "Advanced",
            "Literary interpretation considers narrator, point of view, structure, imagery, tone and cultural or historical context. An interpretation is stronger when it explains how specific language produces an effect and accounts for other plausible readings. A fictional narrator's statement does not necessarily represent an author's opinion. Metaphor and symbolism can carry multiple meanings; readers should not pretend one association proves authorial intent. Close reading prioritizes genuine textual evidence, and contextual claims should be sourced rather than invented from popular stereotypes about a period or writer.",
            "'The street swallowed the last light' uses personification and metaphor to evoke a threatening atmosphere, whereas 'the street grew dark' reports a change more literally. Without surrounding context neither line establishes the narrator's reliability.",
            "Choose a public-domain paragraph and identify two stylistic devices, one point-of-view signal and two plausible interpretations. Support each interpretation with exact quoted words and explain where your evidence remains ambiguous.",
            "Which is the strongest foundation for a close reading?",
            listOf("Specific words and structural features in the actual text", "Imaginary lines attributed to the author", "A plot summary without textual analysis"), 'A',
            "Analysis links observable language choices to interpretive claims."
        ),
        AstraMasterclass.Unit(
            "Editing, correction and responsible fact checking", "Advanced",
            "Editing should begin with factual and structural questions, proceed through organization and clarity, and end with grammar and punctuation. Copyediting cannot repair an unsupported central argument. Verify author names, dates, figures, causal claims and institutional affiliations against trustworthy sources instead of recycling model-generated wording. A correction record should identify the earlier claim, evidence that contradicts it and the replacement. When questioned about an error, acknowledging it directly is more useful than inventing a fictional distinction to protect an earlier answer.",
            "If a model incorrectly identifies 'Niall Shiller' as the author of Irrational Exuberance, it should verify the actual author, Robert J. Shiller, acknowledge the mistaken attribution and avoid repeating it. Inventing another economist worsens the error.",
            "Edit a 250-word draft in separate factual, structural and sentence-level passes. Maintain an audit log of all corrected assertions, citations and numbers. Verify that later sections do not repeat a retracted claim.",
            "What is appropriate after identifying a false author attribution?",
            listOf("Acknowledge and correct the claim using reliable evidence", "Invent another person's biography", "Avoid the original question permanently"), 'A',
            "Transparent corrections preserve the accuracy of the document and the reader's ability to verify it."
        ),
        AstraMasterclass.Unit(
            "Public speaking, explanations and active listening", "Advanced",
            "Effective oral communication presents a clear question, a few supported points, well-chosen illustrations and a conclusion that answers the question. Signposting helps an audience follow technical material, and a calm pace allows terminology to be understood. Active listening checks what a speaker actually meant instead of answering a convenient but different question. Where several languages are involved, language and regional accent choices should be explicit. Speech recognition is fallible, especially on specialist vocabulary, so a transcribed question may need confirmation before high-stakes action.",
            "A five-minute explanation of bond duration can open with 'Bond prices usually fall when yields rise', illustrate a hypothetical 1% yield shift, qualify that duration is an approximation, and invite a numerical follow-up. That is more teachable than listing formulas without context.",
            "Prepare a five-minute explanation of an economic concept. Record it and count unnecessary jargon, check whether every statistic is hypothetical or sourced, and prepare three questions to test the listener's understanding.",
            "What supports understanding in a technical talk?",
            listOf("Clear structure, defined terms and evidence-backed examples", "Maximum speed and unexplained jargon", "Avoidance of clarifying questions"), 'A',
            "Structure and comprehension checks help an audience reason with the speaker's information."
        )
    )
}
