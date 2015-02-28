class EventsAnalyzer {
    static Map<String, Integer> aggregateByFile(List<TrackingEvent> events) {
        events.groupBy{it.file}.collectEntries{[it.key, it.value.size()]}.sort{-it.value} as Map<String, Integer>
    }

    static Map<String, Integer> aggregateByElement(List<TrackingEvent> events) {
        events.groupBy{it.methodOrClass}.collectEntries{[it.key, it.value.size()]}.sort{-it.value} as Map<String, Integer>
    }

    static asString(Map<String, Integer> map) {
        def durationAsString = { Integer seconds ->
            seconds.intdiv(60) + ":" + String.format("%02d", seconds % 60)
        }
        def keyAsString = { it == null ? "[not in editor]" : it }
        map.collectEntries{ [keyAsString(it.key), durationAsString(it.value)] }.entrySet().join("\n")
    }
}
