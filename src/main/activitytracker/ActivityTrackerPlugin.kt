package activitytracker

import activitytracker.liveplugin.GlobalVar
import com.intellij.ide.actions.ShowFilePathAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import liveplugin.PluginUtil

class ActivityTrackerPlugin(
        val tracker: ActivityTracker,
        val trackerLog: TrackerLog,
        val propertiesComponent: PropertiesComponent
) {
    private val stateVar: GlobalVar<State> = GlobalVar("$pluginId.state")
    private var pluginUI: PluginUI? = null

    fun init(): ActivityTrackerPlugin {
        updateState{ State.load(propertiesComponent, pluginId) }
        return this
    }

    fun setPluginUI(pluginUI: PluginUI) {
        this.pluginUI = pluginUI
        pluginUI.update(stateVar.get()!!)
    }

    fun toggleTracking() {
        updateState { it.copy(isTracking = !it.isTracking) }
    }

    fun enablePollIdeState(value: Boolean) {
        updateState { it.copy(pollIdeState = value) }
    }

    fun enableTrackIdeActions(value: Boolean) {
        updateState { it.copy(trackIdeActions = value) }
    }

    fun enableTrackKeyboard(value: Boolean) {
        updateState { it.copy(trackKeyboard = value) }
    }

    fun enableTrackMouse(value: Boolean) {
        updateState { it.copy(trackMouse = value) }
    }

    fun openTrackingLogFile(project: Project?) {
        if (project == null) return
        PluginUtil.openInEditor(trackerLog.currentLogFile().absolutePath, project)
    }

    fun openTrackingLogFolder() {
        ShowFilePathAction.openFile(trackerLog.currentLogFile().parentFile)
    }

    private fun updateState(closure: (State) -> State) {
        stateVar.set { oldValue: State? ->
            val value = oldValue ?: State.defaultValue
            val newValue = closure(value)
            onUpdate(value, newValue)
            newValue
        }
    }

    private fun onUpdate(oldState: State, newState: State) {
        if (oldState == newState) return

        tracker.stopTracking()
        if (newState.isTracking) {
            tracker.startTracking(asTrackerConfig(newState))
        }
        pluginUI?.update(newState)
        newState.save(propertiesComponent, pluginId)
    }

    data class State(
            val isTracking: Boolean,
            val pollIdeState: Boolean,
            val pollIdeStateMs: Int,
            val trackIdeActions: Boolean,
            val trackKeyboard: Boolean,
            val trackMouse: Boolean,
            val mouseMoveEventsThresholdMs: Int
    ) {
        companion object {
            val defaultValue = State(true, true, 1000, true, false, false, 250)

            fun load(propertiesComponent: PropertiesComponent, id: String): State {
                return with(propertiesComponent) {
                    State(
                        getBoolean("$id-isTracking", defaultValue.isTracking),
                        getBoolean("$id-pollIdeState", defaultValue.pollIdeState),
                        getInt("$id-pollIdeStateMs", defaultValue.pollIdeStateMs),
                        getBoolean("$id-trackIdeActions", defaultValue.trackIdeActions),
                        getBoolean("$id-trackKeyboard", defaultValue.trackKeyboard),
                        getBoolean("$id-trackMouse", defaultValue.trackMouse),
                        getInt("$id-mouseMoveEventsThresholdMs", defaultValue.mouseMoveEventsThresholdMs)
                    )
                }
            }
        }

        fun save(propertiesComponent: PropertiesComponent, id: String) {
            with(propertiesComponent) {
                setValue("$id-isTracking", isTracking)
                setValue("$id-pollIdeState", pollIdeState)
                setValue("$id-pollIdeStateMs", pollIdeStateMs, Integer.MIN_VALUE)
                setValue("$id-trackIdeActions", trackIdeActions)
                setValue("$id-trackKeyboard", trackKeyboard)
                setValue("$id-trackMouse", trackMouse)
                setValue("$id-mouseMoveEventsThresholdMs", mouseMoveEventsThresholdMs, Integer.MIN_VALUE)
            }
        }

    }

    companion object {
        val pluginId = "ActivityTracker"

        private fun asTrackerConfig(state: State): ActivityTracker.Config {
            return ActivityTracker.Config(
                state.pollIdeState,
                state.pollIdeStateMs.toLong(),
                state.trackIdeActions,
                state.trackKeyboard,
                state.trackMouse,
                state.mouseMoveEventsThresholdMs.toLong()
            )
        }
    }
}