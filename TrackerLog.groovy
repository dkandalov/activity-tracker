class TrackerLog {
	private final String statsFilePath

	TrackerLog(String path) {
		def pathFile = new File(path)
		if (!pathFile.exists()) pathFile.mkdir()
		this.statsFilePath = path + "/stats.csv"
	}

	def append(TrackerEvent event) {
		new File(statsFilePath).append(event.toCsv() + "\n")
	}

	def resetHistory() {
		new File(statsFilePath).delete()
	}

	List<TrackerEvent> readHistory(Date fromTime, Date toTime) {
		new File(statsFilePath).withReader { reader ->
			def result = []
			String line
			while ((line = reader.readLine()) != null) {
				def event = TrackerEvent.fromCsv(line)
				if (event.time.after(fromTime) && event.time.before(toTime))
					result << event
				if (event.time.after(toTime)) break
			}
			result
		}
	}
}
