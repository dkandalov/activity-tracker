package activitytracker

import groovy.transform.Immutable
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormatterBuilder

import static org.joda.time.DateTimeFieldType.*

@Immutable(knownImmutableClasses = [DateTime])
final class TrackerEvent {
    private static final DateTimeFormatter dateTimeParseFormat = createDateTimeParseFormat()
    private static final DateTimeFormatter dateTimePrintFormat = createDateTimePrintFormat()

	DateTime time
	String userName
	String eventType
	String eventData
    String projectName
	String focusedComponent
	String file
    String psiPath
	int editorLine
	int editorColumn

    static TrackerEvent fromCsv(CSVRecord csvRecord) {
        def (time,
	         userName,
	         eventType,
		     eventData,
		     projectName,
		     focusedComponent,
		     file,
		     psiPath,
		     editorLine,
		     editorColumn
        ) = csvRecord.toList()

        new TrackerEvent(
		        parseDateTime(time),
		        userName,
		        eventType,
		        eventData,
		        projectName,
		        focusedComponent,
		        file,
		        psiPath,
		        Integer.parseInt(editorLine),
		        Integer.parseInt(editorColumn)
        )
    }

	void toCsv(CSVPrinter csvPrinter) {
	    csvPrinter.printRecord(
		    dateTimePrintFormat.print(time),
		    userName,
		    eventType,
		    eventData,
		    projectName,
		    focusedComponent,
		    file,
		    psiPath,
		    editorLine,
		    editorColumn
	    )
    }

	static TrackerEvent ideNotInFocus(DateTime time, String userName, String eventType, String eventData) {
		new TrackerEvent(time, userName, eventType, eventData, "", "", "", "", -1, -1)
	}

	private static DateTime parseDateTime(String time) {
		DateTime.parse(time, dateTimeParseFormat)
	}

	/**
	 * This has to be separate from {@link #createDateTimePrintFormat()}
	 * because builder with optional elements doesn't support printing.
	 */
	private static def createDateTimeParseFormat() {
		// support for plugin version "0.1.3 beta" format where amount of milliseconds could vary (java8 DateTimeFormatter.ISO_OFFSET_DATE_TIME)
		def msParser = new DateTimeFormatterBuilder()
				.appendLiteral('.').appendDecimal(millisOfSecond(), 1, 3)
				.toParser()
		// support for plugin version "0.1.2 beta" format which didn't have timezone
		def timeZoneParser = new DateTimeFormatterBuilder()
				.appendTimeZoneOffset("Z", true, 2, 4)
				.toParser()

		new DateTimeFormatterBuilder()
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
	private static def createDateTimePrintFormat() {
		new DateTimeFormatterBuilder()
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
