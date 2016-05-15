package activitytracker

import activitytracker.liveplugin.newDisposable
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit.MILLISECONDS

class TrackerLog(path: String, val parentDisposable: Disposable) {
    private val log = Logger.getInstance(TrackerLog::class.java)
    private val writeFrequencyMs = 1000L
    private val utf8 = Charset.forName("UTF-8")

    private val statsFilePath: String
    private val eventQueue: Queue<TrackerEvent> = ConcurrentLinkedQueue()

    init {
        val pathFile = File(path)
        if (!pathFile.exists()) pathFile.mkdir()
        this.statsFilePath = path + "/ide-events.csv"
    }

    fun init(): TrackerLog {
        val runnable = {
            try {
                File(statsFilePath).printWriter(utf8).use { writer ->
                    val csvPrinter = CSVPrinter(writer, CSVFormat.RFC4180)
                    var event = eventQueue.poll()
                    while (event != null) {
                        event.toCsv(csvPrinter)
                        event = eventQueue.poll()
                    }
                    csvPrinter.close()
                }
            } catch (e: Exception) {
                log.error(e)
            }
        }

        val future = JobScheduler.getScheduler().scheduleWithFixedDelay(runnable, writeFrequencyMs, writeFrequencyMs, MILLISECONDS)
        newDisposable(parentDisposable) {
            future.cancel(true)
        }
        return this
    }

    fun append(event: TrackerEvent?) {
        if (event == null) return
        eventQueue.add(event)
    }

    fun clearLog(): Boolean {
        return FileUtil.delete(File(statsFilePath))
    }

    fun readEvents(onParseError: (String, Exception) -> Unit): List<TrackerEvent> {
        return File(statsFilePath).bufferedReader(utf8).use { reader ->
            val result = arrayListOf<TrackerEvent>()
            val csvParser = CSVParser(reader, CSVFormat.RFC4180)
            try {
                csvParser.forEach{
                    try {
                        result.add(TrackerEvent.fromCsv(it))
                    } catch (e: Exception) {
                        onParseError(it.toString(), e)
                    }
                }
            } finally {
                csvParser.close()
            }
            result
        }
    }

    fun rollLog(now: Date = Date()): File {
        val postfix = SimpleDateFormat("_yyyy-MM-dd").format(now)
        var rolledStatsFile = File(statsFilePath + postfix)
        var i = 1
        while (rolledStatsFile.exists()) {
            rolledStatsFile = File(statsFilePath + postfix + "_" + i)
            i++
        }

        FileUtil.rename(File(statsFilePath), rolledStatsFile)
        return rolledStatsFile
    }

    fun currentLogFile(): File {
        return File(statsFilePath)
    }
}