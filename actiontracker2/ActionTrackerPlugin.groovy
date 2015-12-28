package actiontracker2
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import liveplugin.implementation.GlobalVar

import static liveplugin.PluginUtil.newGlobalVar
import static liveplugin.implementation.Misc.newDisposable

class ActionTrackerPlugin {
	private final ActionTracker tracker
	private final Disposable pluginDisposable
	private final GlobalVar<Boolean> isTracking
	private Disposable trackingDisposable
	PluginUI pluginUI

	ActionTrackerPlugin(ActionTracker tracker, Disposable pluginDisposable) {
		this.tracker = tracker
		this.pluginDisposable = pluginDisposable
		this.isTracking = newGlobalVar("ActionTrackerII.isTracking", false) as GlobalVar<Boolean>
	}

	def onIdeStartup() {
		isTracking.set(true)
		trackingDisposable = newDisposable([pluginDisposable])
		tracker.startTracking(trackingDisposable)
		pluginUI.update(isTracking.get())
	}

	def toggleTracking() {
		isTracking.set{ !it }
		if (isTracking.get()) {
			trackingDisposable = newDisposable([pluginDisposable])
			tracker.startTracking(trackingDisposable)
		} else {
			if (trackingDisposable != null) {
				Disposer.dispose(trackingDisposable)
			}
		}
		pluginUI.update(isTracking.get())
	}
}
