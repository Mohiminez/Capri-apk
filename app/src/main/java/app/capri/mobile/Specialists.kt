package app.capri.mobile

// Specialists: the brain that answers is picked by AutoRoute, and the specialist decides HOW it answers. Each one adds a short working brief
// to her prompt for that kind of question (code, writing, translation, maths, study, planning). Ordinary chat gets none, so she stays herself.
enum class Specialist(val id: String, val en: String, val ar: String) {
    CODER("coder", "Coder", "المبرمجة"), WRITER("writer", "Writer", "الكاتبة"), TRANSLATOR("translator", "Translator", "المترجمة"),
    MATHS("maths", "Maths", "الحسابات"), TUTOR("tutor", "Tutor", "المعلمة"), PLANNER("planner", "Planner", "المخططة"), NONE("none", "", "")
}

object Specialists {
    fun pick(text: String, hasImage: Boolean = false): Specialist {
        val t = text.trim()
        if (t.length < 8 || hasImage) return Specialist.NONE
        fun has(re: String) = Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(t)
        return when {
            has("```|\\b(code|function|bug|stack ?trace|exception|python|kotlin|java(script)?|regex|sql|api|compile|script|debug)\\b|كود|برمجة|دالة|سكربت|خطأ برمجي|اصلح الكود") -> Specialist.CODER
            has("\\btranslate\\b|\\bin (arabic|english|french|spanish|german|turkish)\\b|ترجم|ترجمة|بالعربي|بالانجليزي|بالإنجليزي") -> Specialist.TRANSLATOR
            has("\\b(solve|equation|integral|derivative|probability|percent(age)?|calculate|prove)\\b|\\d\\s*[-+*/^x]\\s*\\d|معادلة|احسب|حل المسألة|نسبة مئوية|احتمال") -> Specialist.MATHS
            has("\\b(essay|story|poem|email|letter|cover letter|rewrite|proofread|caption|slogan)\\b|اكتب لي|قصة|قصيدة|رسالة|ايميل|اعد صياغة|صياغة|منشور") -> Specialist.WRITER
            has("\\b(explain|teach me|what is the difference|how does .* work|eli5)\\b|اشرح|علمني|وضح لي|شنو الفرق|ما الفرق") -> Specialist.TUTOR
            has("\\b(plan|schedule|roadmap|steps to|checklist|itinerary)\\b|خطة|جدول|خطوات|قائمة مهام|برنامج") -> Specialist.PLANNER
            else -> Specialist.NONE
        }
    }

    /** The brief that rides along in her prompt. Empty for plain chat. */
    fun brief(a: Specialist): String = when (a) {
        Specialist.CODER -> "\nSpecialist (coder): give working code in a fenced block first, then at most three short lines on how it works and one thing to watch for. Name the language. Never invent library functions; say so if unsure."
        Specialist.WRITER -> "\nSpecialist (writer): write the piece itself, in the language and tone asked, ready to send. No preamble. If the request is missing a key detail, make a sensible choice and say it in one line at the end."
        Specialist.TRANSLATOR -> "\nSpecialist (translator): give only the translation, natural and faithful, keeping names and numbers. If the register or dialect matters, add one short note after."
        Specialist.MATHS -> "\nSpecialist (maths): work step by step, check the result by a second route or by substituting back, and give the final answer clearly on its own line."
        Specialist.TUTOR -> "\nSpecialist (tutor): explain simply first in two or three sentences, then one concrete example, then offer to go deeper. No jargon without a definition."
        Specialist.PLANNER -> "\nSpecialist (planner): give a short numbered plan, each step one line with who or what and roughly when. End with the first action to do today."
        Specialist.NONE -> ""
    }

    fun context(text: String, hasImage: Boolean, enabled: Boolean): String = if (enabled) brief(pick(text, hasImage)) else ""
}
