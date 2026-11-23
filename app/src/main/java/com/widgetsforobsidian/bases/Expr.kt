package com.widgetsforobsidian.bases

/** AST of the Obsidian Bases expression language (the subset this app understands). */
sealed interface Expr {
    data class Literal(val value: Any?) : Expr
    data class Ident(val name: String) : Expr
    data class Member(val target: Expr, val name: String) : Expr
    data class Index(val target: Expr, val index: Expr) : Expr
    data class Call(val callee: Expr, val args: List<Expr>) : Expr
    data class Unary(val op: String, val operand: Expr) : Expr
    data class Binary(val op: String, val left: Expr, val right: Expr) : Expr
    data class ListLiteral(val items: List<Expr>) : Expr

    /** YAML filter groups: `and:` / `or:` / `not:` lists. `not` is true when none of its items is. */
    data class And(val items: List<Expr>) : Expr
    data class Or(val items: List<Expr>) : Expr
    data class None(val items: List<Expr>) : Expr
}

class ExpressionException(message: String) : RuntimeException(message)

/** Precedence-climbing parser for expressions such as `file.inFolder("x") && status != "done"`. */
class ExprParser private constructor(private val source: String) {

    private val tokens = tokenize(source)
    private var pos = 0

    companion object {
        fun parse(source: String): Expr {
            val parser = ExprParser(source)
            val expr = parser.parseOr()
            if (parser.peek().type != T.EOF) parser.fail("unexpected '${parser.peek().text}'")
            return expr
        }

        private val BINARY_LEVELS = listOf(
            setOf("||"),
            setOf("&&"),
            setOf("==", "!="),
            setOf("<", "<=", ">", ">="),
            setOf("+", "-"),
            setOf("*", "/", "%"),
        )
    }

    private enum class T { NUM, STR, IDENT, OP, EOF }
    private data class Token(val type: T, val text: String, val value: Any? = null)

    private fun parseOr(): Expr = parseLevel(0)

    private fun parseLevel(level: Int): Expr {
        if (level == BINARY_LEVELS.size) return parseUnary()
        var left = parseLevel(level + 1)
        while (peek().type == T.OP && peek().text in BINARY_LEVELS[level]) {
            val op = next().text
            left = Expr.Binary(op, left, parseLevel(level + 1))
        }
        return left
    }

    private fun parseUnary(): Expr {
        val t = peek()
        if (t.type == T.OP && (t.text == "!" || t.text == "-")) {
            next()
            return Expr.Unary(t.text, parseUnary())
        }
        return parsePostfix(parsePrimary())
    }

    private fun parsePostfix(start: Expr): Expr {
        var expr = start
        while (true) {
            expr = when {
                accept(".") -> {
                    val name = next()
                    if (name.type != T.IDENT) fail("expected name after '.'")
                    Expr.Member(expr, name.text)
                }
                accept("(") -> Expr.Call(expr, parseArgs(")"))
                accept("[") -> Expr.Index(expr, parseOr()).also { expect("]") }
                else -> return expr
            }
        }
    }

    private fun parsePrimary(): Expr {
        val t = next()
        return when (t.type) {
            T.NUM, T.STR -> Expr.Literal(t.value)
            T.IDENT -> when (t.text) {
                "true" -> Expr.Literal(true)
                "false" -> Expr.Literal(false)
                "null" -> Expr.Literal(null)
                else -> Expr.Ident(t.text)
            }
            T.OP -> when (t.text) {
                "(" -> parseOr().also { expect(")") }
                "[" -> Expr.ListLiteral(parseArgs("]"))
                else -> fail("unexpected '${t.text}'")
            }
            T.EOF -> fail("unexpected end of expression")
        }
    }

    private fun parseArgs(close: String): List<Expr> {
        val args = mutableListOf<Expr>()
        if (accept(close)) return args
        do args += parseOr() while (accept(","))
        expect(close)
        return args
    }

    private fun peek() = tokens[pos]
    private fun next() = tokens[pos].also { if (it.type != T.EOF) pos++ }
    private fun accept(op: String): Boolean {
        val t = peek()
        if (t.type == T.OP && t.text == op) { pos++; return true }
        return false
    }
    private fun expect(op: String) { if (!accept(op)) fail("expected '$op'") }
    private fun fail(msg: String): Nothing = throw ExpressionException("$msg in: $source")

    private fun tokenize(s: String): List<Token> {
        val out = mutableListOf<Token>()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    val text = s.substring(start, i)
                    out += Token(T.NUM, text, text.toDouble())
                }
                c == '"' || c == '\'' -> {
                    val sb = StringBuilder()
                    i++
                    while (i < s.length && s[i] != c) {
                        if (s[i] == '\\' && i + 1 < s.length) i++
                        sb.append(s[i++])
                    }
                    if (i >= s.length) throw ExpressionException("unterminated string in: $s")
                    i++
                    out += Token(T.STR, sb.toString(), sb.toString())
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    out += Token(T.IDENT, s.substring(start, i))
                }
                else -> {
                    val two = if (i + 1 < s.length) s.substring(i, i + 2) else ""
                    val op = if (two in setOf("==", "!=", "<=", ">=", "&&", "||")) two else c.toString()
                    if (op.length == 1 && op !in "()[],.!<>+-*/%") throw ExpressionException("unexpected '$c' in: $s")
                    out += Token(T.OP, op)
                    i += op.length
                }
            }
        }
        out += Token(T.EOF, "")
        return out
    }
}
