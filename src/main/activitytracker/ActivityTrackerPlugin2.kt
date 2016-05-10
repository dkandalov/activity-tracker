package activitytracker

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import liveplugin.PluginUtil.newGlobalVar
import liveplugin.implementation.GlobalVar

class ActivityTrackerPlugin2(
        tracker: ActivityTracker, trackerLog: TrackerLog,
        propertiesComponent: PropertiesComponent, parentDisposable: Disposable
) {
    private val stateVar: GlobalVar<State> = newGlobalVar("$pluginId.state", null)

    // TODO convert the rest of the class

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
    }
}