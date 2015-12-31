package activitytracker
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.Nullable

import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentLinkedQueue

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static liveplugin.implementation.Misc.newDisposable

class TrackerLog {
	private final static long writeFrequencyMs = 1000
	private final String statsFilePath
	private final Disposable parentDisposable
	private final Queue<TrackerEvent> eventQueue = new ConcurrentLinkedQueue<>()

	TrackerLog(String path, Disposable parentDisposable) {
		def pathFile = new File(path)
		if (!pathFile.exists()) pathFile.mkdir()
		this.statsFilePath = path + "/ide-events.csv"
		this.parentDisposable = parentDisposable
	}

	def init() {
		def runnable = {
			def file = new File(statsFilePath)
			def event = eventQueue.poll()
			while (event != null) {
				file.append(event.toCsv() + "\n")
				event = eventQueue.poll()
			}
		} as Runnable
		def future = JobScheduler.scheduler.scheduleAtFixedRate(runnable, writeFrequencyMs, writeFrequencyMs, MILLISECONDS)
		newDisposable(parentDisposable) {
			future.cancel(true)
		}
		this
	}

	def append(@Nullable TrackerEvent event) {
		if (event == null) return
		eventQueue.add(event)
	}

	boolean clearLog() {
		FileUtil.delete(new File(statsFilePath))
	}

	List<TrackerEvent> readEvents() {
		new File(statsFilePath).withReader { reader ->
			def result = []
			String line
			while ((line = reader.readLine()) != null) {
				result << TrackerEvent.fromCsv(line)
			}
			result
		}
	}

	File rollLog(Date now = new Date()) {
		def postfix = new SimpleDateFormat("_yyyy-MM-dd").format(now)
		def rolledStatsFile = new File(statsFilePath + postfix)
		def i = 1
		while (rolledStatsFile.exists()) {
			rolledStatsFile = new File(statsFilePath + postfix + "_" + i)
			i++
		}

		FileUtil.rename(new File(statsFilePath), rolledStatsFile)
		rolledStatsFile
	}

	File currentLogFile() {
		new File(statsFilePath)
	}
}
