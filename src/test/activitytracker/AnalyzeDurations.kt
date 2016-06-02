package activitytracker

import java.util.*

fun main(args: Array<String>) {
    val userHome = System.getProperty("user.home")
    val eventsFilePath = "$userHome/Library/Application Support/IntelliJIdea2016.2/activity-tracker/ide-events.csv"
    val trackerLog = TrackerLog(eventsFilePath)

    val allDurations = mutableListOf<Int>()
    val onParseError = { line: String, e: Exception ->
        println("Failed to parse: $line")
    }
    trackerLog.forEachEvent(onParseError) { event ->
        if (event.eventType == "Duration") {
            allDurations.addAll(event.eventData.split(",").map{ it.toInt() })
        }
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

