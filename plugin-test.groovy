import liveplugin.testrunner.IntegrationTestsRunner
import org.junit.Test

IntegrationTestsRunner.runIntegrationTests([TestClass], project, pluginPath)

class TestClass {
	@Test void "convert log event to/from csv line"() {
        def logEvent = new LogEvent(new Date(0), "aProject", "aFile", "aMethod")
        assert logEvent.toCsv() == "01:00:00 01/01/1970,aProject,aFile,aMethod"
        assert LogEvent.fromCsv("01:00:00 01/01/1970,aProject,aFile,aMethod") == logEvent
	}
}
