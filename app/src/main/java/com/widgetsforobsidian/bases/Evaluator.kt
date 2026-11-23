package com.widgetsforobsidian.bases

import java.time.LocalDate

/** A note as seen by the query engine. [properties] holds plain Kotlin values (String, Double, Boolean, List, Map, null). */
data class NoteRow(
    val path: String,
    val basename: String,
    val folder: String,
    val extension: String,
    val mtime: Long,
    val size: Long,
    val properties: Map<String, Any?>,
    val tags: List<String>,
    val links: List<String>,
)

/** Evaluates [Expr]s against one [NoteRow]. Formulas are evaluated lazily and cached per row. */
class Evaluator(
    private val row: NoteRow,
    private val formulas: Map<String, Expr>,
    private val today: LocalDate,
) {
    private object FileScope
    private object NoteScope
    private object FormulaScope

    private val formulaCache = HashMap<String, Any?>()
    private val evaluating = HashSet<String>()

    /** Resolves a property id as used in `order`, `sort` and `pbGroup`: `status`, `note.status`, `formula.x`, `file.name`. */
    fun property(id: String): Any? {
        val dot = id.indexOf('.')
        if (dot < 0) return row.properties[id]
        val scope = id.substring(0, dot)
        val name = id.substring(dot + 1)
        return when (scope) {
            "note" -> row.properties[name]
            "formula" -> formula(name)
            "file" -> fileMember(name)
            else -> row.properties[id]
        }
    }

    fun eval(expr: Expr): Any? = when (expr) {
        is Expr.Literal -> expr.value
        is Expr.Ident -> when (expr.name) {
            "file" -> FileScope
            "note" -> NoteScope
            "formula" -> FormulaScope
            "this" -> NoteScope
            else -> row.properties[expr.name]
        }
        is Expr.Member -> member(eval(expr.target), expr.name)
        is Expr.Index -> {
            val target = eval(expr.target)
            val index = eval(expr.index)
            when {
                target is List<*> && index is Double -> target.getOrNull(index.toInt())
                target is Map<*, *> -> target[index.toString()]
                target === NoteScope -> row.properties[index.toString()]
                else -> null
            }
        }
        is Expr.Call -> call(expr)
        is Expr.Unary -> when (expr.op) {
            "!" -> !truthy(eval(expr.operand))
            "-" -> toNumber(eval(expr.operand))?.let { -it }
            else -> unsupported("operator ${expr.op}")
        }
        is Expr.Binary -> binary(expr)
        is Expr.ListLiteral -> expr.items.map(::eval)
        is Expr.And -> expr.items.all { truthy(eval(it)) }
        is Expr.Or -> expr.items.any { truthy(eval(it)) }
        is Expr.None -> expr.items.none { truthy(eval(it)) }
    }

    private fun formula(name: String): Any? {
        if (formulaCache.containsKey(name)) return formulaCache[name]
        val expr = formulas[name] ?: return null
        if (!evaluating.add(name)) unsupported("circular formula $name")
        val value = try { eval(expr) } finally { evaluating.remove(name) }
        formulaCache[name] = value
        return value
    }

    private fun member(target: Any?, name: String): Any? = when {
        target === FileScope -> fileMember(name)
        target === NoteScope -> row.properties[name]
        target === FormulaScope -> formula(name)
        target is Map<*, *> -> target[name]
        name == "length" && target is String -> target.length.toDouble()
        name == "length" && target is List<*> -> target.size.toDouble()
        else -> null
    }

    private fun fileMember(name: String): Any? = when (name) {
        "name" -> if (row.extension.isEmpty()) row.basename else "${row.basename}.${row.extension}"
        "basename" -> row.basename
        "path" -> row.path
        "folder" -> row.folder
        "ext" -> row.extension
        "mtime" -> row.mtime.toDouble()
        "size" -> row.size.toDouble()
        "tags" -> row.tags
        "links" -> row.links
        "properties" -> row.properties
        else -> null
    }

    private fun call(expr: Expr.Call): Any? {
        val callee = expr.callee
        val args = { expr.args.map(::eval) }
        if (callee is Expr.Member) {
            val target = eval(callee.target)
            return method(target, callee.name, args())
        }
        if (callee !is Expr.Ident) unsupported("call")
        return when (callee.name) {
            // `if` evaluates only the chosen branch.
            "if" -> {
                if (expr.args.isEmpty()) unsupported("if()")
                if (truthy(eval(expr.args[0]))) expr.args.getOrNull(1)?.let(::eval)
                else expr.args.getOrNull(2)?.let(::eval)
            }
            "today" -> today.toString()
            "now" -> System.currentTimeMillis().toDouble()
            "date" -> args().firstOrNull()?.toString()?.take(10)
            "number" -> toNumber(args().firstOrNull())
            "list" -> args().firstOrNull().let { if (it is List<*>) it else listOf(it) }
            "min" -> args().mapNotNull(::toNumber).minOrNull()
            "max" -> args().mapNotNull(::toNumber).maxOrNull()
            else -> unsupported("function ${callee.name}()")
        }
    }

    private fun method(target: Any?, name: String, args: List<Any?>): Any? {
        if (target === FileScope) return fileMethod(name, args)
        return when (name) {
            "isEmpty" -> isEmpty(target)
            "isTruthy" -> truthy(target)
            "toString" -> display(target)
            "contains" -> when (target) {
                is String -> target.contains(display(args.firstOrNull()), ignoreCase = true)
                is List<*> -> target.any { looseEquals(it, args.firstOrNull()) }
                else -> false
            }
            "containsAny" -> (target as? List<*>)?.any { item -> args.any { looseEquals(item, it) } } ?: false
            "containsAll" -> (target as? List<*>)?.let { list -> args.all { a -> list.any { looseEquals(it, a) } } } ?: false
            "startsWith" -> (target as? String)?.startsWith(display(args.firstOrNull())) ?: false
            "endsWith" -> (target as? String)?.endsWith(display(args.firstOrNull())) ?: false
            "lower" -> (target as? String)?.lowercase()
            "upper" -> (target as? String)?.uppercase()
            "trim" -> (target as? String)?.trim()
            "join" -> (target as? List<*>)?.joinToString(display(args.firstOrNull() ?: ", ")) { display(it) }
            else -> unsupported("method .$name()")
        }
    }

    private fun fileMethod(name: String, args: List<Any?>): Any? = when (name) {
        "inFolder" -> {
            val folder = display(args.firstOrNull()).trim('/')
            folder.isEmpty() || row.folder == folder || row.folder.startsWith("$folder/")
        }
        "hasTag" -> args.any { arg ->
            val tag = display(arg).removePrefix("#").lowercase()
            row.tags.any { it.lowercase() == tag || it.lowercase().startsWith("$tag/") }
        }
        "hasLink" -> {
            val target = linkKey(display(args.firstOrNull()))
            row.links.any { linkKey(it) == target }
        }
        "hasProperty" -> row.properties.containsKey(display(args.firstOrNull()))
        else -> unsupported("method file.$name()")
    }

    private fun binary(expr: Expr.Binary): Any? {
        if (expr.op == "&&") return truthy(eval(expr.left)) && truthy(eval(expr.right))
        if (expr.op == "||") return truthy(eval(expr.left)) || truthy(eval(expr.right))
        val l = eval(expr.left)
        val r = eval(expr.right)
        return when (expr.op) {
            "==" -> looseEquals(l, r)
            "!=" -> !looseEquals(l, r)
            "<" -> compareValues(l, r)?.let { it < 0 } ?: false
            "<=" -> compareValues(l, r)?.let { it <= 0 } ?: false
            ">" -> compareValues(l, r)?.let { it > 0 } ?: false
            ">=" -> compareValues(l, r)?.let { it >= 0 } ?: false
            "+" -> {
                val a = l as? Double
                val b = r as? Double
                if (a != null && b != null) a + b else display(l) + display(r)
            }
            "-", "*", "/", "%" -> {
                val a = toNumber(l) ?: return null
                val b = toNumber(r) ?: return null
                when (expr.op) {
                    "-" -> a - b
                    "*" -> a * b
                    "/" -> if (b == 0.0) null else a / b
                    else -> if (b == 0.0) null else a % b
                }
            }
            else -> unsupported("operator ${expr.op}")
        }
    }

    private fun unsupported(what: String): Nothing = throw ExpressionException("unsupported $what")

    companion object {
        fun truthy(v: Any?): Boolean = when (v) {
            null -> false
            is Boolean -> v
            is Double -> v != 0.0
            is String -> v.isNotEmpty()
            is Collection<*> -> v.isNotEmpty()
            is Map<*, *> -> v.isNotEmpty()
            else -> true
        }

        fun isEmpty(v: Any?): Boolean = when (v) {
            null -> true
            is String -> v.isEmpty()
            is Collection<*> -> v.isEmpty()
            is Map<*, *> -> v.isEmpty()
            else -> false
        }

        fun toNumber(v: Any?): Double? = when (v) {
            is Double -> v
            is Number -> v.toDouble()
            is Boolean -> if (v) 1.0 else 0.0
            is String -> v.trim().toDoubleOrNull()
            else -> null
        }

        fun display(v: Any?): String = when (v) {
            null -> ""
            is Double -> if (v % 1.0 == 0.0 && v in -1e15..1e15) v.toLong().toString() else v.toString()
            is List<*> -> v.joinToString(", ") { display(it) }
            else -> v.toString()
        }

        fun looseEquals(a: Any?, b: Any?): Boolean {
            if (a == null || b == null) return a == null && b == null || isEmpty(a) && isEmpty(b)
            if (a is Double || b is Double) {
                val x = toNumber(a)
                val y = toNumber(b)
                if (x != null && y != null) return x == y
            }
            if (a is Boolean || b is Boolean) return truthy(a) == truthy(b)
            if (a is String && b is String) return a == b || linkKey(a) == linkKey(b) && (isLink(a) || isLink(b))
            return a == b
        }

        /** Ordering used by comparisons and sorting. Returns null when one side is missing. */
        fun compareValues(a: Any?, b: Any?): Int? {
            if (a == null || b == null) return null
            val x = toNumber(a)
            val y = toNumber(b)
            if (x != null && y != null && (a !is String || b !is String)) return x.compareTo(y)
            return display(a).compareTo(display(b), ignoreCase = true)
        }

        private fun isLink(s: String) = s.startsWith("[[") && s.endsWith("]]")

        /** Normalizes `[[folder/Note.md|alias]]`, `folder/Note` or `Note` to `note` for link matching. */
        fun linkKey(link: String): String = link.trim()
            .removePrefix("[[").removeSuffix("]]")
            .substringBefore('|').substringBefore('#')
            .substringAfterLast('/').removeSuffix(".md")
            .trim().lowercase()
    }
}
