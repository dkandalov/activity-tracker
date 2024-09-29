package activitytracker

import activitytracker.TrackerEvent.Companion.printEvent
import activitytracker.TrackerEvent.Companion.toTrackerEvent
import activitytracker.liveplugin.whenDisposed
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

class TrackerLog(private val eventsFilePath: String) {
    private val log = Logger.getInstance(TrackerLog::class.java)
    private val eventQueue: Queue<TrackerEvent> = ConcurrentLinkedQueue()
    private val eventsFile = File(eventsFilePath)

    fun initWriter(parentDisposable: Disposable, writeFrequencyMs: Long): TrackerLog {
        val runnable = {
            try {
                FileUtil.createIfDoesntExist(eventsFile)
                FileOutputStream(eventsFile, true).buffered().writer(UTF_8).use { writer ->
                    val csvPrinter = CSVPrinter(writer, CSVFormat.RFC4180)
                    var event: TrackerEvent? = eventQueue.poll()
                    while (event != null) {
                        csvPrinter.printEvent(event)
                        event = eventQueue.poll()
                    }
                    csvPrinter.flush()
                    csvPrinter.close()
                }
            } catch (e: Exception) {
                log.error(e)
            }
        }

        val future = JobScheduler.getScheduler().scheduleWithFixedDelay(runnable, writeFrequencyMs, writeFrequencyMs, MILLISECONDS)
        parentDisposable.whenDisposed {
            future.cancel(true)
        }
        return this
    }

    fun append(event: TrackerEvent?) {
        if (event == null) return
        eventQueue.add(event)
    }

    fun clearLog(): Boolean = FileUtil.delete(eventsFile)

    fun readEvents(onParseError: (String, Exception) -> Any): Sequence<TrackerEvent> {
        if (!eventsFile.exists()) return emptySequence()

        val reader = eventsFile.bufferedReader(UTF_8)
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
        var rolledStatsFile = File(eventsFile.path + postfix)
        var i = 1
        while (rolledStatsFile.exists()) {
            rolledStatsFile = File(eventsFile.path + postfix + "_" + i)
            i++
        }

        FileUtil.rename(eventsFile, rolledStatsFile)
        return rolledStatsFile
    }

    fun currentLogFile(): File = File(eventsFilePath)

    fun isTooLargeToProcess(): Boolean {
        val `2gb` = 2_000_000_000L
        return eventsFile.length() > `2gb`
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
