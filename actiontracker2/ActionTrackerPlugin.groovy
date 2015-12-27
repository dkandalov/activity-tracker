package actiontracker2

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import liveplugin.implementation.GlobalVar

import static liveplugin.PluginUtil.newGlobalVar

class ActionTrackerPlugin {
	private final ActionTracker tracker
	private final Disposable pluginDisposable
	private final GlobalVar<Boolean> isTracking
	private final GlobalVar<Disposable> trackingDisposable
	PluginUI pluginUI

	ActionTrackerPlugin(ActionTracker tracker, Disposable pluginDisposable) {
		this.tracker = tracker
		this.pluginDisposable = pluginDisposable
		this.isTracking = newGlobalVar("ActionTrackerII.isTracking", false) as GlobalVar<Boolean>
		this.trackingDisposable = newGlobalVar("ActionTrackerII.disposable") as GlobalVar<Disposable>
	}

	def onIdeStartup() {
		isTracking.set(true)
		tracker.startTracking(trackingDisposable.get())
		pluginUI.update(isTracking.get())
	}

	def toggleTracking() {
		isTracking.set{ !it }
		if (isTracking.get()) {
			def disposable = new Disposable() {
				@Override void dispose() {
					isTracking.set(false)
				}
			}
			Disposer.register(pluginDisposable, disposable)
			trackingDisposable.set(disposable)

			tracker.startTracking(disposable)
		} else {
			def disposable = trackingDisposable.get()
			if (disposable != null) {
				Disposer.dispose(disposable)
			}
		}
		pluginUI.update(isTracking.get())
	}
}
