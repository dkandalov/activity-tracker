package activitytracker

import activitytracker.tracking.TrackerEvent.Companion.printEvent
import activitytracker.tracking.TrackerEvent.Companion.toTrackerEvent
import activitytracker.liveplugin.whenDisposed
import activitytracker.tracking.TrackerEvent
import com.intellij.concurrency.JobScheduler
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import java.io.Closeable
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

    fun initWriter(parentDisposable: Disposable, writeFrequencyMs: Long): TrackerLog {
        val runnable = {
            try {
                val file = File(eventsFilePath)
                FileUtil.createIfDoesntExist(file)
                FileOutputStream(file, true).buffered().writer(UTF_8).use { writer ->
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

// Copied from Kotlin stdlib because it's only available in Kotlin 1.2 but the plugin supports 1.1.
private inline fun <T : Closeable?, R> T.use(block: (T) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        closeFinally(exception)
    }
}

private fun Closeable?.closeFinally(cause: Throwable?) = when {
    this == null -> {}
    cause == null -> close()
    else ->
        try {
            close()
        } catch (closeException: Throwable) {
            cause.addSuppressed(closeException)
        }
}