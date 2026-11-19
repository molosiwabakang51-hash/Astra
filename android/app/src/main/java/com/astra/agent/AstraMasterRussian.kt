package com.astra.agent

/** Original practical offline Russian grammar and comprehension material. */
internal object AstraMasterRussian {
    val units: List<AstraMasterclass.Unit> = listOf(
        AstraMasterclass.Unit(
            "Cyrillic alphabet, stress and sound distinctions", "A1",
            "Russian uses the Cyrillic alphabet, which contains 33 letters. Some letters resemble Latin letters but represent different sounds: В is /v/, Н is /n/, and Р is /r/. Others identify sounds not represented by a single English letter, such as Ж, Ш and Щ. The soft sign ь marks palatalization or a grammatical distinction and has no independent vowel sound. Russian word stress is mobile and affects vowel reduction, so learning spelling without a stressed pronunciation can produce poor comprehension. Use a reliable native recording to check accent and consonant softness.",
            "Compare дом (house), день (day) and дверь (door). The written soft sign in день does not add a spoken vowel; it indicates a soft preceding consonant. In молоко́, unstressed о vowels are reduced in ordinary standard pronunciation. The Latin lookalike Р in рука́ corresponds to an r sound, not English P.",
            "Transliterate ten words in both directions, then record them with marked stress. Include день, хорошо́, письмо́, меня́ and рука́. Identify hard and soft consonants and compare your own audio to a native reference, noting which sounds your device's TTS may pronounce imperfectly.",
            "What does the Russian letter Р normally represent?",
            listOf("An r sound", "An English p sound", "A silent vowel"), 'A',
            "Cyrillic Р resembles Latin P visually but corresponds to a Russian r sound."
        ),
        AstraMasterclass.Unit(
            "Greetings, identity and present-tense omission of быть", "A1",
            "Russian can express identity in the present tense without an overt equivalent of English am, is or are in everyday affirmative statements: Я студент, Она врач. This does not mean the verb быть never occurs; its past and future forms are common, and dash punctuation may be needed in some written nominal constructions. Register matters in greetings: привет is informal, while здравствуйте suits formal settings. Introductions typically include меня зовут with an accusative pronoun, literally 'they call me'. Read full dialogues instead of memorizing isolated words.",
            "Здравствуйте! Меня зовут Аня. Я студентка. — Очень приятно! Меня зовут Сергей. Я инженер. The speakers introduce themselves with different occupations; no present-tense form of 'to be' is required in these ordinary noun predicates. The informal equivalent would use Привет!",
            "Write a six-turn dialogue between a student and an instructor using a polite greeting, two introductions, an occupation, a question and a farewell. Translate it, identify formal versus informal address and practise recording it slowly with correct stressed syllables.",
            "How is 'I am a student' ordinarily expressed in present-tense Russian?",
            listOf("Я есть студент", "Я студент", "Мне студент"), 'B',
            "Russian normally omits the present-tense copula in simple affirmative nominal predicates."
        ),
        AstraMasterclass.Unit(
            "Noun gender, number and core patterns", "A1",
            "Russian nouns are usually categorized as masculine, feminine or neuter, and they inflect for number and case. Many masculine nouns end in a consonant, feminine nouns often in -а or -я, and neuter nouns often in -о or -е, though exceptions exist. The soft sign ь does not guarantee a particular gender: день is masculine, ночь is feminine. Some nouns referring to people have grammatical gender linked to meaning. Plural endings vary by stem and spelling rules, and some plurals are irregular. Learn every noun with gender, stressed form and an example phrase.",
            "Стол (table) is masculine, книга (book) is feminine, окно (window) is neuter and ночь (night) is feminine. Common plurals include столы, книги and окна. День has the plural дни, demonstrating that a simple add-an-ending rule is insufficient.",
            "Classify ten nouns by gender and number and form their plurals; include город, музей, неделя, море, окно, день and ночь. Explain why knowing a final consonant or a soft sign alone may not identify the correct declension and why accent marks in a dictionary help.",
            "Which noun is normally neuter?",
            listOf("стол", "книга", "окно"), 'C',
            "Окно has the typical neuter singular -о ending."
        ),
        AstraMasterclass.Unit(
            "Pronouns, possessives and polite address", "A1–A2",
            "Personal pronouns include я, ты, он, она, оно, мы, вы and они. Вы serves both plural and polite singular address, with corresponding verb agreement. Possessives such as мой and твой agree with the possessed noun's gender, number and case, not with the owner's sex. Его, её and их are commonly invariable possessive forms. Russian may omit a pronoun when the conjugated verb and context make the subject clear, but doing so is a pragmatic choice rather than a universal obligation.",
            "Это моя книга, but Это мой словарь, because книга is feminine and словарь masculine. Это моё окно uses the neuter form. Вы говорите по-русски? can politely address a single unfamiliar person or a group.",
            "Describe five objects in a classroom using мой, моя, моё and мои. Rewrite three sentences to address a teacher with вы instead of ты, altering the verb endings. Add two polite requests and explain how agreement relates to the noun rather than to the speaker.",
            "Which phrase correctly means 'my book'?",
            listOf("мой книга", "моя книга", "моё книга"), 'B',
            "Книга is grammatically feminine, so the agreeing possessive is моя."
        ),
        AstraMasterclass.Unit(
            "The nominative and accusative cases", "A1–A2",
            "Cases mark the syntactic roles of noun phrases. The nominative commonly labels a subject, while the accusative often marks a direct object or a destination after certain prepositions. The accusative of inanimate masculine singular nouns often resembles the nominative, but animate masculine singular nouns frequently take a genitive-like accusative. Feminine singular -а nouns often change to -у and -я nouns to -ю. These patterns should be applied with gender, number and animacy in mind; not every object of every verb is accusative.",
            "Я читаю книгу: я is nominative subject and книгу is the accusative of книга. Я вижу стол retains стол as the inanimate masculine accusative. Я вижу брата uses брата as the animate masculine accusative of брат. Я иду в школу expresses movement into a destination.",
            "Take six nominative nouns and place each after Я вижу, including an animate masculine noun and a feminine -а noun. Add two sentences of movement with в + destination. For every transformation write gender, animacy, case and reason for the ending.",
            "Which phrase correctly means 'I am reading a book'?",
            listOf("Я читаю книга", "Я читаю книги", "Я читаю книгу"), 'C',
            "Книгу is the accusative singular of feminine книга in this direct-object construction."
        ),
        AstraMasterclass.Unit(
            "Genitive case: absence, possession and quantity", "A2",
            "The genitive has many functions, including absence after нет, relations of possession, certain quantity expressions and complements of particular prepositions. Singular masculine nouns often use -а or -я, feminine -а nouns often use -ы or -и, and neuter -о nouns often use -а; the full declension system contains important exceptions. Genitive plural forms are especially diverse and cannot be generated reliably by one suffix. After numerals, noun forms depend on the quantity and its grammatical environment, so a phrase like пять книг differs from две книги.",
            "У меня есть книга means 'I have a book' with a genitive-like possession construction after у. У меня нет книги means 'I do not have a book', using genitive книги after нет. Пять книг is the genitive plural pattern associated with five books.",
            "Write affirmative and negative possession sentences for книга, словарь and время using у меня есть and у меня нет. Then form number phrases for 1, 2 and 5 books, checking the required noun form and the distinction between nominal and genitive case usage.",
            "Which is the usual negative possessive of 'I have no book'?",
            listOf("У меня нет книги", "У меня нет книгу", "У меня нет книга"), 'A',
            "Нет normally governs the genitive, and книга becomes книги in the genitive singular."
        ),
        AstraMasterclass.Unit(
            "Dative case and people receiving actions", "A2",
            "The dative often identifies recipients, experiencers, age expressions and complements of specific verbs and prepositions. Dative nouns follow patterns such as брат → брату and сестра → сестре. Pronouns have distinct dative forms: мне, тебе, ему, ей, нам, вам and им. Several Russian expressions that English phrases with 'I' as a subject instead use a dative experiencer, as in Мне холодно, 'I am cold'. Accidental literal translation from English can produce incorrect case usage, so learn constructions in context.",
            "Я даю другу книгу means I give a book to a friend. Другу is dative recipient and книгу is accusative direct object. Мне двадцать лет means I am twenty years old and does not use я as subject in the ordinary construction. Ей нравится музыка uses a dative experiencer.",
            "Write a seven-sentence dialogue involving lending a book, telling someone their age, and expressing that someone likes music. Identify the recipient and direct object in each example. Replace three nouns with dative personal pronouns and check all endings.",
            "Which sentence ordinarily means 'I am cold'?",
            listOf("Я холодный", "Мне холодно", "Меня холодно"), 'B',
            "Russian commonly uses the dative experiencer мне with the predicative холодно."
        ),
        AstraMasterclass.Unit(
            "Instrumental case: means, company and roles", "A2–B1",
            "The instrumental marks a tool or means, a companion after с, and predicative roles in various constructions. A common masculine singular ending is -ом or -ем, and common feminine singular endings include -ой or -ей, but agreement and special declensions matter. The word с can take instrumental for accompaniment, whereas different meanings and prepositions can govern other cases. Occupational identity is often instrumental after some forms of быть, such as Он был инженером. Always identify the governing preposition or verb before selecting a case.",
            "Я пишу ручкой means I write with a pen: ручкой is instrumental singular of ручка. Я говорю с другом means I speak with a friend. Она была учителем uses instrumental учителем to express a role in a past copular construction.",
            "Write a diary describing five activities, each with an instrument or companion, and include a past statement of profession. For each instrumental noun, provide its nominative dictionary form and explain which word or meaning requires instrumental.",
            "Which phrase usually means 'with a friend'?",
            listOf("с другу", "с другом", "с друга"), 'B',
            "С expressing accompaniment takes the instrumental, here другом."
        ),
        AstraMasterclass.Unit(
            "Prepositional case: places and subject matter", "A2",
            "The prepositional case commonly follows prepositions such as в and на to express static location, and о/об to indicate the subject of discussion. Many singular nouns use endings -е or -и in this case, but stems and special groups matter. Contrast в школе (at school) with в школу (to school); the same preposition can take a different case depending on meaning. Do not conclude that all Russian place descriptions are prepositional: many spatial relationships use other prepositions and cases.",
            "Я учусь в школе describes study in a school, using prepositional школе. Я иду в школу describes motion toward school, using accusative школу. Мы говорим о музыке means we are speaking about music; музыке follows о in the prepositional case.",
            "Draw a two-column location/destination table with words школа, магазин, музей and университет. Create eight sentences using в + location versus в + destination. Add two sentences on topics of conversation with о and explain the semantic difference.",
            "Which phrase typically means 'in the school'?",
            listOf("в школе", "в школу", "в школой"), 'A',
            "Static location with в generally uses the prepositional: школе."
        ),
        AstraMasterclass.Unit(
            "Present-tense conjugation and negation", "A1–A2",
            "Russian finite verbs agree with person and number. In broad terms two common conjugation patterns differ in their present or simple-future endings, though spelling rules and irregular verbs matter. Читать gives я читаю, ты читаешь, он читает, мы читаем, вы читаете, они читают. The negative particle не ordinarily precedes the finite verb, but special constructions and emphasis can change placement. Frequent verbs such as быть, хотеть, есть and идти require attention to irregular or atypical forms. A Russian present form may cover habitual and current activity without an English continuous suffix.",
            "Я читаю книгу means I am reading or I read a book depending on context. Ты читаешь and мы читаем use different endings. Я не читаю сегодня means I am not reading today. Он говорит means he speaks or is speaking, depending on context.",
            "Conjugate читать and говорить for all six subject combinations, then form four negative sentences. For each sentence state whether it could describe a habit or a present action and identify a context phrase that disambiguates it.",
            "Which is the correct first-person plural of читать?",
            listOf("мы читаю", "мы читает", "мы читаем"), 'C',
            "The first-person plural present of читать is читаем."
        ),
        AstraMasterclass.Unit(
            "Past tense, gender and completed narratives", "A2",
            "Russian past-tense finite forms commonly show gender in the singular: он читал, она читала, оно читало, while plural читали does not distinguish gender. Past forms do not conjugate separately for person in the same way as present forms. Context and verbal aspect identify whether a past event is ongoing, repeated or presented as completed. With plural or polite вы, the past form is plural, and the gender of a single addressee does not generally change it. Accuracy depends on matching the subject and verb's aspect, not on translating English past suffixes literally.",
            "Он работал вчера and она работала вчера have different singular past endings. Они работали вчера uses plural. Я читала книгу spoken by a female narrator uses -ла, while я читал spoken by a male narrator uses -л; written Russian encodes this distinction.",
            "Create two five-sentence biographies in the past, one about a male student and one about a female student. Use читать, работать, знать and говорить, then rewrite two sentences with plural subjects and check agreement.",
            "Which past form agrees with 'она' for читать?",
            listOf("читала", "читал", "читали"), 'A',
            "Она is feminine singular and the common past form ends -ла: читала."
        ),
        AstraMasterclass.Unit(
            "Imperfective versus perfective aspect", "B1",
            "Russian distinguishes imperfective and perfective verb aspects. Imperfective forms often express process, habitual activity or a nonbounded event; perfective forms generally present an event as a bounded whole. The pair писать / написать shows different perspectives on writing. Perfective verbs lack an ordinary present interpretation and their nonpast forms typically describe future events. Aspect is not a direct replacement for English simple versus progressive tenses; context, negation and lexical meanings constrain the choice. Not every verb pair differs by a single prefix.",
            "Вчера я писал письмо can describe that I was writing a letter, without asserting completion. Вчера я написал письмо presents the writing as completed. Каждый день я пишу письма describes a habit, while Завтра я напишу письмо expresses a future completed event.",
            "Write four pairs contrasting process and completed result using читать/прочитать, писать/написать and делать/сделать. Include a sentence about daily habits and one about a future result. For each pair explain what changes in the speaker's viewpoint.",
            "Which phrase clearly presents completed writing yesterday?",
            listOf("Вчера я пишу письмо", "Вчера я писал письмо", "Вчера я написал письмо"), 'C',
            "Написал is perfective past and presents the letter-writing event as a completed whole."
        ),
        AstraMasterclass.Unit(
            "Future constructions and planning", "B1",
            "An imperfective future commonly uses a future form of быть plus the infinitive, as in буду читать, emphasizing activity, repetition or an unbounded process. A perfective future commonly uses the verb's simple nonpast form, such as прочитаю. These forms are not merely synonyms for 'will': aspect encodes whether the action is presented as a whole or as a process. Time expressions such as завтра and скоро locate events, while aspect supplies another layer of meaning. Distinguish future intention from a promise or guaranteed outcome.",
            "Завтра я буду читать книгу describes a future reading process; Завтра я прочитаю книгу can present future completion. Мы будем работать contrasts with Мы сделаем работу, where the latter presents achieving a result. Neither grammatical form guarantees a future event will actually happen.",
            "Write three plans for tomorrow using imperfective future and three projected results using perfective future. Use the same base activity in at least two paired sentences, and explain how the time marker stays constant while aspect changes the interpretation.",
            "Which is an imperfective future form of читать?",
            listOf("буду читать", "читал", "прочитал"), 'A',
            "Буду плюс the infinitive читать forms the analytic imperfective future."
        ),
        AstraMasterclass.Unit(
            "Verbs of motion with and without prefixes", "B1–B2",
            "Russian motion verbs distinguish unidirectional and multidirectional patterns, such as идти/ходить for walking and ехать/ездить for travel by vehicle. The choice depends on whether the described movement is a particular direction, a habit, a round trip or another context. Prefixes can add meanings of arrival, departure, entry or exit and interact with aspect. A learner should not assume all English 'go' verbs translate to идти. A destination may use в/на with accusative, while a static location may use prepositional case.",
            "Я иду в школу describes movement toward school on foot now, whereas Я хожу в школу каждый день describes a repeated habit. Я еду домой means I am traveling home by a vehicle, while Я езжу домой по выходным describes repeated travel.",
            "Build eight diary statements distinguishing walking versus vehicle travel, one-time movement versus habit, and direction versus location. Add two prefixed verbs such as прийти or уйти, checking that tense and aspect match the intended chronology.",
            "Which best describes a repeated habit of walking to school?",
            listOf("Я иду в школу каждый день", "Я хожу в школу каждый день", "Я еду в школу пешком"), 'B',
            "Ходить commonly expresses repeated or multidirectional movement on foot."
        ),
        AstraMasterclass.Unit(
            "Imperatives, requests and conversational politeness", "A2–B1",
            "Russian uses imperative forms to request, instruct or invite. The choice of singular informal ты versus polite or plural вы changes imperative endings, such as читай versus читайте. Adding пожалуйста can soften a request, but tone and context still matter. Could-you style expressions, such as Не могли бы вы..., can be especially polite. Negated imperatives may have aspect preferences that depend on the intended situation; avoid learning one mechanical rule for every prohibition. Practise repairing misunderstood speech rather than repeating the same words louder.",
            "Читай, пожалуйста is an informal request to read; Читайте, пожалуйста is polite or plural. Не могли бы вы повторить? is a courteous request to repeat. Я не понял(а), повторите, пожалуйста asks the interlocutor to repeat after a comprehension problem.",
            "Role-play a formal conversation asking for directions, a train departure and a repeated explanation. Use two polite imperatives, one conditional request and one clarification phrase. Compare your Russian with a casual dialogue to see what changes beyond just adding пожалуйста.",
            "Which request uses polite/plural address?",
            listOf("Читайте, пожалуйста", "Читай, пожалуйста", "Я читаю"), 'A',
            "Читайте is the вы imperative form, suitable for plural or polite requests."
        ),
        AstraMasterclass.Unit(
            "Numbers, dates, time and case-sensitive quantities", "A2–B1",
            "Russian number phrases interact with case and gender. Один and два can change with gender, and the noun form after numbers depends on the final numeral and syntactic context. For basic nominative counting, один стол, два стола and пять столов follow distinct patterns, while compounds ending in 11–14 create exceptions to a naive last-digit rule. Clock time, dates and prices use additional constructions; accuracy improves when quantities are learned as full phrases. Avoid assuming a number phrase can be translated word for word from English.",
            "Один стол means one table, два стола means two tables, and пять столов means five tables. However, одиннадцать столов uses the genitive plural despite ending in the digit one. Двадцать один стол returns to the one-table pattern in the basic count construction.",
            "Write the Russian phrases for 1, 2, 5, 11, 21 and 22 tables, then do the same with feminine книга. Create a schedule with three times of day and two dates, and label any form you must verify using a grammar reference.",
            "Which nominative quantity phrase is usual for 'five tables'?",
            listOf("пять стол", "пять стола", "пять столов"), 'C',
            "Пять generally takes genitive plural in this count construction: столов."
        ),
        AstraMasterclass.Unit(
            "Relative clauses, conjunctions and reported views", "B2",
            "Russian complex sentences use conjunctions and relative words to connect claims. Который agrees in gender and number with its antecedent but takes case according to its role inside the relative clause. Что, когда, потому что, хотя and если introduce different semantic relations; they are not interchangeable. Russian word order is flexible because case endings carry substantial grammatical information, but order also manages emphasis and topic–comment structure. Longer arguments benefit from tracking what each pronoun refers to, the aspect of subordinate verbs and the strength of evidential claims.",
            "Книга, которую я читаю, интересная means 'The book that I am reading is interesting'; которую is feminine singular accusative because it is the object within the relative clause. Человек, который говорит, мой друг uses который as a subject. Хотя идёт дождь, мы гуляем introduces concession.",
            "Combine eight pairs of sentences into relative and causal clauses using который, потому что and хотя. Identify the relative pronoun's grammatical case in each example and rewrite a paragraph to make its main claim, reason and counterargument explicit.",
            "Which relative form fits 'Книга, ___ я читаю, интересная'?",
            listOf("который", "которую", "которого"), 'B',
            "Которую is feminine singular accusative, agreeing with книга while functioning as the direct object of читаю."
        ),
        AstraMasterclass.Unit(
            "Reading, listening and evidence-based Russian communication", "B1–B2",
            "Competent comprehension requires identifying the purpose, facts, inference, register and reliability of a source, not just recalling dictionary entries. Russian aspect, negation, and discourse particles can change what a speaker asserts. A transcript may not reveal sarcasm, stress or intonation; the audio is needed to evaluate those features. When summarizing news or scientific text, distinguish what the writer claims from what independent evidence confirms. A local language model can practise conversation, but inaccurate grammar or invented source attributions must be checked against dictionaries and native examples.",
            "Compare Он сказал, что эксперимент завершился with Он считает, что эксперимент завершился. The first reports a claim of speech, while the second reports a belief, neither independently proving the experiment occurred. Вчера and завтра change temporal reference while aspect influences how completion is presented.",
            "Select a Russian article or transcript you are legally allowed to use. Write a 120-word Russian summary, identify five claims and distinguish reported speech from evidence. Record a two-minute explanation, review pronunciation, then mark errors in verb aspect, case and discourse reference.",
            "Which approach best handles a claim encountered in Russian media?",
            listOf("Assume it is true because it was translated fluently", "Separate the source's claim from independently verified evidence", "Treat every unfamiliar word as a false statement"), 'B',
            "Translation and source comprehension do not automatically establish the factual truth of what the source reports."
        )
    )
}
