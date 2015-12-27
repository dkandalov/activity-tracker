package actiontracker2

import groovy.transform.Immutable

import java.text.SimpleDateFormat

@Immutable
final class TrackerEvent {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("kk:mm:ss dd/MM/yyyy")
    Date time
    String projectName
    String file
    String methodOrClass
    String actionId

    static TrackerEvent fromCsv(String csvLine) {
        def (date, projectName, file, element, actionId) = csvLine.split(",").toList()
        new TrackerEvent(TIME_FORMAT.parse(date), projectName, file, element, actionId)
    }

    String toCsv() {
        "${TIME_FORMAT.format(time)},$projectName,$file,$methodOrClass,$actionId"
    }
}
