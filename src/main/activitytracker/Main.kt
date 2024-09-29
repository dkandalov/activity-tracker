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
        val trackerLog = TrackerLog("${PathManager.getPluginsPath()}/activity-tracker/ide-events.csv")
            .initWriter(parentDisposable = application, writeFrequencyMs = 10000L)
        PluginUI(
            Plugin(
                ActivityTracker(
                    CompilationTracker.instance,
                    PsiPathProvider.instance,
                    TaskNameProvider.instance,
                    trackerLog,
                    parentDisposable = application,
                    logTrackerCallDuration = false
                ),
                trackerLog,
                PropertiesComponent.getInstance()
            ).init(),
            trackerLog,
            EventAnalyzer(trackerLog),
            parentDisposable = application
        ).init()
    }
}
