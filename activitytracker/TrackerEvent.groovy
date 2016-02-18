package activitytracker
import groovy.transform.Immutable
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Immutable(knownImmutableClasses = [ZonedDateTime])
final class TrackerEvent {
	private static final DateTimeFormatter oldDateTimeFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd kk:mm:ss.SSS")
    private static final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ISO_OFFSET_DATE_TIME

	ZonedDateTime time
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
		    dateTimeFormat.format(time),
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

	static TrackerEvent ideNotInFocus(ZonedDateTime time, String userName, String eventType, String eventData) {
		new TrackerEvent(time, userName, eventType, eventData, "", "", "", "", -1, -1)
	}

	private static ZonedDateTime parseDateTime(String time) {
		try {
			ZonedDateTime.parse(time, oldDateTimeFormat)
		} catch (Exception ignored) {
			ZonedDateTime.parse(time, dateTimeFormat)
		}
	}
}
