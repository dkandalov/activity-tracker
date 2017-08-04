package activitytracker

import java.util.*

fun main(args: Array<String>) {
    val userHome = System.getProperty("user.home")
    val eventsFilePath = "$userHome/Library/Application Support/IntelliJIdea2016.2/activity-tracker/ide-events.csv"
    val eventSequence = TrackerLog(eventsFilePath).readEvents { line: String, e: Exception ->
        println("Failed to parse: $line")
    }

    eventsGroupedIntoSessions()

    // createHistogramOfDurationEvents(eventSequence)
}

private data class Session(val events: List<TrackerEvent>)

private fun eventsGroupedIntoSessions(): List<Session> {
    
    return emptyList()
}

private fun createHistogramOfDurationEvents(eventSequence: Sequence<TrackerEvent>) {
    val allDurations = mutableListOf<Int>()
    eventSequence
        .filter { it.type == "Duration" }
        .forEach { event ->
            allDurations.addAll(event.data.split(",").map(String::toInt))
        }
    val histogram = Histogram().addAll(allDurations)
    histogram.frequencyByValue.entries.forEach {
        println(it)
    }
}

private class Histogram {
    val frequencyByValue: TreeMap<Int, Int> = TreeMap()

    fun addAll(values: Collection<Int>): Histogram {
        values.forEach { value ->
            val frequency = frequencyByValue.getOrElse(value, { -> 0 })
            frequencyByValue.put(value, frequency + 1)
        }
        return this
    }
}

