package activitytracker

import activitytracker.ActivityTrackerPlugin2.Companion.pluginId
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import groovy.lang.Closure
import liveplugin.PluginUtil.*
import org.jetbrains.annotations.NotNull
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent

class PluginUI2(
        val plugin: ActivityTrackerPlugin2,
        val trackerLog: TrackerLog,
        val parentDisposable: Disposable)
{
    val log = Logger.getInstance(PluginUI2::class.java)
    var state: ActivityTrackerPlugin2.State = ActivityTrackerPlugin2.State.defaultValue

    fun init(): PluginUI2 {
        plugin.setPluginUI(this)
        registerWidget(parentDisposable)
        registerPopup(parentDisposable)
        return this
    }

    fun update(state: ActivityTrackerPlugin2.State) {
        this.state = state
        updateWidget(widgetId)
    }

    private fun registerPopup(parentDisposable: Disposable) {
        registerAction("$pluginId-Popup", "ctrl shift alt O", "", "Activity Tracker Popup", parentDisposable, toGroovyClosure(this, { actionEvent: AnActionEvent ->
            val project = actionEvent.project
            if (project != null) {
                createListPopup(actionEvent.dataContext).showCenteredInCurrentWindow(project)
            }
        }))
    }

    private fun registerWidget(parentDisposable: Disposable) {
        val presentation = object : StatusBarWidget.TextPresentation {
            override fun getText(): String {
                return "Activity tracker: " + (if (state.isTracking) "on" else "off")
            }

            override fun getTooltipText(): String {
                return "Click to open menu"
            }

            override fun getClickConsumer(): Consumer<MouseEvent> {
                return Consumer { mouseEvent ->
                    val dataContext = newDataContext().put(PlatformDataKeys.CONTEXT_COMPONENT.name, mouseEvent.component)
                    val popup = createListPopup(dataContext)
                    val dimension = popup.content.preferredSize
                    val point = Point(0, -dimension.height)
                    popup.show(RelativePoint(mouseEvent.component, point))
                }
            }

            override fun getAlignment(): Float {
                return Component.CENTER_ALIGNMENT
            }

            @Suppress("OverridingDeprecatedMember")
            @NotNull override fun getMaxPossibleText(): String {
                return ""
            }
        }
        liveplugin.PluginUtil.registerWidget(widgetId, parentDisposable, "before Position", presentation)
    }

    private fun createListPopup(dataContext: DataContext): ListPopup {
        // TODO
        return JBPopupFactory.getInstance().createActionGroupPopup(
                "Activity Tracker",
                null!!, // TODO actionGroup,
                dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
        )
    }

    companion object {
        val widgetId = "$pluginId-Widget"

        private fun toGroovyClosure(owner: Any, lambda: (actionEvent: AnActionEvent) -> (Unit)): Closure<Unit> {
            return object : Closure<Unit>(owner) {
                override fun call(vararg args: Any?): Unit? {
                    return lambda(args[0] as AnActionEvent)
                }
            }
        }
    }
}