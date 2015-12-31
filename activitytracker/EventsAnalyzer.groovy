package activitytracker

class EventsAnalyzer {
    static Map<String, Integer> timeInEditorByFile(List<TrackerEvent> events) {
	    (Map<String, Integer>) events
			  .findAll{ it.eventType == "IdeState" && it.focusedComponent == "Editor" && it.file != "" }
		      .groupBy{ it.file }
	          .collectEntries{ [it.key, it.value.size()] }
			  .collectEntries{ [fileName(it.key), it.value] }
		      .sort{ -it.value }
    }

	private static String fileName(String filePath) {
		def i = filePath.lastIndexOf(File.separator)
		if (i == -1) return filePath
		else filePath.substring(i + 1)
	}
}
