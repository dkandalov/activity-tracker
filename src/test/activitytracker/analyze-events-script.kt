package activitytracker

import java.util.*

fun main(args: Array<String>) {
    val userHome = System.getProperty("user.home")
    val eventsFilePath = "$userHome/Library/Application Support/IntelliJIdea2017.2/activity-tracker/ide-events.csv"
    val eventSequence = TrackerLog(eventsFilePath).readEvents { line: String, e: Exception ->
        println("Failed to parse: $line")
    }

//    amountOfKeyPresses(eventSequence)
     groupEventsIntoSessions(eventSequence.take(10000))
    // createHistogramOfDurationEvents(eventSequence)
}

private fun amountOfKeyPresses(eventSequence: Sequence<TrackerEvent>) {
    val histogram = Histogram<Char>()
    eventSequence
        .filter { it.type == TrackerEvent.Type.KeyEvent }
        .map { it.data.split(":") }
        .filter { it[0] != "65535" }
        .map { it[0].toInt().toChar().toLowerCase() }
        .map {
            when (it) {
                '~' -> '`'
                '!' -> '1'
                '@' -> '2'
                '#' -> '3'
                '$' -> '4'
                '%' -> '5'
                '^' -> '6'
                '&' -> '7'
                '*' -> '8'
                '(' -> '9'
                ')' -> '0'
                '_' -> '-'
                '+' -> '='
                '<' -> ','
                '>' -> '.'
                '{' -> '['
                '}' -> ']'
                '|' -> '\\'
                '\"' -> '\''
                ':' -> ';'
                '?' -> '/'
                else -> it
            }
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
}

private data class Session(val events: List<TrackerEvent>)

private fun groupEventsIntoSessions(eventSequence: Sequence<TrackerEvent>): List<Session> {
    val result = ArrayList<Session>()
    val lastEvent = eventSequence.firstOrNull() ?: return emptyList()
    eventSequence.forEach {
        
    }
    return result
}

private fun createHistogramOfDurationEvents(eventSequence: Sequence<TrackerEvent>) {
    val allDurations = mutableListOf<Int>()
    eventSequence
        .filter { it.type == TrackerEvent.Type.Duration }
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
        return Histogram(map)
    }
}

