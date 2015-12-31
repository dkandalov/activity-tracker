import activitytracker.ActivityTracker
import activitytracker.ActivityTrackerPlugin
import activitytracker.PluginUI
import activitytracker.TrackerLog
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.PathManager

import static liveplugin.PluginUtil.invokeOnEDT
import static liveplugin.PluginUtil.show

invokeOnEDT {
	def pathToTrackingLogFiles = "${PathManager.pluginsPath}/activity-tracker"
	def trackerLog = new TrackerLog(pathToTrackingLogFiles, pluginDisposable).init()
	def tracker = new ActivityTracker(trackerLog, pluginDisposable)
	def propertiesComponent = PropertiesComponent.instance
	def plugin = new ActivityTrackerPlugin(tracker, trackerLog, propertiesComponent, pluginDisposable).init()
	new PluginUI(plugin, trackerLog, pluginDisposable).init()

	if (!isIdeStartup) show("Reloaded ActivityTracker")
}
