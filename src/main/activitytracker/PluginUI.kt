package activitytracker

import activitytracker.EventAnalyzer.Result.*
import activitytracker.Plugin.Companion.pluginId
import activitytracker.liveplugin.*
import com.intellij.CommonBundle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.ui.IdeUiService
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.SPEEDSEARCH
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent

class PluginUI(
    private val plugin: Plugin,
    private val trackerLog: TrackerLog,
    private val eventAnalyzer: EventAnalyzer,
    private val parentDisposable: Disposable
) {
    private val log = Logger.getInstance(PluginUI::class.java)
    private var state = Plugin.State.defaultValue
    private val actionGroup by lazy { createActionGroup() }
    private val widgetId = "Activity Tracker Widget"

    fun init(): PluginUI {
        plugin.setUI(this)
        registerWidget(parentDisposable)
        registerPopup(parentDisposable)
        eventAnalyzer.runner = { task ->
            runInBackground("Analyzing activity log", task = { task() })
        }
        return this
    }

    fun update(state: Plugin.State) {
        this.state = state
        updateWidget(widgetId)
    }

    private fun registerPopup(parentDisposable: Disposable) {
        registerAction("$pluginId-Popup", "ctrl shift alt O", "", "Activity Tracker Popup", parentDisposable) {
            val project = it.project
            if (project != null) {
                createListPopup(it.dataContext).showCenteredInCurrentWindow(project)
            }
        }
    }

    private fun registerWidget(parentDisposable: Disposable) {
        val presentation = object : StatusBarWidget.TextPresentation {
            override fun getText() = "Activity tracker: " + (if (state.isTracking) "on" else "off")
            override fun getAlignment() = Component.CENTER_ALIGNMENT
            override fun getTooltipText() = "Click to open menu"
            override fun getClickConsumer() = Consumer { mouseEvent: MouseEvent ->
                val popup = createListPopup(MapDataContext().put(PlatformDataKeys.CONTEXT_COMPONENT.name, mouseEvent.component))
                val point = Point(0, -popup.content.preferredSize.height)
                popup.show(RelativePoint(mouseEvent.component, point))
            }
        }

        val extensionPoint = StatusBarWidgetFactory.EP_NAME.getPoint { Extensions.getRootArea() }
        val factory = object : StatusBarWidgetFactory {
            override fun getId() = widgetId
            override fun getDisplayName() = widgetId
            override fun isAvailable(project: Project) = true
            override fun canBeEnabledOn(statusBar: StatusBar) = true
            override fun createWidget(project: Project) = object : StatusBarWidget, StatusBarWidget.Multiframe {
                override fun ID() = widgetId
                override fun getPresentation() = presentation
                override fun install(statusBar: StatusBar) = statusBar.updateWidget(ID())
                override fun copy() = this
                override fun dispose() {}
            }

            override fun disposeWidget(widget: StatusBarWidget) {}
        }
        @Suppress("UnstableApiUsage")
        extensionPoint.registerExtension(factory, LoadingOrder.before("positionWidget"), parentDisposable)
    }

    private fun createListPopup(dataContext: DataContext) =
        JBPopupFactory.getInstance()
            .createActionGroupPopup("Activity Tracker", actionGroup, dataContext, SPEEDSEARCH, true)

    private fun createActionGroup(): DefaultActionGroup {
        val toggleTracking = object : AnAction(), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = plugin.toggleTracking()
            override fun getActionUpdateThread() = EDT
            override fun update(event: AnActionEvent) {
                event.presentation.text = if (state.isTracking) "Stop Tracking" else "Start Tracking"
            }
        }
        val togglePollIdeState = object : CheckboxAction("Poll IDE state") {
            override fun isSelected(event: AnActionEvent) = state.pollIdeState
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enablePollIdeState(value)
            override fun getActionUpdateThread() = EDT
        }
        val toggleTrackActions = object : CheckboxAction("Track IDE actions") {
            override fun isSelected(event: AnActionEvent) = state.trackIdeActions
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackIdeActions(value)
            override fun getActionUpdateThread() = EDT
        }
        val toggleTrackKeyboard = object : CheckboxAction("Track keyboard") {
            override fun isSelected(event: AnActionEvent) = state.trackKeyboard
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackKeyboard(value)
            override fun getActionUpdateThread() = EDT
        }
        val trackKeyboardReleased = object : CheckboxAction("Track keyboard(Released)") {
            override fun isSelected(event: AnActionEvent) = state.trackKeyboardReleased
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackKeyboardReleased(value)
            override fun getActionUpdateThread() = EDT
        }
        val toggleTrackMouse = object : CheckboxAction("Track mouse") {
            override fun isSelected(event: AnActionEvent) = state.trackMouse
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackMouse(value)
            override fun getActionUpdateThread() = EDT
        }
        val openLogInIde = object : AnAction("Open in IDE"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = plugin.openTrackingLogFile(event.project)
        }
        val openLogFolder = object : AnAction("Open in File Manager"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = plugin.openTrackingLogFolder()
        }
        val showStatistics = object : AnAction("Show Stats"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                val project = event.project
                if (trackerLog.isTooLargeToProcess()) {
                    showNotification("Current activity log is too large to process in IDE.")
                } else if (project != null) {
                    eventAnalyzer.analyze(whenDone = { result ->
                        invokeLaterOnEDT {
                            when (result) {
                                is Ok -> {
                                    StatsToolWindow.showIn(project, result.stats, eventAnalyzer, parentDisposable)
                                    if (result.errors.isNotEmpty()) {
                                        showNotification("There were ${result.errors.size} errors parsing log file. See IDE log for details.")
                                        result.errors.forEach { log.warn(it.first, it.second) }
                                    }
                                }
                                is AlreadyRunning -> showNotification("Analysis is already running.")
                                is DataIsTooLarge -> showNotification("Activity log is too large to process in IDE.")
                            }
                        }
                    })
                }
            }
        }
        val rollCurrentLog = object : AnAction("Roll Tracking Log"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                val userAnswer = showOkCancelDialog(
                    event.project,
                    "Roll tracking log file?\nCurrent log will be moved into new file.",
                    "Activity Tracker",
                    CommonBundle.getOkButtonText(),
                    CommonBundle.getCancelButtonText(),
                    Messages.getQuestionIcon()
                )
                if (userAnswer != Messages.OK) return

                val rolledFile = trackerLog.rollLog()
                showNotification("Rolled tracking log into <a href=''>${rolledFile.name}</a>") {
                    RevealFileAction.openFile(rolledFile)
                }
            }
        }
        val clearCurrentLog = object : AnAction("Clear Tracking Log"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) {
                val userAnswer = showOkCancelDialog(
                    event.project,
                    "Clear current tracking log file?\n(This operation cannot be undone.)",
                    "Activity Tracker",
                    CommonBundle.getOkButtonText(),
                    CommonBundle.getCancelButtonText(),
                    Messages.getQuestionIcon()
                )
                if (userAnswer != Messages.OK) return

                val wasCleared = trackerLog.clearLog()
                if (wasCleared) showNotification("Tracking log was cleared")
            }
        }
        val openHelp = object : AnAction("Help"), DumbAware {
            override fun actionPerformed(event: AnActionEvent) = BrowserUtil.open("https://github.com/dkandalov/activity-tracker#help")
        }

        registerAction("Start/Stop Activity Tracking", action = toggleTracking)
        registerAction("Roll Tracking Log", action = rollCurrentLog)
        registerAction("Clear Tracking Log", action = clearCurrentLog)
        // TODO register other actions

        return DefaultActionGroup().apply {
            add(toggleTracking)
            add(DefaultActionGroup("Current Log", true).apply {
                add(showStatistics)
                add(openLogInIde)
                add(openLogFolder)
                addSeparator()
                add(rollCurrentLog)
                add(clearCurrentLog)
            })
            addSeparator()
            add(DefaultActionGroup("Settings", true).apply {
                add(toggleTrackActions)
                add(togglePollIdeState)
                add(toggleTrackKeyboard)
                add(trackKeyboardReleased)
                add(toggleTrackMouse)
            })
            add(openHelp)
        }
    }
}