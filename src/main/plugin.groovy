import activitytracker.ActivityTracker2
import activitytracker.ActivityTrackerPlugin2
import activitytracker.PluginUI2
import activitytracker.TrackerLog
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager

import static liveplugin.PluginUtil.invokeOnEDT
import static liveplugin.PluginUtil.show
// add-to-classpath $PLUGIN_PATH/bin/production/activity-tracker/
// add-to-classpath $PLUGIN_PATH/lib/commons-csv-1.0.jar
// add-to-classpath $PLUGIN_PATH/lib/joda-time-2.9.2.jar

invokeOnEDT {
	def pathToTrackingLogFiles = "${PathManager.pluginsPath}/activity-tracker"
	def trackerLog = new TrackerLog(pathToTrackingLogFiles, pluginDisposable).init()
	def tracker = new ActivityTracker2(trackerLog, pluginDisposable)
	def propertiesComponent = PropertiesComponent.instance
	def plugin = new ActivityTrackerPlugin2(tracker, trackerLog, propertiesComponent, pluginDisposable).init()
	new PluginUI2(plugin, trackerLog, pluginDisposable).init()

	if (!isIdeStartup) show("Reloaded ActivityTracker")
}