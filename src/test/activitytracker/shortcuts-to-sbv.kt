package activitytracker

import activitytracker.TrackerEvent.Type.KeyEvent
import org.joda.time.DateTime
import org.joda.time.DateTimeFieldType.*
import org.joda.time.format.DateTimeFormatterBuilder
import java.awt.event.InputEvent
import java.lang.reflect.Modifier
import kotlin.coroutines.experimental.buildSequence

/**
 * Script to read IDE shortcuts from events log and
 * produce output compatible with .sbv file format.
 */
fun main(args: Array<String>) {
    val eventsFilePath = "2019-01-11.csv"
    val shortcuts = TrackerLog(eventsFilePath)
        .readEvents(onParseError = printError)
        .toList()
        .filter { it.type == KeyEvent }
        .mapNotNull { event ->
            val (char, code, modifierFlags) = event.data.split(':').map(String::toInt)
            val keyName = code.toPrintableKeyName()
            val modifiers = modifierFlags.toPrintableModifiers()

            val shouldLogShortcut = char != 65535 && keyName != "Undefined" &&
                (modifiers.isNotEmpty() || keyName == "Tab" || keyName == "Escape") &&
                !(modifiers == listOf("Shift") && (keyName.length == 1 || keyName == "Colon")) // e.g Shift+A

            if (shouldLogShortcut) Shortcut(event.time, (modifiers + keyName).joinToString("+")) else null
        }

    val firstShortcut = shortcuts.first()
    shortcuts
        .map { it.copy(time = it.time.minusMillis(firstShortcut.time.millisOfDay)) }
        .chunkedBy { it.time.secondOfMinute().get() / 5 }
        .forEach { chunk ->
            val fromTime = chunk.first().time
            val toTime = chunk.last().time.let {
                if (it == fromTime) it.plusMillis(500) else it
            }
            println("${fromTime.toSbvTimeFormat()},${toTime.toSbvTimeFormat()}")
            println(chunk.map { it.text }.joinToString(" "))
            println()
        }
}

private data class Shortcut(val time: DateTime, val text: String)

private val sbvTimeFormatter = DateTimeFormatterBuilder()
    .appendFixedDecimal(hourOfDay(), 1).appendLiteral(':')
    .appendFixedDecimal(minuteOfHour(), 2).appendLiteral(':')
    .appendFixedDecimal(secondOfMinute(), 2).appendLiteral('.')
    .appendFixedDecimal(millisOfSecond(), 3)
    .toFormatter()!!

private fun DateTime.toSbvTimeFormat(): String = sbvTimeFormatter.print(this)

private val keyNameByCode = java.awt.event.KeyEvent::class.java.declaredFields
    .filter { it.name.startsWith("VK_") && Modifier.isStatic(it.modifiers) }
    .associate { Pair(it.get(null), it.name.vkToPrintableName()) }

private fun Int.toPrintableKeyName(): String = keyNameByCode[this] ?: error("")

private fun Int.toPrintableModifiers(): List<String> =
    listOf(
        if (and(InputEvent.CTRL_MASK) != 0) "Ctrl" else "",
        if (and(InputEvent.ALT_MASK) != 0) "Alt" else "",
        if (and(InputEvent.META_MASK) != 0) "Meta" else "",
        if (and(InputEvent.SHIFT_MASK) != 0) "Shift" else ""
    ).filter(String::isNotEmpty)

private fun String.vkToPrintableName(): String {
    val s = removePrefix("VK_").toLowerCase()
    return when (s) {
        "comma"         -> ","
        "minus"         -> "-"
        "period"        -> "."
        "slash"         -> "/"
        "semicolon"     -> ";"
        "equals"        -> "="
        "open_bracket"  -> "["
        "back_slash"    -> "\\"
        "close_bracket" -> "]"
        else            -> s.replace("_", "").capitalize()
    }
}

private fun <T, R> List<T>.chunkedBy(f: (T) -> R): List<List<T>> = asSequence().chunkedBy(f).toList()

private fun <T, R> Sequence<T>.chunkedBy(f: (T) -> R): Sequence<List<T>> = buildSequence {
    var lastKey: R? = null
    var list = ArrayList<T>()
    forEach { item ->
        val key = f(item)
        if (key != lastKey) {
            lastKey = key
            if (list.isNotEmpty()) yield(list)
            list = ArrayList()
        }
        list.add(item)
    }
    if (list.isNotEmpty()) yield(list)
}
