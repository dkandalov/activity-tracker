package activitytracker

import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord
import org.joda.time.DateTime
import org.joda.time.DateTimeFieldType.*
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormatterBuilder

data class TrackerEvent(
    val time: DateTime,
    val userName: String,
    val type: String,
    val data: String,
    val projectName: String,
    val focusedComponent: String,
    val file: String,
    val psiPath: String,
    val editorLine: Int,
    val editorColumn: Int,
    val task: String
) {
    fun toCsv(csvPrinter: CSVPrinter) {
        csvPrinter.printRecord(
            dateTimePrintFormat.print(time),
            userName,
            type,
            data,
            projectName,
            focusedComponent,
            file,
            psiPath,
            editorLine,
            editorColumn,
            task
        )
    }


    companion object {
        private val dateTimeParseFormat: DateTimeFormatter = createDateTimeParseFormat()
        private val dateTimePrintFormat: DateTimeFormatter = createDateTimePrintFormat()

        fun ideNotInFocus(time: DateTime, userName: String, eventType: String, eventData: String): TrackerEvent {
            return TrackerEvent(time, userName, eventType, eventData, "", "", "", "", -1, -1, "")
        }

        fun fromCsv(csvRecord: CSVRecord): TrackerEvent {
            return TrackerEvent(
                time = parseDateTime(csvRecord[0]),
                userName = csvRecord[1],
                type = csvRecord[2],
                data = csvRecord[3],
                projectName = csvRecord[4],
                focusedComponent = csvRecord[5],
                file = csvRecord[6],
                psiPath = csvRecord[7],
                editorLine = csvRecord[8].toInt(),
                editorColumn = csvRecord[9].toInt(),
                task = if (csvRecord.size() < 11) "" else csvRecord[10] // backward compatibility with plugin data before 1.0.6 beta
            )
        }

        fun parseDateTime(time: String): DateTime = DateTime.parse(time, dateTimeParseFormat.withZoneUTC())

        /**
         * Parser has to be separate from {@link #createDateTimePrintFormat()}
         * because builder with optional elements doesn't support printing.
         */
        private fun createDateTimeParseFormat(): DateTimeFormatter {
            // support for plugin version "0.1.3 beta" format where amount of milliseconds could vary (java8 DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            val msParser = DateTimeFormatterBuilder()
                    .appendLiteral('.').appendDecimal(millisOfSecond(), 1, 3)
                    .toParser()
            // support for plugin version "0.1.2 beta" format which didn't have timezone
            val timeZoneParser = DateTimeFormatterBuilder()
                    .appendTimeZoneOffset("Z", true, 2, 4)
                    .toParser()

            return DateTimeFormatterBuilder()
                .appendFixedDecimal(year(), 4)
                .appendLiteral('-').appendFixedDecimal(monthOfYear(), 2)
                .appendLiteral('-').appendFixedDecimal(dayOfMonth(), 2)
                .appendLiteral('T').appendFixedDecimal(hourOfDay(), 2)
                .appendLiteral(':').appendFixedDecimal(minuteOfHour(), 2)
                .appendLiteral(':').appendFixedDecimal(secondOfMinute(), 2)
                .appendOptional(msParser)
                .appendOptional(timeZoneParser)
                .toFormatter()
        }

        /**
         * Similar to {@link org.joda.time.format.ISODateTimeFormat#basicDateTime()} except for "yyyy-MM-dd" part.
         * It's also similar java8 DateTimeFormatter.ISO_OFFSET_DATE_TIME except that this printer always prints all millisecond digits
         * which should be easier to read (or manually parse).
         */
        private fun createDateTimePrintFormat(): DateTimeFormatter {
            return DateTimeFormatterBuilder()
                .appendFixedDecimal(year(), 4)
                .appendLiteral('-').appendFixedDecimal(monthOfYear(), 2)
                .appendLiteral('-').appendFixedDecimal(dayOfMonth(), 2)
                .appendLiteral('T').appendFixedDecimal(hourOfDay(), 2)
                .appendLiteral(':').appendFixedDecimal(minuteOfHour(), 2)
                .appendLiteral(':').appendFixedDecimal(secondOfMinute(), 2)
                .appendLiteral('.').appendDecimal(millisOfSecond(), 3, 3)
                .appendTimeZoneOffset("Z", true, 2, 4)
                .toFormatter()
        }
    }
}