package activitytracker

import activitytracker.liveplugin.openInEditor
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

class ActivityTrackerPlugin(
    private val tracker: ActivityTracker,
    private val trackerLog: TrackerLog,
    private val propertiesComponent: PropertiesComponent
) {
    private var state: State = State.defaultValue
    private var pluginUI: PluginUI? = null

    fun init(): ActivityTrackerPlugin {
        state = State.load(propertiesComponent, pluginId)
        onStateChange(state)
        return this
    }

    fun setPluginUI(pluginUI: PluginUI) {
        this.pluginUI = pluginUI
        pluginUI.update(state)
    }

    fun toggleTracking() = updateState { it.copy(isTracking = !it.isTracking) }

    fun enablePollIdeState(value: Boolean) = updateState { it.copy(pollIdeState = value) }

    fun enableTrackIdeActions(value: Boolean) = updateState { it.copy(trackIdeActions = value) }

    fun enableTrackKeyboard(value: Boolean) = updateState { it.copy(trackKeyboard = value) }

    fun enableTrackMouse(value: Boolean) = updateState { it.copy(trackMouse = value) }

    fun openTrackingLogFile(project: Project?) {
        if (project == null) return
        openInEditor(trackerLog.currentLogFile().absolutePath, project)
    }

    fun openTrackingLogFolder() {
        RevealFileAction.openFile(trackerLog.currentLogFile().parentFile)
    }

    private fun updateState(closure: (State) -> State) {
        val oldState = state
        state = closure(state)
        if (oldState != state) {
            onStateChange(state)
        }
    }

    private fun onStateChange(newState: State) {
        tracker.stopTracking()
        if (newState.isTracking) {
            tracker.startTracking(newState.toConfig())
        }
        pluginUI?.update(newState)
        newState.save(propertiesComponent, pluginId)
    }

    private fun State.toConfig() = ActivityTracker.Config(
        pollIdeState,
        pollIdeStateMs.toLong(),
        trackIdeActions,
        trackKeyboard,
        trackMouse,
        mouseMoveEventsThresholdMs.toLong()
    )


    data class State(
        val isTracking: Boolean,
        val pollIdeState: Boolean,
        val pollIdeStateMs: Int,
        val trackIdeActions: Boolean,
        val trackKeyboard: Boolean,
        val trackMouse: Boolean,
        val mouseMoveEventsThresholdMs: Int
    ) {
        fun save(propertiesComponent: PropertiesComponent, id: String) {
            propertiesComponent.run {
                setValue("$id-isTracking", isTracking, defaultValue.isTracking)
                setValue("$id-pollIdeState", pollIdeState, defaultValue.pollIdeState)
                setValue("$id-pollIdeStateMs", pollIdeStateMs, defaultValue.pollIdeStateMs)
                setValue("$id-trackIdeActions", trackIdeActions, defaultValue.trackIdeActions)
                setValue("$id-trackKeyboard", trackKeyboard, defaultValue.trackKeyboard)
                setValue("$id-trackMouse", trackMouse, defaultValue.trackMouse)
                setValue("$id-mouseMoveEventsThresholdMs", mouseMoveEventsThresholdMs, defaultValue.mouseMoveEventsThresholdMs)
            }
        }

        companion object {
            val defaultValue = State(
                isTracking = true,
                pollIdeState = true,
                pollIdeStateMs = 1000,
                trackIdeActions = true,
                trackKeyboard = false,
                trackMouse = false,
                mouseMoveEventsThresholdMs = 250
            )

            fun load(propertiesComponent: PropertiesComponent, id: String): State {
                return propertiesComponent.run {
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
    }

    companion object {
        const val pluginId = "ActivityTracker"
    }
}