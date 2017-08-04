package activitytracker

import java.util.*

fun main(args: Array<String>) {
    val userHome = System.getProperty("user.home")
    val eventsFilePath = "$userHome/Library/Application Support/IntelliJIdea2017.2/activity-tracker/ide-events.csv"
    val eventSequence = TrackerLog(eventsFilePath).readEvents { line: String, e: Exception ->
        println("Failed to parse: $line")
    }

    val histogram = Histogram<Char>()
    eventSequence
        .filter { it.type == "KeyEvent" }
        .map { it.data.split(":") }
        .filter { it[0] != "65535" }
        .map { it[0].toInt().toChar().toLowerCase() }
        .map {
            if (it == '~') '`'
            else if (it == '!') '1'
            else if (it == '@') '2'
            else if (it == '#') '3'
            else if (it == '$') '4'
            else if (it == '%') '5'
            else if (it == '^') '6'
            else if (it == '&') '7'
            else if (it == '*') '8'
            else if (it == '(') '9'
            else if (it == ')') '0'
            else if (it == '_') '-'
            else if (it == '+') '='
            else if (it == '<') ','
            else if (it == '>') '.'
            else if (it == '{') '['
            else if (it == '}') ']'
            else if (it == '|') '\\'
            else if (it == '\"') '\''
            else if (it == ':') ';'
            else if (it == '?') '/'
            else it
        }
        .forEachIndexed { i, c ->
            histogram.add(c)
        }


    histogram.frequencyByValue.entries
        .sortedBy { -it.value }
        .forEach { println(it) }

//    val normalized = histogram.normalizeTo(max = 100)
//    normalized
//        .frequencyByValue.entries
//        .sortedBy { -it.value }
//        .forEach { entry ->
//            0.until(entry.value).forEach {
//                print(entry.key)
//            }
//        }

    // eventsGroupedIntoSessions(eventSequence)
    // createHistogramOfDurationEvents(eventSequence)
}

private data class Session(val events: List<TrackerEvent>)

private fun eventsGroupedIntoSessions(eventSequence: Sequence<TrackerEvent>): List<Session> {
    val lastEvent = eventSequence.firstOrNull() ?: return emptyList()

    return emptyList()
}

private fun createHistogramOfDurationEvents(eventSequence: Sequence<TrackerEvent>) {
    val allDurations = mutableListOf<Int>()
    eventSequence
        .filter { it.type == "Duration" }
        .forEach { event ->
            allDurations.addAll(event.data.split(",").map(String::toInt))
        }
    val histogram = Histogram<Int>().addAll(allDurations)
    histogram.frequencyByValue.entries.forEach {
        println(it)
    }
}

private class Histogram<T>(val frequencyByValue: HashMap<T, Int> = HashMap()) {

    fun add(value: T): Histogram<T> {
        val frequency = frequencyByValue.getOrElse(value, { -> 0 })
        frequencyByValue.put(value, frequency + 1)
        return this
    }

    fun addAll(values: Collection<T>): Histogram<T> {
        values.forEach { add(it) }
        return this
    }

    fun normalizeTo(max: Int): Histogram<T> {
        val maxValue = frequencyByValue.values.max()!!
        val ratio = max.toDouble() / maxValue

        val map = HashMap<T, Int>()
        frequencyByValue.entries.forEach {
            map[it.key] = (it.value * ratio).toInt()
        }
        return Histogram<T>(map)
    }
}

