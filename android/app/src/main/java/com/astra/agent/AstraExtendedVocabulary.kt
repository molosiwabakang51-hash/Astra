package com.astra.agent

/** Compact original multilingual glossary. Individual short dictionary equivalents
 * are facts; this pack contains no copied textbook passages or remote inference.
 * Terms are grouped into 9 themes with 8 entries each, so lesson progression
 * can advance in four-card blocks without repeating an entire lesson.
 */
internal object AstraExtendedVocabulary {
    internal data class Entry(val target: String, val english: String)
    private val meanings = "mother|father|child|friend|family|man|woman|person|house|school|hospital|shop|road|village|city|car|food|milk|meat|egg|sugar|salt|vegetable|rice|sun|moon|star|rain|wind|fire|river|mountain|cow|sheep|goat|chicken|dog|cat|bird|lion|head|hand|eye|ear|mouth|nose|heart|foot|teacher|student|doctor|money|time|year|week|book|to eat|to drink|to sleep|to go|to come|to speak|to read|to write|to help|to know|to buy|to see|today|tomorrow|yesterday|peace".split('|')
    private val packs: Map<String, String> = mapOf(
        "setswana" to "mme|rre|ngwana|tsala|lelapa|monna|mosadi|motho|ntlo|sekolo|bookelo|lebenkele|tsela|motse|toropo|koloi|dijo|maswi|nama|lee|sukiri|letswai|merogo|raese|letsatsi|ngwedi|naledi|pula|phefo|molelo|noka|thaba|kgomo|nku|podi|koko|ntša|katse|nonyane|tau|tlhogo|seatla|leitlho|tsebe|molomo|nko|pelo|leoto|morutabana|moithuti|ngaka|madi|nako|ngwaga|beke|buka|go ja|go nwa|go robala|go tsamaya|go tla|go bua|go bala|go kwala|go thusa|go itse|go reka|go bona|gompieno|kamoso|maabane|kagiso",
        "french" to "mère|père|enfant|ami|famille|homme|femme|personne|maison|école|hôpital|magasin|route|village|ville|voiture|nourriture|lait|viande|œuf|sucre|sel|légume|riz|soleil|lune|étoile|pluie|vent|feu|rivière|montagne|vache|mouton|chèvre|poulet|chien|chat|oiseau|lion|tête|main|œil|oreille|bouche|nez|cœur|pied|professeur|étudiant|médecin|argent|temps|année|semaine|livre|manger|boire|dormir|aller|venir|parler|lire|écrire|aider|savoir|acheter|voir|aujourd'hui|demain|hier|paix",
        "spanish" to "madre|padre|niño|amigo|familia|hombre|mujer|persona|casa|escuela|hospital|tienda|carretera|pueblo|ciudad|coche|comida|leche|carne|huevo|azúcar|sal|verdura|arroz|sol|luna|estrella|lluvia|viento|fuego|río|montaña|vaca|oveja|cabra|pollo|perro|gato|pájaro|león|cabeza|mano|ojo|oreja|boca|nariz|corazón|pie|profesor|estudiante|médico|dinero|tiempo|año|semana|libro|comer|beber|dormir|ir|venir|hablar|leer|escribir|ayudar|saber|comprar|ver|hoy|mañana|ayer|paz",
        "portuguese" to "mãe|pai|criança|amigo|família|homem|mulher|pessoa|casa|escola|hospital|loja|estrada|aldeia|cidade|carro|comida|leite|carne|ovo|açúcar|sal|legume|arroz|sol|lua|estrela|chuva|vento|fogo|rio|montanha|vaca|ovelha|cabra|galinha|cão|gato|pássaro|leão|cabeça|mão|olho|orelha|boca|nariz|coração|pé|professor|estudante|médico|dinheiro|tempo|ano|semana|livro|comer|beber|dormir|ir|vir|falar|ler|escrever|ajudar|saber|comprar|ver|hoje|amanhã|ontem|paz",
        "german" to "Mutter|Vater|Kind|Freund|Familie|Mann|Frau|Person|Haus|Schule|Krankenhaus|Geschäft|Straße|Dorf|Stadt|Auto|Essen|Milch|Fleisch|Ei|Zucker|Salz|Gemüse|Reis|Sonne|Mond|Stern|Regen|Wind|Feuer|Fluss|Berg|Kuh|Schaf|Ziege|Huhn|Hund|Katze|Vogel|Löwe|Kopf|Hand|Auge|Ohr|Mund|Nase|Herz|Fuß|Lehrer|Schüler|Arzt|Geld|Zeit|Jahr|Woche|Buch|essen|trinken|schlafen|gehen|kommen|sprechen|lesen|schreiben|helfen|wissen|kaufen|sehen|heute|morgen|gestern|Frieden",
        "swahili" to "mama|baba|mtoto|rafiki|familia|mwanaume|mwanamke|mtu|nyumba|shule|hospitali|duka|barabara|kijiji|mji|gari|chakula|maziwa|nyama|yai|sukari|chumvi|mboga|wali|jua|mwezi|nyota|mvua|upepo|moto|mto|mlima|ng'ombe|kondoo|mbuzi|kuku|mbwa|paka|ndege|simba|kichwa|mkono|jicho|sikio|mdomo|pua|moyo|mguu|mwalimu|mwanafunzi|daktari|pesa|wakati|mwaka|wiki|kitabu|kula|kunywa|kulala|kwenda|kuja|kuzungumza|kusoma|kuandika|kusaidia|kujua|kununua|kuona|leo|kesho|jana|amani",
        "japanese" to "母|父|子供|友達|家族|男性|女性|人|家|学校|病院|店|道|村|町|車|食べ物|牛乳|肉|卵|砂糖|塩|野菜|ご飯|太陽|月|星|雨|風|火|川|山|牛|羊|ヤギ|鶏|犬|猫|鳥|ライオン|頭|手|目|耳|口|鼻|心臓|足|先生|学生|医者|お金|時間|年|週|本|食べる|飲む|寝る|行く|来る|話す|読む|書く|助ける|知る|買う|見る|今日|明日|昨日|平和",
        "mandarin" to "母亲|父亲|孩子|朋友|家人|男人|女人|人|房子|学校|医院|商店|路|村庄|城市|汽车|食物|牛奶|肉|鸡蛋|糖|盐|蔬菜|米饭|太阳|月亮|星星|雨|风|火|河|山|牛|羊|山羊|鸡|狗|猫|鸟|狮子|头|手|眼睛|耳朵|嘴|鼻子|心脏|脚|老师|学生|医生|钱|时间|年|星期|书|吃|喝|睡觉|去|来|说话|读|写|帮助|知道|买|看见|今天|明天|昨天|和平"
    )

    fun additional(languageId: String): List<Entry> {
        val source = packs[languageId] ?: return emptyList()
        val native = source.split('|')
        check(native.size == meanings.size && meanings.size == 72) {
            "Multilingual glossary columns are not aligned."
        }
        return native.indices.map { i ->
            Entry(native[i].trim(), meanings[i].trim())
        }
    }
}
