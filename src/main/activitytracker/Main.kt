package activitytracker

import activitytracker.tracking.ActivityTracker
import activitytracker.tracking.CompilationTracker
import activitytracker.tracking.PsiPathProvider
import activitytracker.tracking.TaskNameProvider
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager

class Main: AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        val application = ApplicationManager.getApplication()
        val pathToTrackingLogFile = "${PathManager.getPluginsPath()}/activity-tracker/ide-events.csv"
        val trackerLog = TrackerLog(pathToTrackingLogFile).initWriter(parentDisposable = application, writeFrequencyMs = 10000L)
        val tracker = ActivityTracker(
            CompilationTracker.instance,
            PsiPathProvider.instance,
            TaskNameProvider.instance,
            trackerLog,
            parentDisposable = application,
            logTrackerCallDuration = false
        )
        val plugin = Plugin(tracker, trackerLog, PropertiesComponent.getInstance()).init()
        val eventAnalyzer = EventAnalyzer(trackerLog)
        PluginUI(plugin, trackerLog, eventAnalyzer, parentDisposable = application).init()
    }
}
