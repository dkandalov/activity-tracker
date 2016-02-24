package activitytracker
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.jetbrains.annotations.Nullable

import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentLinkedQueue

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static liveplugin.implementation.Misc.newDisposable

class TrackerLog {
	private final log = Logger.getInstance(TrackerLog)

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
			try {
				new File(statsFilePath).withWriterAppend("UTF-8") { writer ->
					def csvPrinter = new CSVPrinter(writer, CSVFormat.RFC4180)
					def event = eventQueue.poll()
					while (event != null) {
						event.toCsv(csvPrinter)
						event = eventQueue.poll()
					}
					csvPrinter.close()
				}
			} catch (Exception e) {
				log.error(e)
			}
		} as Runnable

		def future = JobScheduler.scheduler.scheduleWithFixedDelay(runnable, writeFrequencyMs, writeFrequencyMs, MILLISECONDS)
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

	List<TrackerEvent> readEvents(Closure onParseError) {
		new File(statsFilePath).withReader("UTF-8") { reader ->
			def result = []
			def csvParser = new CSVParser(reader, CSVFormat.RFC4180)
			try {
				csvParser.each{
					try {
						result << TrackerEvent.fromCsv(it)
					} catch (Exception e) {
						onParseError.call(it.toString(), e)
					}
				}
			} finally {
				csvParser.close()
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
