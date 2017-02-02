package activitytracker

import activitytracker.EventAnalyzer.Result.*
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class EventAnalyzer(private val trackerLog: TrackerLog) {
    var runner: (() -> Unit) -> Unit = {}
    private val isRunning = AtomicBoolean()

    fun analyze(whenDone: (Result) -> Unit) {
        runner.invoke {
            if (trackerLog.isTooLargeToProcess()) {
                whenDone(DataIsTooLarge())
            } else if (isRunning.get()) {
                whenDone(AlreadyRunning())
            } else {
                isRunning.set(true)

                try {
                    val errors = ArrayList<Pair<String, Exception>>()
                    val events = trackerLog.readEventSequence(onParseError = { line: String, e: Exception ->
                        errors.add(Pair(line, e))
                        if (errors.size > 20) errors.removeAt(0)
                    })

                    val stats = analyze(events).copy(dataFile = trackerLog.currentLogFile().absolutePath)

                    whenDone(Ok(stats, errors))
                } finally {
                    isRunning.set(false)
                }
            }
        }
    }

    sealed class Result {
        class Ok(val stats: Stats, val errors: List<Pair<String, Exception>>) : Result()
        class AlreadyRunning : Result()
        class DataIsTooLarge : Result()
    }
}

data class Stats(
    val secondsInEditorByFile: List<Pair<String, Int>>,
    val secondsByProject: List<Pair<String, Int>>,
    val countByActionId: List<Pair<String, Int>>,
    val dataFile: String = ""
)

fun analyze(events: Sequence<TrackerEvent>): Stats {
    val map1 = HashMap<String, Int>()
    val map2 = HashMap<String, Int>()
    val map3 = HashMap<String, Int>()
    events.forEach {
        secondsInEditorByFile(it, map1)
        secondsByProject(it, map2)
        countByActionId(it, map3)
    }
    return Stats(
        secondsInEditorByFile = map1.entries.map{ Pair(it.key, it.value) }.sortedBy{ -it.second }.withTotal(),
        secondsByProject = map2.entries.map{ Pair(it.key, it.value) }.sortedBy{ -it.second }.withTotal(),
        countByActionId = map3.entries.map{ Pair(it.key, it.value) }.sortedBy{ -it.second }
    )
}

private fun secondsInEditorByFile(event: TrackerEvent, map: MutableMap<String, Int>) {
    if (event.eventType == "IdeState" && event.focusedComponent == "Editor" && event.file != "") {
        val key = fileName(event.file)
        map[key] = (map[key] ?: 0) + 1
    }
}

private fun secondsByProject(event: TrackerEvent, map: MutableMap<String, Int>) {
    if (event.eventType == "IdeState" && event.eventData == "Active") {
        val key = event.projectName
        map[key] = (map[key] ?: 0) + 1
    }
}

private fun countByActionId(event: TrackerEvent, map: MutableMap<String, Int>) {
    if (event.eventType == "Action") {
        val key = event.eventData
        map[key] = (map[key] ?: 0) + 1
    }
}

private fun fileName(filePath: String): String  {
    val i = filePath.lastIndexOf(File.separator)
    return if (i == -1) filePath
    else filePath.substring(i + 1)
}

private fun List<Pair<String, Int>>.withTotal(): List<Pair<String, Int>> {
    return this + Pair("Total", sumBy{ it.second })
}
