package com.widgetsforobsidian.markdown

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads which note Obsidian has open from `.obsidian/workspace-mobile.json` (or `workspace.json`):
 * the file of the active leaf if it is a markdown note, else the most recent entry of `lastOpenFiles`.
 */
object WorkspaceReader {

    fun currentNote(json: String): String? {
        val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return null
        val activeId = (root["active"] as? JsonPrimitive)?.contentOrNull
        val activeFile = activeId?.let { findLeaf(root["main"], it) }?.let(::leafFile)
        if (activeFile != null && activeFile.endsWith(".md")) return activeFile
        return (root["lastOpenFiles"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.firstOrNull { it.endsWith(".md") }
    }

    private fun findLeaf(node: JsonElement?, id: String): JsonObject? = when (node) {
        is JsonObject -> if ((node["id"] as? JsonPrimitive)?.contentOrNull == id) node
        else node.values.firstNotNullOfOrNull { findLeaf(it, id) }
        is JsonArray -> node.firstNotNullOfOrNull { findLeaf(it, id) }
        else -> null
    }

    private fun leafFile(leaf: JsonObject): String? {
        val state = (leaf["state"] as? JsonObject)?.get("state") as? JsonObject ?: return null
        return (state["file"] as? JsonPrimitive)?.contentOrNull
    }
}
