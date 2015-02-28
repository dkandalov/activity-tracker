import groovy.transform.Immutable

import java.text.SimpleDateFormat

@Immutable
final class TrackingEvent {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("kk:mm:ss dd/MM/yyyy")
    Date time
    String projectName
    String file
    String methodOrClass
    String actionId

    static TrackingEvent fromCsv(String csvLine) {
        def (date, projectName, file, element, actionId) = csvLine.split(",").toList()
        new TrackingEvent(TIME_FORMAT.parse(date), projectName, file, element, actionId)
    }

    String toCsv() {
        "${TIME_FORMAT.format(time)},$projectName,$file,$methodOrClass,$actionId"
    }
}
