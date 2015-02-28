import liveplugin.testrunner.IntegrationTestsRunner
import org.junit.Test

IntegrationTestsRunner.runIntegrationTests([TestClass], project, pluginPath)

class TestClass {
	@Test void "convert log event to/from csv line"() {
        def logEvent = new TrackingEvent(new Date(0), "aProject", "aFile", "aMethod", "SomeAction")
        assert logEvent.toCsv() == "01:00:00 01/01/1970,aProject,aFile,aMethod,SomeAction"
        assert TrackingEvent.fromCsv("01:00:00 01/01/1970,aProject,aFile,aMethod,SomeAction") == logEvent
	}
}
