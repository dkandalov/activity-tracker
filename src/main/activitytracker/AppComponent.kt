package activitytracker

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager

class AppComponent {
    init {
        val application = ApplicationManager.getApplication()
        application.messageBus.connect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
            override fun appFrameCreated(commandLineArgs: List<String>) {

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
                val plugin = ActivityTrackerPlugin(tracker, trackerLog, PropertiesComponent.getInstance()).init()
                val eventAnalyzer = EventAnalyzer(trackerLog)
                PluginUI(plugin, trackerLog, eventAnalyzer, parentDisposable = application).init()

            }
        })
    }
}
