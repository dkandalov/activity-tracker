package actiontracker2

import com.intellij.ide.actions.ShowFilePathAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import liveplugin.implementation.GlobalVar

import static liveplugin.PluginUtil.newGlobalVar
import static liveplugin.PluginUtil.openInEditor
import static liveplugin.implementation.Misc.newDisposable

class ActionTrackerPlugin {
	private final ActionTracker tracker
	private final Disposable pluginDisposable
	private final GlobalVar<Boolean> isTracking
	private Disposable trackingDisposable
	PluginUI pluginUI
	private final TrackerLog trackerLog

	ActionTrackerPlugin(ActionTracker tracker, TrackerLog trackerLog, Disposable pluginDisposable) {
		this.tracker = tracker
		this.trackerLog = trackerLog
		this.pluginDisposable = pluginDisposable
		this.isTracking = newGlobalVar("ActionTrackerII.isTracking", false) as GlobalVar<Boolean>
	}

	def onIdeStartup() {
		isTracking.set(false)
		toggleTracking()
	}

	def toggleTracking() {
		isTracking.set{ !it }
		if (isTracking.get()) {
			trackingDisposable = newDisposable([pluginDisposable])
			tracker.startTracking(trackingDisposable, 0)
		} else {
			if (trackingDisposable != null) {
				Disposer.dispose(trackingDisposable)
			}
		}
		pluginUI.update(isTracking.get())
	}

	def openTrackingLogFile(Project project) {
		openInEditor(trackerLog.currentLogFile().absolutePath, project)
	}

	def openTrackingLogFolder() {
		ShowFilePathAction.openFile(trackerLog.currentLogFile().parentFile)
	}
}
