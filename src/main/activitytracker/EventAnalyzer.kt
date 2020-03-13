package activitytracker

import activitytracker.EventAnalyzer.Result.*
import activitytracker.TrackerEvent.Type.IdeState
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class EventAnalyzer(private val trackerLog: TrackerLog) {
    var runner: (() -> Unit) -> Unit = {}
    private val isRunning = AtomicBoolean()

    fun analyze(whenDone: (Result) -> Unit) {
        runner.invoke {
            when {
                trackerLog.isTooLargeToProcess() -> whenDone(DataIsTooLarge)
                isRunning.get()                  -> whenDone(AlreadyRunning)
                else                             -> {
                    isRunning.set(true)
                    try {
                        val errors = ArrayList<Pair<String, Exception>>()
                        val events = trackerLog.readEvents(onParseError = { line: String, e: Exception ->
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
    }

    sealed class Result {
        class Ok(val stats: Stats, val errors: List<Pair<String, Exception>>) : Result()
        object AlreadyRunning : Result()
        object DataIsTooLarge : Result()
    }
}

data class Stats(
    val secondsInEditorByFile: List<Pair<String, Int>>,
    val secondsByProject: List<Pair<String, Int>>,
    val secondsByTask: List<Pair<String, Int>>,
    val countByActionId: List<Pair<String, Int>>,
    val dataFile: String = ""
)

fun analyze(events: Sequence<TrackerEvent>): Stats {
    val map1 = HashMap<String, Int>()
    val map2 = HashMap<String, Int>()
    val map3 = HashMap<String, Int>()
    val map4 = HashMap<String, Int>()
    events.forEach {
        secondsInEditorByFile(it, map1)
        secondsByProject(it, map2)
        secondsByTask(it, map3)
        countByActionId(it, map4)
    }
    return Stats(
        secondsInEditorByFile = map1.entries.map{ Pair(it.key, it.value) }.sortedBy{ -it.second }.withTotal(),
        secondsByProject = map2.entries.map{ Pair(it.key, it.value) }.sortedBy{ -it.second }.withTotal(),
        secondsByTask = map3.entries.map{ Pair(it.key, it.value) }.sortedBy{ -it.second }.withTotal(),
        countByActionId = map4.entries.map{ Pair(it.key, it.value) }.sortedBy{ -it.second }
    )
}

private fun secondsInEditorByFile(event: TrackerEvent, map: MutableMap<String, Int>) {
    if (event.type == IdeState && event.focusedComponent == "Editor" && event.file != "") {
        val key = fileName(event.file)
        map[key] = (map[key] ?: 0) + 1
    }
}

private fun secondsByProject(event: TrackerEvent, map: MutableMap<String, Int>) {
    if (event.type == IdeState && event.data == "Active") {
        val key = event.projectName
        map[key] = (map[key] ?: 0) + 1
    }
}

private fun secondsByTask(event: TrackerEvent, map: MutableMap<String, Int>) {
    if (event.type == IdeState && event.data == "Active") {
        val key = event.task
        map[key] = (map[key] ?: 0) + 1
    }
}

private fun countByActionId(event: TrackerEvent, map: MutableMap<String, Int>) {
    if (event.type == TrackerEvent.Type.Action) {
        val key = event.data
        map[key] = (map[key] ?: 0) + 1
    }
}

private fun fileName(filePath: String): String  {
    val i = filePath.lastIndexOf(File.separator)
    return if (i == -1) filePath else filePath.substring(i + 1)
}

private fun List<Pair<String, Int>>.withTotal() = this + Pair("Total", sumBy{ it.second })
