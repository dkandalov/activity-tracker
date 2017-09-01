package activitytracker

import org.joda.time.DateTimeZone
import org.joda.time.Duration
import java.util.*

fun main(args: Array<String>) {
    val userHome = System.getProperty("user.home")
//    val eventsFilePath = "$userHome/Library/Application Support/IntelliJIdea2017.2/activity-tracker/2017-09-01.csv"
    val eventsFilePath = "$userHome/Library/Application Support/IntelliJIdea2017.2/activity-tracker/ide-events.csv"
    val eventSequence = TrackerLog(eventsFilePath).readEvents { line: String, e: Exception ->
        println("Failed to parse: $line")
    }

//    amountOfKeyPresses(eventSequence)
    groupEventsIntoSessions(eventSequence)
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
    val events = eventSequence.filter { it.type == TrackerEvent.Type.IdeState }

    val allSessions = ArrayList<Session>()
    var currentSession = ArrayList<TrackerEvent>()
    events.forEach { event ->
        if (currentSession.isNotEmpty() && currentSession.last().data != event.data) {
            allSessions.add(Session(currentSession))
            currentSession = ArrayList()
        }
        currentSession.add(event)
    }

    val result = ArrayList<Session>()
    var lastSession: Session? = null
    allSessions
        .filter { it.events.first().data == "Active" }
        .forEach { session ->
            if (lastSession == null) {
                lastSession = session
            } else {
                val duration = Duration(lastSession!!.events.last().time, session.events.first().time)
                if (duration < Duration.standardMinutes(5)) {
                    lastSession = Session(lastSession!!.events + session.events)
                } else {
                    result.add(lastSession!!)
                    lastSession = session
                }
            }
        }
    result.add(lastSession!!)

    val histogram = Histogram<Int>()
    result.forEach { session ->
        val duration = Duration(session.events.first().time, session.events.last().time)
        println(
            "minutes: ${duration.toStandardMinutes().minutes}; " +
            "from: ${session.events.first().time.withZone(DateTimeZone.forOffsetHours(1))}; " +
            "to: ${session.events.last().time.withZone(DateTimeZone.forOffsetHours(1))}; "
        )
        histogram.add(duration.toStandardMinutes().minutes)
    }
    histogram.printed()

    return result
}

private fun <T: Comparable<T>> Histogram<T>.printed() {
    frequencyByValue.entries
        .sortedBy { it.key }
        .forEach { entry ->
            println("${entry.value}: ${entry.key}")
        }
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

