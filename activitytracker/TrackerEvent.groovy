package activitytracker
import groovy.transform.Immutable

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Immutable(knownImmutableClasses = [ZonedDateTime])
final class TrackerEvent {
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

    static TrackerEvent fromCsv(String csvLine) {
        def (time, userName, eventType, eventData, projectName, focusedComponent, file, psiPath, editorLine, editorColumn) = csvLine.split(",").toList()
        new TrackerEvent(
		        ZonedDateTime.parse(time, dateTimeFormat),
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

    String toCsv() {
	    [dateTimeFormat.format(time),
	     userName,
	     eventType,
	     eventData,
	     projectName,
	     focusedComponent,
	     file,
	     psiPath,
	     editorLine,
	     editorColumn
	    ].join(",")
    }

	static TrackerEvent ideNotInFocus(ZonedDateTime time, String userName, String eventType, String eventData) {
		new TrackerEvent(time, userName, eventType, eventData, "", "", "", "", -1, -1)
	}
}
