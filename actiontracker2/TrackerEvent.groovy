package actiontracker2
import groovy.transform.Immutable

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Immutable(knownImmutableClasses = [LocalDateTime])
final class TrackerEvent {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd kk:mm:ss.SSS")

	LocalDateTime time
	String userName
	String eventType
	String eventData
    String projectName
	String focusedComponent
	String openFilePath
    String psiPath
	int editorLine
	int editorColumn

    static TrackerEvent fromCsv(String csvLine) {
        def (date, userName, eventType, eventData, projectName, focusedComponent, file, element, editorLine, editorColumn) = csvLine.split(",").toList()
        new TrackerEvent(LocalDateTime.parse(date, TIME_FORMAT), userName, eventType, eventData, projectName, focusedComponent, file, element, editorLine, editorColumn)
    }

    String toCsv() {
	    [TIME_FORMAT.format(time), userName, eventType, eventData, projectName, focusedComponent, openFilePath, psiPath, editorLine, editorColumn].join(",")
    }

	static TrackerEvent ideNotInFocus(LocalDateTime time, String userName, String eventType, String eventData) {
		new TrackerEvent(time, userName, eventType, eventData, "", "", "", "", -1, -1)
	}
}
