package activitytracker

import activitytracker.TrackerEvent.Type.*
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.Duration
import java.util.*

val userHome = System.getProperty("user.home")!!
val printError = { line: String, _: Exception -> println("Failed to parse: $line") }

fun main() {
//    val eventsFilePath = "$userHome/Library/Application Support/IntelliJIdea2017.2/activity-tracker/2017-09-01.csv"
    val eventsFilePath = "$userHome/Library/Application Support/IntelliJIdea2017.2/activity-tracker/ide-events.csv"
    val eventSequence = TrackerLog(eventsFilePath).readEvents { line: String, _: Exception ->
        println("Failed to parse: $line")
    }

//     amountOfKeyPresses(eventSequence)
//     createHistogramOfDurationEvents(eventSequence)

    val sessions = eventSequence.groupIntoSessions().filterAndMergeSessions().onEach { println(it) }

    Histogram(sessions.map{ it.duration.standardMinutes }.toList()).bucketed(20).printed()
}

private fun amountOfKeyPresses(eventSequence: Sequence<TrackerEvent>) {
    val histogram = Histogram<Char>()
    eventSequence
        .filter { it.type == KeyEvent }
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
        .forEachIndexed { _, c ->
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

private data class Session(val events: List<TrackerEvent>) {
    val duration: Duration get() = Duration(events.first().time, events.last().time)

    override fun toString() =
        "minutes: ${duration.standardMinutes}; " +
        "from: ${events.first().localTime}; " +
        "to: ${events.last().localTime};"

    private val TrackerEvent.localTime: DateTime get() = time.withZone(DateTimeZone.forOffsetHours(1))
}

fun TrackerEvent.ideIsActive() = type != IdeState || (type == IdeState && data != "Inactive")

private fun Sequence<TrackerEvent>.groupIntoSessions(): Sequence<Session> =
    sequence {
        var currentEvents = ArrayList<TrackerEvent>()
        forEach { event ->
            val isEndOfCurrentSession =
                currentEvents.isNotEmpty() && currentEvents.last().ideIsActive() != event.ideIsActive()

            if (isEndOfCurrentSession) {
                yield(Session(currentEvents))
                currentEvents = ArrayList()
            }
            currentEvents.add(event)
        }
    }

private fun Sequence<Session>.filterAndMergeSessions(): Sequence<Session> =
    sequence {
        var lastSession: Session? = null
        this@filterAndMergeSessions
            .filter { session ->
                session.events.first().ideIsActive()
                    && session.duration > Duration.standardMinutes(5)
                    && session.events.any { it.type == IdeState && it.focusedComponent == "Editor" }
            }
            .forEach { session ->
                lastSession = if (lastSession == null) session
                else {
                    val timeBetweenSessions = Duration(lastSession!!.events.last().time, session.events.first().time)
                    if (timeBetweenSessions < Duration.standardMinutes(5)) {
                        Session(lastSession!!.events + session.events)
                    } else {
                        yield(lastSession!!)
                        session
                    }
                }
            }
        yield(lastSession!!)
    }

/**
 * The intention is to check whether activity-tracker has significant impact on IDE performance.
 *
 * To do this `ActivityTracker.logTrackerCallDuration` was enabled, the plugin was used for some time
 * and then this function was used to analyse events.
 *
 * The conclusion was that there is no significant impact on performance
 * (no numbers are available at the time of writing though).
 */
private fun createHistogramOfDurationEvents(eventSequence: Sequence<TrackerEvent>) {
    val allDurations = mutableListOf<Int>()
    eventSequence
        .filter { it.type == Duration }
        .forEach { event ->
            allDurations.addAll(event.data.split(",").map(String::toInt))
        }
    val histogram = Histogram<Int>().addAll(allDurations)
    histogram.frequencyByValue.entries.forEach {
        println(it)
    }
}

private class Histogram<T>(val frequencyByValue: HashMap<T, Int> = HashMap()) {

    constructor(values: Collection<T>): this() {
        addAll(values)
    }

    fun add(value: T): Histogram<T> {
        val frequency = frequencyByValue.getOrElse(value, { 0 })
        frequencyByValue[value] = frequency + 1
        return this
    }

    fun addAll(values: Collection<T>): Histogram<T> {
        values.forEach { add(it) }
        return this
    }
}

private fun <T: Comparable<T>> Histogram<T>.printed() {
    frequencyByValue.entries
        .sortedBy { it.key }
        .forEach { entry ->
            println("${entry.key}\t${entry.value}")
        }
}

private fun Histogram<Long>.bucketed(bucketSize: Long): Histogram<Long> {
    val histogram = Histogram<Long>()
    frequencyByValue.entries.forEach {
        histogram.add((it.key / bucketSize) * bucketSize + bucketSize)
    }
    return histogram
}
