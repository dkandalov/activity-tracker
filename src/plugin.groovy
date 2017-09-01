import activitytracker.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager

import static liveplugin.PluginUtil.invokeOnEDT
import static liveplugin.PluginUtil.show
// add-to-classpath $PLUGIN_PATH/build/classes/main/
// add-to-classpath $PLUGIN_PATH/lib/commons-csv-1.3.jar
// add-to-classpath $PLUGIN_PATH/lib/joda-time-2.9.2.jar

invokeOnEDT {
//	def analyzer = new EventAnalyzer(new TrackerLog(""))
//	new StatsToolWindow.Companion().showIn(project, new Stats([], [], [], "some-file.csv"), analyzer, pluginDisposable)
//	return

	def pathToTrackingLogFile = "${PathManager.pluginsPath}/activity-tracker/ide-events.csv"
	def trackerLog = new TrackerLog(pathToTrackingLogFile).initWriter(1000L, pluginDisposable)
	def tracker = new ActivityTracker(trackerLog, pluginDisposable, false)
	def plugin = new ActivityTrackerPlugin(tracker, trackerLog, PropertiesComponent.instance).init()
	def eventAnalyzer = new EventAnalyzer(trackerLog)
	new PluginUI(plugin, trackerLog, eventAnalyzer, pluginDisposable).init()

	if (!isIdeStartup) show("Reloaded ActivityTracker")
}
