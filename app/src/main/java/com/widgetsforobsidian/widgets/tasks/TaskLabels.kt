package com.widgetsforobsidian.widgets.tasks

import android.content.Context
import com.widgetsforobsidian.R

/**
 * Words the task widgets show for property values and dates. Kept apart from [TaskFormat] so
 * the formatting rules stay plain Kotlin (unit-tested on the JVM) while the words come from
 * the app's localized resources.
 */
data class TaskLabels(
    val todo: String,
    val doing: String,
    val blocked: String,
    val done: String,
    val inbox: String,
    val high: String,
    val medium: String,
    val low: String,
    val noValue: String,
    val today: String,
    val tomorrow: String,
    val yesterday: String,
    val shortDatePattern: String,
) {
    fun status(key: String): String? = when (key) {
        "todo" -> todo
        "doing" -> doing
        "blocked" -> blocked
        "done" -> done
        else -> null
    }

    fun priority(key: String): String? = when (key) {
        "high" -> high
        "medium" -> medium
        "low" -> low
        else -> null
    }

    /** Status worth flagging under a task title; `todo` and `done` are implied by the checkbox. */
    fun statusBadge(key: String): String? = if (key == "doing" || key == "blocked") status(key) else null

    companion object {
        val ENGLISH = TaskLabels(
            todo = "To do", doing = "In progress", blocked = "Blocked", done = "Done",
            inbox = "Inbox", high = "High", medium = "Medium", low = "Low", noValue = "No value",
            today = "today", tomorrow = "tomorrow", yesterday = "yesterday", shortDatePattern = "MM/dd",
        )

        fun from(context: Context) = TaskLabels(
            todo = context.getString(R.string.status_todo),
            doing = context.getString(R.string.status_doing),
            blocked = context.getString(R.string.status_blocked),
            done = context.getString(R.string.status_done),
            inbox = context.getString(R.string.inbox),
            high = context.getString(R.string.priority_high),
            medium = context.getString(R.string.priority_medium),
            low = context.getString(R.string.priority_low),
            noValue = context.getString(R.string.no_value),
            today = context.getString(R.string.due_today),
            tomorrow = context.getString(R.string.due_tomorrow),
            yesterday = context.getString(R.string.due_yesterday),
            shortDatePattern = context.getString(R.string.short_date_pattern),
        )
    }
}
