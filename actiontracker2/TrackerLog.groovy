package actiontracker2

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Nullable

import java.text.SimpleDateFormat

class TrackerLog {
	private final String statsFilePath

	TrackerLog(String path) {
		def pathFile = new File(path)
		if (!pathFile.exists()) pathFile.mkdir()
		this.statsFilePath = path + "/stats.csv"
	}

	def append(@Nullable TrackerEvent event) {
		// TODO use queue
		if (event == null) return
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

	void rollFile() {
		def postfix = new SimpleDateFormat("_yyyyMMdd_HHmmss").format(new Date())
		def statsFile = new File(statsFilePath)
		FileUtil.rename(statsFile, new File(statsFilePath + postfix))
	}

	File currentLogFile() {
		new File(statsFilePath)
	}
}
