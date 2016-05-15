package activitytracker

import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormatter
import org.joda.time.format.DateTimeFormatterBuilder

import static org.joda.time.DateTimeFieldType.*

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

	TrackerEvent(DateTime time, String userName, String eventType, String eventData, String projectName,
	             String focusedComponent, String file, String psiPath, int editorLine, int editorColumn) {
		this.time = time
		this.userName = userName
		this.eventType = eventType
		this.eventData = eventData
		this.projectName = projectName
		this.focusedComponent = focusedComponent
		this.file = file
		this.psiPath = psiPath
		this.editorLine = editorLine
		this.editorColumn = editorColumn
	}

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

	boolean equals(o) {
		if (this.is(o)) return true
		if (getClass() != o.class) return false

		TrackerEvent that = (TrackerEvent) o

		if (editorColumn != that.editorColumn) return false
		if (editorLine != that.editorLine) return false
		if (eventData != that.eventData) return false
		if (eventType != that.eventType) return false
		if (file != that.file) return false
		if (focusedComponent != that.focusedComponent) return false
		if (projectName != that.projectName) return false
		if (psiPath != that.psiPath) return false
		if (time != that.time) return false
		if (userName != that.userName) return false

		return true
	}

	int hashCode() {
		int result
		result = (time != null ? time.hashCode() : 0)
		result = 31 * result + (userName != null ? userName.hashCode() : 0)
		result = 31 * result + (eventType != null ? eventType.hashCode() : 0)
		result = 31 * result + (eventData != null ? eventData.hashCode() : 0)
		result = 31 * result + (projectName != null ? projectName.hashCode() : 0)
		result = 31 * result + (focusedComponent != null ? focusedComponent.hashCode() : 0)
		result = 31 * result + (file != null ? file.hashCode() : 0)
		result = 31 * result + (psiPath != null ? psiPath.hashCode() : 0)
		result = 31 * result + editorLine
		result = 31 * result + editorColumn
		return result
	}
}
