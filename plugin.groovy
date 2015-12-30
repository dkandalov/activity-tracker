import activitytracker.ActivityTracker
import activitytracker.ActivityTrackerPlugin
import activitytracker.PluginUI
import activitytracker.TrackerLog
import com.intellij.ide.util.PropertiesComponent

import static liveplugin.PluginUtil.invokeOnEDT
import static liveplugin.PluginUtil.show

invokeOnEDT {
	def trackerLog = new TrackerLog(pluginPath + "/stats", pluginDisposable).init()
	def tracker = new ActivityTracker(trackerLog, pluginDisposable)
	def propertiesComponent = PropertiesComponent.instance
	def plugin = new ActivityTrackerPlugin(tracker, trackerLog, propertiesComponent, pluginDisposable).init()
	new PluginUI(plugin, trackerLog).init(pluginDisposable)

	if (!isIdeStartup) show("Reloaded ActivityTracker")
}
