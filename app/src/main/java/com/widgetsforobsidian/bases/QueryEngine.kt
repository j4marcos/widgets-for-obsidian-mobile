package com.widgetsforobsidian.bases

import java.time.LocalDate

data class ResultItem(val row: NoteRow, val evaluator: Evaluator) {
    fun value(propertyId: String): Any? = runCatching { evaluator.property(propertyId) }.getOrNull()
}

data class ResultGroup(
    /** Value of the swimlane property (`pbRows`), or null when the view has none. */
    val row: Any?,
    /** Value of the group property, or null when the view is not grouped. */
    val group: Any?,
    val items: List<ResultItem>,
)

data class QueryResult(
    val view: BaseView,
    val groups: List<ResultGroup>,
    val total: Int,
    /** Expressions the engine could not evaluate; matching rows were kept rather than hidden. */
    val warnings: List<String>,
)

/** Runs one view of a base over the indexed notes: filter, sort, limit, group. */
object QueryEngine {

    /** Known value orders, so groups read like a board (doing before todo before done). */
    private val KNOWN_ORDERS = listOf(
        listOf("doing", "in-progress", "in progress", "todo", "to-do", "backlog", "blocked", "waiting", "done", "cancelled", "canceled"),
        listOf("urgent", "high", "medium", "low", "none"),
    )

    fun run(base: BaseDefinition, viewIndex: Int, rows: List<NoteRow>, today: LocalDate = LocalDate.now()): QueryResult {
        val view = base.views.firstOrNull { it.index == viewIndex } ?: base.views.firstOrNull()
            ?: BaseView(0, "table", "Default", null, emptyList(), emptyList(), null, null, null)
        val warnings = LinkedHashSet<String>()

        val matched = rows.asSequence()
            .map { ResultItem(it, Evaluator(it, base.formulas, today)) }
            .filter { item ->
                listOfNotNull(base.filters, view.filters).all { test(it, item.evaluator, warnings) != false }
            }
            .toMutableList()

        if (view.sort.isNotEmpty()) matched.sortWith(sortComparator(view.sort))
        val limited = view.limit?.let { matched.take(it) } ?: matched

        val groups = if (view.groupBy == null && view.rowsBy == null) {
            listOf(ResultGroup(null, null, limited))
        } else {
            limited.groupBy { it.groupValue(view.rowsBy) to it.groupValue(view.groupBy) }
                .map { (key, items) -> ResultGroup(key.first, key.second, items) }
                .sortedWith(
                    compareBy<ResultGroup, Any?>(GROUP_ORDER) { it.row }.thenBy(GROUP_ORDER) { it.group },
                )
        }
        return QueryResult(view, groups, limited.size, warnings.toList())
    }

    /** Rows that pass the base-level filters only (no view filters). */
    fun matching(base: BaseDefinition, rows: List<NoteRow>, today: LocalDate = LocalDate.now()): List<NoteRow> {
        val filter = base.filters ?: return rows
        val ignored = mutableSetOf<String>()
        return rows.filter { test(filter, Evaluator(it, base.formulas, today), ignored) != false }
    }

    /** Returns null when the expression uses something this engine does not support. */
    private fun test(expr: Expr, ev: Evaluator, warnings: MutableSet<String>): Boolean? = when (expr) {
        is Expr.And -> expr.items.all { test(it, ev, warnings) != false }
        is Expr.Or -> expr.items.map { test(it, ev, warnings) }
            .let { results -> results.any { it == true } || results.isNotEmpty() && results.all { it == null } }
        is Expr.None -> expr.items.none { test(it, ev, warnings) == true }
        else -> try {
            Evaluator.truthy(ev.eval(expr))
        } catch (e: ExpressionException) {
            warnings += e.message.orEmpty()
            null
        }
    }

    private fun ResultItem.groupValue(propertyId: String?): Any? {
        if (propertyId == null) return null
        val v = value(propertyId)
        return if (v is List<*>) v.firstOrNull() else v
    }

    /** Missing values always sort last, whatever the direction. */
    private fun sortComparator(specs: List<SortSpec>) = Comparator<ResultItem> { a, b ->
        for (spec in specs) {
            val x = a.value(spec.property)
            val y = b.value(spec.property)
            val c = when {
                Evaluator.isEmpty(x) && Evaluator.isEmpty(y) -> 0
                Evaluator.isEmpty(x) -> 1
                Evaluator.isEmpty(y) -> -1
                else -> (Evaluator.compareValues(x, y) ?: 0).let { if (spec.descending) -it else it }
            }
            if (c != 0) return@Comparator c
        }
        a.row.path.compareTo(b.row.path)
    }

    private val GROUP_ORDER = Comparator<Any?> { a, b ->
        when {
            a == b -> 0
            Evaluator.isEmpty(a) -> 1
            Evaluator.isEmpty(b) -> -1
            else -> {
                val x = Evaluator.display(a).lowercase()
                val y = Evaluator.display(b).lowercase()
                val known = KNOWN_ORDERS.firstOrNull { x in it && y in it }
                known?.let { it.indexOf(x).compareTo(it.indexOf(y)) } ?: (Evaluator.compareValues(a, b) ?: 0)
            }
        }
    }

    /**
     * Folder that every matching note must be in, taken from a top-level `file.inFolder("...")`
     * filter. Lets the caller load only that folder from the index instead of the whole vault.
     */
    fun folderHint(filter: Expr?): String? = when (filter) {
        is Expr.And -> filter.items.firstNotNullOfOrNull(::folderHint)
        is Expr.Call -> {
            val callee = filter.callee
            val arg = filter.args.singleOrNull() as? Expr.Literal
            if (callee is Expr.Member && callee.name == "inFolder" &&
                (callee.target as? Expr.Ident)?.name == "file" && arg?.value is String
            ) (arg.value as String).trim('/') else null
        }
        else -> null
    }
}
