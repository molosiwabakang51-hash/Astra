package com.astra.agent

/** Original offline French grammar workshop material, independent of installed speech voices. */
internal object AstraMasterFrench {
    val units: List<AstraMasterclass.Unit> = listOf(
        AstraMasterclass.Unit(
            "Sounds, spelling, liaison and elision", "A1–A2",
            "French pronunciation is not obtained by reading English letter values. French has nasal vowels in words such as sans and bon, often silent final consonants, and sound combinations whose spelling must be learned in context. Liaison sometimes links a normally silent consonant to a following vowel, as in les amis, but it is not appropriate in every word boundary. Elision replaces a vowel before another vowel in forms such as le + ami becoming l'ami. Listen and imitate complete phrases rather than isolated letters. Accent marks can distinguish words and sounds, while the quality of a synthesized voice depends on installed language data.",
            "Compare le garçon, les garçons and les amis. In les amis, the final s of les is linked to the vowel in amis; by contrast the final s in les garçons is not pronounced as a separate final consonant. L'ami shows elision, not a new noun ending.",
            "Make three columns: written form, syllables heard and meaning. Include l'école, les amis, un enfant and mon ami. Record yourself using a French voice only if one is installed, then replay your speech and note where liaison is present or absent.",
            "Which written form correctly expresses 'the friend' with elision?",
            listOf("Le ami", "L'ami", "La amis"), 'B',
            "French elides le or la before a vowel or silent h in appropriate contexts, producing l'ami."
        ),
        AstraMasterclass.Unit(
            "Articles, gender, number and agreement", "A1–A2",
            "French nouns are grammatically masculine or feminine. Learn a noun with its article because its ending alone does not always predict gender. Definite articles are le, la, l' and les; indefinite articles are un, une and des. Adjectives commonly agree with nouns in gender and number, though their position and spoken changes vary. For quantities and unspecified mass nouns, French also uses partitive constructions such as du pain and de l'eau. Negation frequently changes an indefinite or partitive article to de or d', with important exceptions such as être.",
            "Un livre intéressant becomes des livres intéressants in the plural. Une petite maison becomes de petites maisons in standard French when the adjective precedes a plural noun; this is a useful pattern, not a reason to treat all plurals alike. Je bois du lait becomes Je ne bois pas de lait.",
            "Write six noun phrases with correct articles and adjectives: one book, two books, a small house, interesting houses, some water, and no milk. Explain how gender and negation affect articles. Check each phrase by reading it aloud rather than assuming written endings always sound distinct.",
            "Which phrase correctly means 'an interesting book'?",
            listOf("Un livre intéressant", "Une livre intéressant", "Un livres intéressantes"), 'A',
            "Livre is masculine singular in the sense of a book, so un and intéressant match it."
        ),
        AstraMasterclass.Unit(
            "Present-tense patterns and essential irregular verbs", "A1–A2",
            "The French present tense can describe habits, general facts and actions occurring now; unlike English, it does not require a separate continuous form for ordinary ongoing actions. Regular -er verbs commonly take -e, -es, -e, -ons, -ez, -ent with je, tu, il/elle, nous, vous and ils/elles. Many frequent verbs are irregular: être, avoir, aller, faire, venir and pouvoir must be practised as paradigms. Written third-person plural endings may be silent; spelling accuracy does not follow from sound alone. Always associate the conjugated form with its subject.",
            "With parler: je parle, tu parles, elle parle, nous parlons, vous parlez, ils parlent. With aller: je vais, tu vas, il va, nous allons, vous allez, elles vont. Je suis étudiant is present tense; Je travaille aujourd'hui can describe what I am doing today.",
            "Create a two-column dialogue about your studies, alternating je and nous in six present-tense sentences. Conjugate parler, faire, aller and être correctly. Mark which sentences describe habits and which describe something happening today, then translate them back into English.",
            "Which form correctly completes 'Nous ___ français' with parler?",
            listOf("parlez", "parle", "parlons"), 'C',
            "With the subject nous, the regular present-tense form of parler is parlons."
        ),
        AstraMasterclass.Unit(
            "Word order, negation and asking questions", "A1–B1",
            "French ordinary declarative sentences often follow subject–verb–object order, but pronoun placement and questions change that pattern. Written standard negation surrounds a finite verb with ne and pas, changing to n' before a vowel: Je ne sais pas. Colloquial speech often omits ne, yet this is register-dependent and should not be confused with the full standard written form. Questions may use intonation, est-ce que or subject–verb inversion, with different degrees of formality. The question word can appear at the beginning in forms such as Pourquoi est-ce que tu pars?",
            "Tu étudies ce soir is a statement. Est-ce que tu étudies ce soir ? and Étudies-tu ce soir ? ask whether you are studying this evening. Je n'étudie pas ce soir negates it. The -t- in parle-t-il is an inserted sound for pronunciation, not a separate verb.",
            "Rewrite 'Elle parle français' as a standard negative and as two different questions. Then create a short interview with who, when and why questions, including one example of est-ce que and one correct inversion. Note which variant sounds more formal.",
            "Which is a standard written negative of 'Je comprends'?",
            listOf("Je comprends ne pas", "Je ne comprends pas", "Je pas comprends"), 'B',
            "Standard French usually places ne before the finite verb and pas after it."
        ),
        AstraMasterclass.Unit(
            "Object pronouns and the place of y and en", "B1",
            "Direct-object pronouns include le, la and les; indirect-object pronouns include lui and leur. These normally appear before the conjugated verb: Je le vois, Je lui parle. The pronoun y can stand for a place or certain à-phrases; en commonly replaces quantities or certain de-phrases. In a two-verb construction where a pronoun belongs to the infinitive, place it immediately before that infinitive: Je vais le lire. Pronoun order can become complex with multiple objects and imperatives, so learn common templates rather than freely rearranging words.",
            "Je regarde le livre becomes Je le regarde. Je parle à Marie becomes Je lui parle. Tu vas au marché ? — Oui, j'y vais. Tu veux du pain ? — Oui, j'en veux. Je vais lire ce livre becomes Je vais le lire.",
            "Transform eight sentences using le, la, lui, leur, y and en, including at least two two-verb constructions. Explain what each pronoun replaces, and test that your rewritten sentences preserve whether an object is direct, indirect, a place or a quantity.",
            "Which sentence correctly replaces 'à Marie' in 'Je parle à Marie'?",
            listOf("Je la parle", "Je lui parle", "Je y parle"), 'B',
            "Parler à quelqu'un ordinarily takes an indirect-object pronoun: Je lui parle."
        ),
        AstraMasterclass.Unit(
            "Reflexive verbs, routines and reciprocity", "A2–B1",
            "Pronominal verbs use a reflexive or reciprocal pronoun that changes with the subject: je me, tu te, il se, nous nous and vous vous. Some meanings are genuinely reflexive, such as se laver, while others are idiomatic, such as se souvenir de, and cannot be translated word for word. Negative present-tense sentences put ne before the reflexive pronoun and pas after the finite verb. In compound past tenses, pronominal verbs take être as auxiliary, but past-participle agreement depends on grammatical function and is not always automatic.",
            "Je me lève à six heures means I get up at six. Nous nous levons tôt is the first-person plural. Je ne me lève pas tard places negation around the pronoun-plus-verb group. In the passé composé, Elle s'est levée tôt uses être and a feminine singular participle.",
            "Write your weekday routine in eight sentences using se lever, s'habiller, se préparer and se coucher. Change two sentences into negatives and two into the passé composé. Explain at least one case where the reflexive form has an idiomatic meaning.",
            "Which sentence correctly says 'We get up'?",
            listOf("Nous nous levons", "Nous se levons", "Nous nous lever"), 'A',
            "The first-person plural reflexive pronoun is nous, and the finite verb is levons."
        ),
        AstraMasterclass.Unit(
            "The passé composé with avoir and être", "A2–B1",
            "The passé composé usually presents a completed past event or a bounded series of events. Form it with the present tense of avoir or être plus a past participle. Many verbs use avoir; numerous verbs of movement and pronominal verbs use être in relevant constructions. With être, the participle normally agrees with the subject; with avoir, agreement can occur when a direct object precedes the participle, so 'avoir means never agree' is inaccurate. Learn auxiliary selection with examples and distinguish completed events from background descriptions.",
            "J'ai étudié hier means I studied yesterday. Elle est arrivée tôt uses être and a feminine participle. Les lettres que j'ai écrites illustrates agreement with a preceding feminine plural direct object; J'ai écrit les lettres does not place the object before the participle.",
            "Write a five-event diary for yesterday using aller, prendre, lire, arriver and finir. Underline each auxiliary and past participle. Justify your choice of avoir or être, then convert one sentence with a preceding direct object to inspect agreement.",
            "Which past sentence is correct for 'She arrived'?",
            listOf("Elle a arrivée", "Elle est arrivé", "Elle est arrivée"), 'C',
            "Arriver typically uses être, and the participle agrees with a feminine singular subject."
        ),
        AstraMasterclass.Unit(
            "The imparfait versus completed past events", "B1",
            "The imparfait often presents habits, descriptions, ongoing situations and background in the past. The passé composé tends to mark bounded events that advance a narrative. Both forms can appear in one sentence: an activity already under way in the imparfait when a completed event occurs in the passé composé. Context, discourse structure and aspect matter more than memorizing an absolute rule that one tense equals long action and the other equals short action. Imparfait endings normally derive from the present nous stem with -ais, -ais, -ait, -ions, -iez, -aient; être is exceptional.",
            "Quand j'étais petit, je lisais chaque soir describes a past habit. Je lisais quand le téléphone a sonné means I was reading when the phone rang. The background je lisais is in the imparfait; the interruption a sonné is in the passé composé.",
            "Write a story about a rainy journey using at least six verbs in the imparfait for weather, setting and repeated actions and four passé composé forms for events. Mark why you selected each tense rather than choosing only on how many minutes an action lasted.",
            "Which best completes 'Je ___ quand il a téléphoné' using lire?",
            listOf("lisais", "ai lu", "lirai"), 'A',
            "The imparfait lisais supplies the activity already in progress when a past event occurred."
        ),
        AstraMasterclass.Unit(
            "Future forms and time expressions", "A2–B1",
            "The futur proche uses the present of aller plus an infinitive and often describes a plan or expected near-term outcome: Je vais partir. The futur simple uses future endings attached to a stem, with irregular stems for common verbs such as avoir, être, aller and faire. Neither future tense inherently guarantees that an event will occur. After a time conjunction such as quand referring to a future event, French can use the future tense even where English uses a present form. Time adverbs and context help distinguish plans, predictions and scheduled events.",
            "Je vais travailler demain emphasizes an upcoming plan. Je travaillerai demain is a future simple form. Quand tu arriveras, nous commencerons combines future forms after a future-oriented quand. J'aurai is the future of avoir, not j'avoirai.",
            "Write four predictions about the year ahead and four planned tasks for tomorrow, choosing futur simple or futur proche. Include one quand clause and at least two irregular future stems. Explain how uncertain predictions differ from firm arrangements.",
            "Which is the futur simple of 'nous partir'?",
            listOf("nous allons partir", "nous partirons", "nous partions"), 'B',
            "Partir uses the infinitive stem and -ons for the first-person plural future: partirons."
        ),
        AstraMasterclass.Unit(
            "Conditional politeness and hypothetical si clauses", "B1–B2",
            "The conditionnel présent uses a future-type stem with imparfait endings, often expressing hypothetical results, softened requests or reported information. In a common hypothetical pattern, si + imparfait is followed by the conditional in the result clause: Si j'avais le temps, je voyagerais. Do not place the present conditional immediately after si in this pattern. Different si constructions express real possibilities or contrary-to-fact past situations and require different tense combinations. Polite forms such as je voudrais and pourriez-vous offer useful alternatives to blunt requests.",
            "Si j'avais plus de temps, j'apprendrais le japonais expresses an imagined condition. Si tu étudies, tu progresseras is a realistic future condition using present after si and future in the main clause. Pourriez-vous m'aider ? is a polite request.",
            "Form three requests using je voudrais, pourriez-vous and aimerais. Then write four pairs of conditional clauses: two realistic possibilities and two hypothetical situations. Label the tense on each side of si and explain why si je voudrais is not appropriate in the target pattern.",
            "Which is the usual hypothetical pattern?",
            listOf("Si j'aurais le temps, je voyagerais", "Si j'avais le temps, je voyagerais", "Si j'ai le temps, je voyagerais"), 'B',
            "For a present hypothetical result, standard French uses si + imparfait and the conditional in the result clause."
        ),
        AstraMasterclass.Unit(
            "Relative clauses: qui, que, où and dont", "B1–B2",
            "Relative pronouns link descriptions to a noun while avoiding repetition. Qui is often the subject of the relative clause; que is often its direct object; où can identify place or time; and dont can replace a complement introduced by de. These are not interchangeable simply because English uses 'who' or 'that' in several contexts. The following verb and complement structure determine the right pronoun. Using dont with another repeated de complement can create redundant wording. Practice by combining pairs of complete sentences and checking their grammatical roles.",
            "Le livre qui est ici est utile means the book which is here is useful; qui is the subject of est. Le livre que j'ai lu est utile uses que as the object of lu. La ville où j'habite is the city where I live. Le sujet dont je parle is the subject I am talking about.",
            "Combine eight pairs of short sentences using each of qui, que, où and dont twice. For every combination, identify the relative pronoun's function and whether the original verb takes a de complement. Compare your results to the original meaning.",
            "Which completes 'Le film ___ je parle est intéressant'?",
            listOf("qui", "que", "dont"), 'C',
            "Parler de quelque chose takes a de complement, which is expressed by dont in this relative clause."
        ),
        AstraMasterclass.Unit(
            "Subjunctive, necessity and uncertainty", "B2",
            "The present subjunctive occurs in many dependent clauses after expressions of necessity, wishes, emotions and doubt. It often follows que, as in Il faut que tu sois prêt. It is not simply a tense for 'the future' or a universal marker of uncertainty; the grammatical trigger and viewpoint matter. Many forms overlap with the indicative in sound or spelling, while être, avoir, aller and faire have distinctive subjunctive forms. Certain expressions and clauses use the indicative instead, even when their meanings involve subjectivity or probability.",
            "Je veux que tu viennes expresses a wish and uses subjunctive viennes. Il faut qu'il fasse attention expresses necessity and uses fasse. Je sais qu'il vient expresses a known fact with indicative vient. The choice is tied to the main expression, not only to chronological time.",
            "Create six pairs contrasting je veux que, il faut que, je sais que and je pense que in affirmative or negative forms. Identify which examples license subjunctive and which use indicative, and explain the function rather than asserting that all uncertainty always takes subjunctive.",
            "Which completes 'Il faut que tu ___ prêt' with être?",
            listOf("sois", "es", "seras"), 'A',
            "Il faut que commonly triggers subjunctive, and tu sois is the present subjunctive of être."
        ),
        AstraMasterclass.Unit(
            "Adverbs, comparison and sentence connectors", "B1–B2",
            "Adverbs modify verbs, adjectives or other expressions. Many are derived from adjective forms with -ment, but numerous common adverbs are irregular or independent, such as bien, mal and beaucoup. Comparative constructions include plus...que, moins...que and aussi...que; meilleur is an adjective meaning better, while mieux is an adverb in many contexts. Logical connectors help distinguish cause, contrast, consequence and concession. Using connectors does not automatically create a sound argument: each causal claim still needs supporting evidence.",
            "Elle travaille mieux qu'avant compares how she works, while C'est une meilleure idée compares the quality of an idea. Parce que introduces a reason; pourtant signals contrast. Bien qu'il soit tard, nous continuons contains a concessive connector and subjunctive soit.",
            "Write a paragraph about learning a language that uses at least four different connectors for reason, contrast, addition and conclusion. Include both mieux and meilleur correctly, then underline each comparison and explain what is being compared.",
            "Which sentence uses the adverb 'better' correctly?",
            listOf("Elle parle meilleur", "Elle parle mieux", "Elle mieux parle meilleur"), 'B',
            "Mieux generally modifies a verb, while meilleur is an adjective modifying a noun."
        ),
        AstraMasterclass.Unit(
            "Reading comprehension, inference and source criticism", "B1–C1",
            "Reading fluency requires more than translating every word independently. Identify the writer's purpose, audience, main claim, evidence, tone and what is only implied. Pay attention to pronouns, time markers, conditional phrasing and contrasts, which change meaning across sentences. Cognates can assist, but false friends such as actuellement (currently) and éventuellement (possibly) can distort a literal translation. Newspaper and social-media material may contain unsupported claims, so separate the text's assertion from externally verified fact. An unfamiliar word is often best interpreted from context first, then checked in a reputable dictionary.",
            "In a short French article, 'Actuellement, la ville étudie le projet' means 'Currently, the city is studying the project', not 'Actually the city studied it.' 'Ce projet pourrait réduire les coûts' uses conditional pourrait and does not assert the savings have already happened.",
            "Select a 250-word French passage from a legal source you may use. Summarize its argument in 80 English words and 80 French words. Underline all assertions involving peut, devrait and pourrait, and distinguish proven statements from predictions. List false friends before checking them against a dictionary.",
            "What does 'actuellement' usually mean?",
            listOf("actually", "currently", "possibly"), 'B',
            "Actuellement normally means 'currently'; 'actually' is often en fait or effectivement depending on context."
        ),
        AstraMasterclass.Unit(
            "Formal correspondence, register and practical dialogue", "B1–C1",
            "Register depends on audience and purpose. Tu is familiar singular address, while vous is polite singular or plural and requires corresponding verb forms. Standard formal messages typically include a clear subject, courteous opening, precise request, relevant dates and a respectful closing; casual spoken French allows shorter sentences and different conventions. Effective communication also requires managing clarification, repair and disagreement politely. Accent choice alone does not guarantee appropriate cultural or professional register. Practise checking whether a statement is a request, a claim or a proposal.",
            "À a friend: 'Tu peux m'aider demain ?' In a formal message: 'Pourriez-vous me confirmer le rendez-vous de jeudi, s'il vous plaît ?' Both ask for help or confirmation, but the second uses vous and conditional pourriez-vous for politeness.",
            "Write two messages requesting information about a course: a casual note to a friend and a formal email to an institution. Include a precise question, one proposed appointment date and a courteous closing. Read each with the correct pronouns and note how pronunciation differs from lexical meaning.",
            "Which form is most suitable for a polite request to an unfamiliar official?",
            listOf("Tu me donnes les documents", "Donne-moi les documents maintenant", "Pourriez-vous m'envoyer les documents, s'il vous plaît ?"), 'C',
            "The conditional pourriez-vous plus vous form typically signals courteous formal address."
        ),
        AstraMasterclass.Unit(
            "Longer arguments, coherence and independent expression", "B2–C1",
            "Advanced communication involves presenting a thesis, defending it with connected reasons, acknowledging counterarguments and concluding proportionately. Coherent French paragraphs use clear referents, consistent tense and connectors such as toutefois, en revanche, par conséquent and malgré cela. A long answer is not necessarily a strong answer; specificity, source quality and a checkable chain of reasoning matter. Revise for article agreement, verb conjugation, pronoun reference, sentence length and unsupported generalizations. Self-assessment must include fresh tasks instead of only repeating memorized example sentences.",
            "Consider 'Les cours en ligne sont utiles parce qu'ils sont accessibles. Toutefois, ils ne remplacent pas toujours les échanges en personne.' The pronoun ils refers to cours, and toutefois introduces a limit rather than contradicting the first clause outright. A more precise argument could cite a specific comparison and note uncertainties.",
            "Write a 250-word French essay on whether digital tools improve language learning. State a thesis, give two developed reasons, present one counterargument, and conclude with limits. Revise it for five separate categories: gender, tense, pronoun, connector and unsupported factual claim.",
            "Which connector most clearly introduces a contrast?",
            listOf("Par conséquent", "Toutefois", "De plus"), 'B',
            "Toutefois means 'however' and is commonly used to contrast or qualify a previous statement."
        )
    )
}
