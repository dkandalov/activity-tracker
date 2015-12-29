package actiontracker2

class EventsAnalyzer {
    static Map<String, Integer> aggregateByFile(List<TrackerEvent> events) {
        events.groupBy{it.openFilePath}.collectEntries{[it.key, it.value.size()]}.sort{-it.value} as Map<String, Integer>
    }

    static Map<String, Integer> aggregateByElement(List<TrackerEvent> events) {
        events.groupBy{it.psiPath}.collectEntries{[it.key, it.value.size()]}.sort{-it.value} as Map<String, Integer>
    }

    static asString(Map<String, Integer> map) {
        def durationAsString = { Integer seconds ->
            seconds.intdiv(60) + ":" + String.format("%02d", seconds % 60)
        }
        def keyAsString = { it == null ? "[not in editor]" : it }
        map.collectEntries{ [keyAsString(it.key), durationAsString(it.value)] }.entrySet().join("\n")
    }
}
