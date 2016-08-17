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
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit.MILLISECONDS

class TrackerLog(val eventsFilePath: String) {
    private val log = Logger.getInstance(TrackerLog::class.java)
    private val utf8 = Charset.forName("UTF-8")

    private val eventQueue: Queue<TrackerEvent> = ConcurrentLinkedQueue()

    init {
        val pathFile = File(eventsFilePath)
        if (!pathFile.exists()) pathFile.mkdir()
    }

    fun initWriter(writeFrequencyMs: Long, parentDisposable: Disposable): TrackerLog {
        val runnable = {
            try {
                FileOutputStream(File(eventsFilePath), true).buffered().writer(utf8).use { writer ->
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
        return FileUtil.delete(File(eventsFilePath))
    }

    fun forEachEvent(onParseError: (String, Exception) -> Unit, consumer: (TrackerEvent) -> Unit) {
        File(eventsFilePath).bufferedReader(utf8).use { reader ->
            val csvParser = CSVParser(reader, CSVFormat.RFC4180)
            try {
                csvParser.forEach{
                    try {
                        consumer.invoke(TrackerEvent.fromCsv(it))
                    } catch (e: Exception) {
                        onParseError(it.toString(), e)
                    }
                }
            } finally {
                csvParser.close()
            }
        }
    }

    fun readAllEvents(): Pair<List<TrackerEvent>, List<ReadError>> {
        val errors = arrayListOf<ReadError>()
        val onParseError = { line: String, e: Exception ->
            errors.add(ReadError(line, e))
            Unit
        }
        val result = arrayListOf<TrackerEvent>()
        forEachEvent(onParseError) {
            result.add(it)
        }
        return Pair(result, errors)
    }

    fun rollLog(now: Date = Date()): File {
        val postfix = SimpleDateFormat("_yyyy-MM-dd").format(now)
        var rolledStatsFile = File(eventsFilePath + postfix)
        var i = 1
        while (rolledStatsFile.exists()) {
            rolledStatsFile = File(eventsFilePath + postfix + "_" + i)
            i++
        }

        FileUtil.rename(File(eventsFilePath), rolledStatsFile)
        return rolledStatsFile
    }

    fun currentLogFile(): File {
        return File(eventsFilePath)
    }

    fun isTooLargeToProcess(): Boolean {
        return File(eventsFilePath).length() > 100000000L
    }

    data class ReadError(val line: String, val e: Exception)

}