import org.junit.Test

class PluginTest {
	@Test void "should convert log event to/from csv line"() {
		assert new LogEvent(new Date(0), "aProject", "aFile", "aMethod").toCsv() == "01:00:00 01/01/1970,aProject,aFile,aMethod"
		assert LogEvent.fromCsv("01:00:00 01/01/1970,aProject,aFile,aMethod") == new LogEvent(new Date(0), "aProject", "aFile", "aMethod")
	}
}
