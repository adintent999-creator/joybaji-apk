package com.bypass.studio

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle

private enum class TokenKind { PLAIN, KEYWORD, STRING, NUMBER, COMMENT, TYPE, FUNCTION }

private val keywordSets: Map<String, Set<String>> = mapOf(
    "kotlin" to setOf(
        "val", "var", "fun", "class", "object", "data", "interface", "sealed", "enum",
        "when", "if", "else", "return", "suspend", "override", "private", "public",
        "import", "package", "true", "false", "null", "this", "it", "while", "for",
        "do", "break", "continue", "try", "catch", "finally", "throw", "companion", "init"
    ),
    "js" to setOf(
        "const", "let", "var", "function", "return", "if", "else", "class", "import",
        "export", "from", "default", "async", "await", "new", "true", "false", "null",
        "undefined", "typeof", "interface", "type", "enum", "extends", "implements",
        "public", "private", "while", "for", "break", "continue", "try", "catch"
    ),
    "python" to setOf(
        "def", "class", "return", "if", "elif", "else", "for", "while", "import", "from",
        "as", "with", "try", "except", "finally", "raise", "True", "False", "None",
        "async", "await", "lambda", "pass", "break", "continue", "global", "nonlocal", "yield"
    ),
    "json" to setOf("true", "false", "null"),
    "html" to setOf("html", "head", "body", "div", "span", "script", "style", "meta", "link", "input", "button"),
    "css" to setOf("color", "background", "margin", "padding", "display", "flex", "grid", "font", "border", "width", "height")
)

fun languageFromFileName(name: String?): String = when {
    name == null -> "kotlin"
    name.endsWith(".kt", true) || name.endsWith(".kts", true) -> "kotlin"
    name.endsWith(".js", true) || name.endsWith(".jsx", true) || name.endsWith(".mjs", true) -> "js"
    name.endsWith(".ts", true) || name.endsWith(".tsx", true) -> "js"
    name.endsWith(".py", true) -> "python"
    name.endsWith(".html", true) || name.endsWith(".htm", true) || name.endsWith(".xml", true) -> "html"
    name.endsWith(".css", true) || name.endsWith(".scss", true) -> "css"
    name.endsWith(".json", true) -> "json"
    else -> "kotlin"
}

fun codeVisualTransformation(theme: StudioTheme, language: String): VisualTransformation {
    val p = paletteFor(theme)
    return VisualTransformation { text ->
        TransformedText(highlightCode(text.text, language, p), OffsetMapping.Identity)
    }
}

private fun highlightCode(code: String, language: String, p: Palette): AnnotatedString {
    val keywords = keywordSets[language].orEmpty()
    return buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '\n' -> {
                    append(c)
                    i++
                }
                c == '/' && i + 1 < code.length && code[i + 1] == '/' -> {
                    val start = i
                    while (i < code.length && code[i] != '\n') i++
                    withStyle(SpanStyle(color = p.muted)) { append(code.substring(start, i)) }
                }
                c == '#' && (language == "python" || language == "css") -> {
                    val start = i
                    while (i < code.length && code[i] != '\n') i++
                    withStyle(SpanStyle(color = p.muted)) { append(code.substring(start, i)) }
                }
                c == '<' && i + 3 < code.length && code.startsWith("<!--", i) -> {
                    val end = code.indexOf("-->", i + 4)
                    val stop = if (end < 0) code.length else end + 3
                    withStyle(SpanStyle(color = p.muted)) { append(code.substring(i, stop)) }
                    i = stop
                }
                c == '"' || c == '\'' || c == '`' -> {
                    val quote = c
                    val start = i
                    i++
                    while (i < code.length) {
                        if (code[i] == '\\') {
                            i += 2
                            continue
                        }
                        if (code[i] == quote) {
                            i++
                            break
                        }
                        if (code[i] == '\n') break
                        i++
                    }
                    withStyle(SpanStyle(color = p.green)) { append(code.substring(start, i.coerceAtMost(code.length))) }
                }
                c.isDigit() -> {
                    val start = i
                    while (i < code.length && (code[i].isDigit() || code[i] == '.' || code[i] == 'x' || code[i] == 'X')) i++
                    withStyle(SpanStyle(color = p.accent)) { append(code.substring(start, i)) }
                }
                c.isLetter() || c == '_' || c == '$' -> {
                    val start = i
                    while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_' || code[i] == '$')) i++
                    val word = code.substring(start, i)
                    var j = i
                    while (j < code.length && code[j] == ' ') j++
                    val kind = when {
                        keywords.contains(word) -> TokenKind.KEYWORD
                        j < code.length && code[j] == '(' -> TokenKind.FUNCTION
                        word.firstOrNull()?.isUpperCase() == true -> TokenKind.TYPE
                        else -> TokenKind.PLAIN
                    }
                    val color = when (kind) {
                        TokenKind.KEYWORD -> p.accent
                        TokenKind.FUNCTION -> p.text
                        TokenKind.TYPE -> p.green
                        else -> p.text
                    }
                    withStyle(SpanStyle(color = color)) { append(word) }
                }
                else -> {
                    withStyle(SpanStyle(color = Color.Unspecified)) { append(c) }
                    i++
                }
            }
        }
    }
}
