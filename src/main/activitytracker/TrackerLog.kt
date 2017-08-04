package activitytracker

import activitytracker.liveplugin.newDisposable
import activitytracker.TrackerEvent.Companion.toTrackerEvent
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.text.Charsets.UTF_8

class TrackerLog(val eventsFilePath: String) {
    private val log = Logger.getInstance(TrackerLog::class.java)
    private val eventQueue: Queue<TrackerEvent> = ConcurrentLinkedQueue()

    init {
        val pathFile = File(eventsFilePath)
        if (!pathFile.exists()) pathFile.mkdir()
    }

    fun initWriter(writeFrequencyMs: Long, parentDisposable: Disposable): TrackerLog {
        val runnable = {
            try {
                val file = File(eventsFilePath)
                FileUtil.createIfDoesntExist(file)
                FileOutputStream(file, true).buffered().writer(UTF_8).use { writer ->
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

    fun clearLog(): Boolean = FileUtil.delete(File(eventsFilePath))

    fun readEvents(onParseError: (String, Exception) -> Any): Sequence<TrackerEvent> {
        val reader = File(eventsFilePath).bufferedReader(UTF_8)
        val parser = CSVParser(reader, CSVFormat.RFC4180)
        val sequence = parser.asSequence().map { csvRecord ->
            try {
                csvRecord.toTrackerEvent()
            } catch (e: Exception) {
                onParseError(csvRecord.toString(), e)
                null
            }
        }

        return sequence.filterNotNull().onClose {
            parser.close()
            reader.close()
        }
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

    fun currentLogFile(): File = File(eventsFilePath)

    fun isTooLargeToProcess(): Boolean {
        val `2gb` = 2000000000L
        return File(eventsFilePath).length() > `2gb`
    }
}


private fun <T> Sequence<T>.onClose(action: () -> Unit): Sequence<T> {
    val iterator = this.iterator()
    return object : Sequence<T> {
        override fun iterator() = object : Iterator<T> {
            override fun hasNext(): Boolean {
                val result = iterator.hasNext()
                if (!result) action()
                return result
            }
            override fun next() = iterator.next()
        }
    }
}
