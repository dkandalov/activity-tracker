package activitytracker

class EventsAnalyzer {
    static Map<String, Integer> timeInEditorByFile(List<TrackerEvent> events) {
	    def result = (Map<String, Integer>) events
			  .findAll{ it.eventType == "IdeState" && it.focusedComponent == "Editor" && it.file != "" }
		      .groupBy{ it.file }
	          .collectEntries{ [it.key, it.value.size()] }
			  .collectEntries{ [fileName(it.key), it.value] }
		      .sort{ -it.value }
	    withTotal(result)
    }

    static Map<String, Integer> timeByProject(List<TrackerEvent> events) {
	    def result = (Map<String, Integer>) events
			  .findAll{ it.eventType == "IdeState" && it.eventData == "Active" }
		      .groupBy{ it.projectName }
	          .collectEntries{ [it.key, it.value.size()] }
			  .collectEntries{ [fileName(it.key), it.value] }
		      .sort{ -it.value }
	    withTotal(result)
    }

	private static withTotal(Map<String, Integer> data) {
		data.put("Total", data.entrySet().sum(0){ it.value } as int)
		data
	}

	private static String fileName(String filePath) {
		def i = filePath.lastIndexOf(File.separator)
		if (i == -1) return filePath
		else filePath.substring(i + 1)
	}
}
