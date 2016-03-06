package activitytracker

import groovy.transform.Immutable
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVRecord
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

@Immutable(knownImmutableClasses = [DateTime])
final class TrackerEvent {
	private static final DateTimeFormatter oldDateTimeFormat = DateTimeFormat.forPattern("yyyy/MM/dd kk:mm:ss.SSS")
    private static final DateTimeFormatter dateTimeFormat = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

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
		    dateTimeFormat.print(time),
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
		try {
			DateTime.parse(time, oldDateTimeFormat)
		} catch (Exception ignored) {
			DateTime.parse(time, dateTimeFormat)
		}
	}
}
