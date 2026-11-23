package com.widgetsforobsidian.bases

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

data class SortSpec(val property: String, val descending: Boolean)

data class BaseView(
    val index: Int,
    val type: String,
    val name: String,
    val filters: Expr?,
    val order: List<String>,
    val sort: List<SortSpec>,
    /** Primary grouping property: core `groupBy.property` or Power Bases `pbGroup`. */
    val groupBy: String?,
    /** Secondary grouping (Power Bases board swimlanes, `pbRows`). */
    val rowsBy: String?,
    val limit: Int?,
) {
    /** Views a list-style widget can show. Calendar, chart, timeline and map views cannot. */
    val isListLike: Boolean
        get() = type in LIST_LIKE_TYPES

    companion object {
        val LIST_LIKE_TYPES = setOf(
            "table", "list", "cards",
            "powerbases-board", "powerbases-table", "powerbases-gallery", "powerbases-list",
        )
    }
}

data class BaseDefinition(
    val filters: Expr?,
    val formulas: Map<String, Expr>,
    val displayNames: Map<String, String>,
    val views: List<BaseView>,
) {
    fun displayName(propertyId: String): String =
        displayNames[propertyId] ?: displayNames["note.$propertyId"]
            ?: propertyId.substringAfter("note.").substringAfter("file.")
}

/** Parses the YAML of an Obsidian `.base` file. */
object BaseParser {

    fun parse(yaml: String): BaseDefinition {
        val root = Load(LoadSettings.builder().build()).loadFromString(yaml) as? Map<*, *>
            ?: throw ExpressionException("a .base file must be a YAML mapping")

        val formulas = (root["formulas"] as? Map<*, *>).orEmpty().entries
            .associate { (k, v) -> k.toString() to ExprParser.parse(v.toString()) }

        val displayNames = (root["properties"] as? Map<*, *>).orEmpty().entries.mapNotNull { (k, v) ->
            ((v as? Map<*, *>)?.get("displayName") as? String)?.let { k.toString() to it }
        }.toMap()

        val views = (root["views"] as? List<*>).orEmpty().mapIndexedNotNull { i, v ->
            (v as? Map<*, *>)?.let { parseView(i, it) }
        }

        return BaseDefinition(parseFilter(root["filters"]), formulas, displayNames, views)
    }

    private fun parseView(index: Int, v: Map<*, *>): BaseView {
        val groupBy = when (val g = v["groupBy"]) {
            is Map<*, *> -> g["property"]?.toString()
            is String -> g
            else -> null
        } ?: v["pbGroup"]?.toString()

        return BaseView(
            index = index,
            type = v["type"]?.toString() ?: "table",
            name = v["name"]?.toString() ?: "View ${index + 1}",
            filters = parseFilter(v["filters"]),
            order = (v["order"] as? List<*>).orEmpty().map { it.toString() },
            sort = (v["sort"] as? List<*>).orEmpty().mapNotNull { s ->
                val m = s as? Map<*, *> ?: return@mapNotNull null
                val property = m["property"]?.toString() ?: return@mapNotNull null
                SortSpec(property, m["direction"]?.toString().equals("DESC", ignoreCase = true))
            },
            groupBy = groupBy,
            rowsBy = v["pbRows"]?.toString(),
            limit = (v["limit"] as? Number)?.toInt(),
        )
    }

    /** A filter is an expression string or a mapping with a single `and` / `or` / `not` list. */
    fun parseFilter(node: Any?): Expr? = when (node) {
        null -> null
        is String -> ExprParser.parse(node)
        is Map<*, *> -> {
            val (key, value) = node.entries.singleOrNull()
                ?: throw ExpressionException("filter group must have exactly one of and/or/not")
            val items = (value as? List<*>).orEmpty().mapNotNull(::parseFilter)
            when (key.toString()) {
                "and" -> Expr.And(items)
                "or" -> Expr.Or(items)
                "not" -> Expr.None(items)
                else -> throw ExpressionException("unknown filter group '$key'")
            }
        }
        is Boolean -> Expr.Literal(node)
        else -> throw ExpressionException("invalid filter: $node")
    }
}
