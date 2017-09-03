package activitytracker

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ApplicationComponent

class AppComponent: ApplicationComponent {

    override fun initComponent() {
        val disposable = ApplicationManager.getApplication()
        val pathToTrackingLogFile = "${PathManager.getPluginsPath()}/activity-tracker/ide-events.csv"
        val trackerLog = TrackerLog(pathToTrackingLogFile).initWriter(disposable, writeFrequencyMs = 10000L)
        val tracker = ActivityTracker(trackerLog, disposable, logTrackerCallDuration = false)
        val plugin = ActivityTrackerPlugin(tracker, trackerLog, PropertiesComponent.getInstance()).init()
        val eventAnalyzer = EventAnalyzer(trackerLog)
        PluginUI(plugin, trackerLog, eventAnalyzer, disposable).init()
    }

    override fun disposeComponent() {}

    override fun getComponentName(): String = "Activity Tracker"
}
