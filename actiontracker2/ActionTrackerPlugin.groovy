package actiontracker2
import com.intellij.ide.actions.ShowFilePathAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import groovy.transform.Immutable
import liveplugin.implementation.GlobalVar

import static liveplugin.PluginUtil.newGlobalVar
import static liveplugin.PluginUtil.openInEditor

class ActionTrackerPlugin {
	static final pluginId = "ActionTrackerII"

	private final ActionTracker tracker
	private final TrackerLog trackerLog
	private final Disposable parentDisposable
	private final GlobalVar<State> stateVar
	PluginUI pluginUI


	ActionTrackerPlugin(ActionTracker tracker, TrackerLog trackerLog, Disposable parentDisposable) {
		this.tracker = tracker
		this.trackerLog = trackerLog
		this.parentDisposable = parentDisposable
		this.stateVar = newGlobalVar("${pluginId}.state", State.defaultValue) as GlobalVar<State>
	}

	def init() {
		// TODO load persisted state
		onUpdate(null, stateVar.get())
		this
	}

	def setPluginUI(PluginUI pluginUI) {
		this.pluginUI = pluginUI
		pluginUI.update(stateVar.get())
	}

	def toggleTracking() {
		updateState{ it.copyWith(isTracking: !it.isTracking) }
	}

	def enablePollIdeState(boolean value) {
		updateState{ it.copyWith(pollIdeState: value) }
	}

	def enableTrackIdeActions(boolean value) {
		updateState{ it.copyWith(trackIdeActions: value) }
	}

	def enableTrackKeyboard(boolean value) {
		updateState{ it.copyWith(trackKeyboard: value) }
	}

	def enableTrackMouse(boolean value) {
		updateState{ it.copyWith(trackMouse: value) }
	}

	def openTrackingLogFile(Project project) {
		openInEditor(trackerLog.currentLogFile().absolutePath, project)
	}

	def openTrackingLogFolder() {
		ShowFilePathAction.openFile(trackerLog.currentLogFile().parentFile)
	}

	private updateState(Closure<State> closure) {
		stateVar.set { State oldValue ->
			def newValue = closure.call(oldValue)
			onUpdate(oldValue, newValue)
			newValue
		}
	}

	private onUpdate(State old, State it) {
		if (old == it) return

		tracker.stopTracking()
		if (it.isTracking) {
			tracker.startTracking(asTrackerConfig(it))
		}
		pluginUI?.update(it)
	}

	private static ActionTracker.Config asTrackerConfig(State state) {
		new ActionTracker.Config(
				state.pollIdeState,
				state.trackIdeActions,
				state.trackKeyboard,
				state.trackMouse
		)
	}

	@Immutable(copyWith = true)
	static final class State {
		static final State defaultValue = new State(true, true, true, true, true)
		boolean isTracking
		boolean pollIdeState
		boolean trackIdeActions
		boolean trackKeyboard
		boolean trackMouse
	}
}
