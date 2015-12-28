package actiontracker2
import groovy.transform.Immutable

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Immutable(knownImmutableClasses = [LocalDateTime])
final class TrackerEvent {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd kk:mm:ss.SSS")

	LocalDateTime time
	String eventType
    String projectName
    String openFilePath
    String locationInFile
	int editorLine
	int editorColumn
    String data

    static TrackerEvent fromCsv(String csvLine) {
        def (date, eventType, projectName, file, element, editorLine, editorColumn, data) = csvLine.split(",").toList()
        new TrackerEvent(LocalDateTime.parse(date, TIME_FORMAT), eventType, projectName, file, element, editorLine, editorColumn, data)
    }

    String toCsv() {
	    [TIME_FORMAT.format(time), eventType, projectName, openFilePath, locationInFile, editorLine, editorColumn, data].join(",")
    }
}
