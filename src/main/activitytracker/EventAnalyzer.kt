package activitytracker

import java.io.File

fun secondsInEditorByFile(events: List<TrackerEvent>): List<Pair<String, Int>> {
    return events
            .filter{ it.eventType == "IdeState" && it.focusedComponent == "Editor" && it.file != "" }
            .groupBy{ it.file }
            .map{ Pair(fileName(it.key), it.value.size)}
            .sortedBy{ it.second }
            .withTotal()
}

fun secondsByProject(events: List<TrackerEvent>): List<Pair<String, Int>> {
    return events
            .filter{ it.eventType == "IdeState" && it.eventData == "Active" }
            .groupBy{ it.projectName }
            .map{ Pair(it.key, it.value.size)}
            .sortedBy{ -it.second }
            .withTotal()
}

fun countByActionId(events: List<TrackerEvent>): List<Pair<String, Int>> {
    return events
        .filter{ it.eventType == "Action" }
        .groupBy{ it.eventData }
        .map{ Pair(it.key, it.value.size) }
        .sortedBy{ -it.second }
}

private fun fileName(filePath: String): String  {
    val i = filePath.lastIndexOf(File.separator)
    return if (i == -1) filePath
    else filePath.substring(i + 1)
}

private fun List<Pair<String, Int>>.withTotal(): List<Pair<String, Int>> {
    return this + Pair("Total", sumBy{ it.second })
}
