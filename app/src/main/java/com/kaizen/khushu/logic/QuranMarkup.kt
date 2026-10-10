package com.kaizen.khushu.logic

/** Preserve source text while removing markup and separately represented verse/footnote markers. */
object QuranMarkup {
    data class Segment(val text: String, val rules: List<String>)
    private data class Tag(val name: String, val rule: String?, val hidden: Boolean)
    private val entities = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
    private val voidTags = setOf("br", "hr", "img")
    private val tags = Regex("<[^>]*>")
    private val classAttribute = Regex("""\bclass\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")

    fun segments(markup: String): List<Segment> {
        val stack = mutableListOf<Tag>()
        val result = mutableListOf<Segment>()
        fun append(text: String) {
            if (text.isNotEmpty() && stack.none { it.hidden }) {
                result += Segment(decodeEntities(text), stack.mapNotNull { it.rule })
            }
        }
        var cursor = 0
        for (match in tags.findAll(markup)) {
            append(markup.substring(cursor, match.range.first))
            val body = match.value.substring(1, match.value.length - 1).trim()
            val closing = body.startsWith('/')
            val name = body.trimStart('/').takeWhile { it.isLetterOrDigit() }.lowercase()
            if (closing) {
                val index = stack.indexOfLast { it.name == name }
                if (index >= 0) while (stack.size > index) stack.removeAt(stack.lastIndex)
            } else if (!body.endsWith('/') && name !in voidTags) {
                val classes = classAttribute.find(body)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
                stack += Tag(name, classes?.takeIf { name == "tajweed" },
                    name == "sup" || name == "span" && classes?.split(' ')?.contains("end") == true)
            } else if (name == "br") append("\n")
            cursor = match.range.last + 1
        }
        append(markup.substring(cursor))
        return result
    }

    fun plainText(markup: String): String = if ('<' !in markup) decodeEntities(markup).trim()
        else segments(markup).joinToString("") { it.text }.trim()

    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return entities.replace(text) { match ->
            val entity = match.groupValues[1]
            when (entity) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                else -> {
                    val number = when {
                        entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)
                        entity.startsWith('#') -> entity.drop(1).toIntOrNull()
                        else -> null
                    }
                    if (number != null && Character.isValidCodePoint(number) && number !in 0xD800..0xDFFF)
                        String(Character.toChars(number)) else match.value
                }
            }
        }
    }
}
