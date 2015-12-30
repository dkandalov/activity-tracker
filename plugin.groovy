import activitytracker.ActivityTracker
import activitytracker.ActivityTrackerPlugin
import activitytracker.PluginUI
import activitytracker.TrackerLog
import com.intellij.ide.util.PropertiesComponent

import static liveplugin.PluginUtil.show


def trackerLog = new TrackerLog(pluginPath + "/stats")
def tracker = new ActivityTracker(trackerLog, pluginDisposable)
def propertiesComponent = PropertiesComponent.instance
def plugin = new ActivityTrackerPlugin(tracker, trackerLog, propertiesComponent, pluginDisposable).init()
new PluginUI(plugin).init(pluginDisposable)

if (!isIdeStartup) show("Reloaded ActivityTracker")
