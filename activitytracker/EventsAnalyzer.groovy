package activitytracker

class EventsAnalyzer {
    static Map<String, String> timeInEditorByFile(List<TrackerEvent> events) {
	    (Map<String, String>) events
			  .findAll{ it.eventType == "IdeState" && it.focusedComponent == "Editor" && it.file != "" }
		      .groupBy{ it.file }
	          .collectEntries{ [it.key, it.value.size()] }
		      .sort{ -it.value }
	          .collectEntries{ [fileName(it.key), secondsToString(it.value)] }
    }

	private static String secondsToString(Integer seconds) {
		seconds.intdiv(60) + ":" + String.format("%02d", seconds % 60)
	}

	private static String fileName(String filePath) {
		def i = filePath.lastIndexOf(File.separator)
		if (i == -1) return filePath
		else filePath.substring(i + 1)
	}
}
