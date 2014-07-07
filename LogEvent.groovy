import groovy.transform.Immutable

import java.text.SimpleDateFormat

@Immutable
final class LogEvent {
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("kk:mm:ss dd/MM/yyyy")
    Date time
    String projectName
    String file
    String element

    static LogEvent fromCsv(String csvLine) {
        def (date, projectName, file, element) = csvLine.split(",").toList()
        new LogEvent(TIME_FORMAT.parse(date), projectName, file, element)
    }

    String toCsv() {
        "${TIME_FORMAT.format(time)},$projectName,$file,$element"
    }
}
