package activitytracker

import activitytracker.ActivityTrackerPlugin.Companion.pluginId
import activitytracker.liveplugin.invokeLaterOnEDT
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowFilePathAction
import com.intellij.notification.*
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.ui.popup.*
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import liveplugin.PluginUtil.*
import liveplugin.implementation.*
import liveplugin.implementation.Threads.doInBackground
import org.jetbrains.annotations.NotNull
import java.awt.*
import java.awt.event.MouseEvent
import java.util.ArrayList
import java.util.function.Function
import javax.swing.event.HyperlinkEvent

class PluginUI(
        val plugin: ActivityTrackerPlugin,
        val trackerLog: TrackerLog,
        val parentDisposable: Disposable)
{
    private val log = Logger.getInstance(PluginUI::class.java)
    private var state: ActivityTrackerPlugin.State = ActivityTrackerPlugin.State.defaultValue

    fun init(): PluginUI {
        plugin.setPluginUI(this)
        registerWidget(parentDisposable)
        registerPopup(parentDisposable)
        return this
    }

    fun update(state: ActivityTrackerPlugin.State) {
        this.state = state
        updateWidget(widgetId)
    }

    private fun registerPopup(parentDisposable: Disposable) {
        Actions.registerAction("$pluginId-Popup", "ctrl shift alt O", "", "Activity Tracker Popup", parentDisposable, Function<AnActionEvent, Any> {
            val project = it.project
            if (project != null) {
                createListPopup(it.dataContext).showCenteredInCurrentWindow(project)
            }
        })
    }

    private fun registerWidget(parentDisposable: Disposable) {
        val presentation = object : StatusBarWidget.TextPresentation {
            override fun getText() = "Activity tracker: " + (if (state.isTracking) "on" else "off")

            override fun getTooltipText() = "Click to open menu"

            override fun getClickConsumer(): Consumer<MouseEvent> = Consumer { mouseEvent ->
                val dataContext = newDataContext().put(PlatformDataKeys.CONTEXT_COMPONENT.name, mouseEvent.component)
                val popup = createListPopup(dataContext)
                val dimension = popup.content.preferredSize
                val point = Point(0, -dimension.height)
                popup.show(RelativePoint(mouseEvent.component, point))
            }

            override fun getAlignment() = Component.CENTER_ALIGNMENT

            @Suppress("OverridingDeprecatedMember")
            @NotNull override fun getMaxPossibleText() = ""
        }
        liveplugin.PluginUtil.registerWidget(widgetId, parentDisposable, "before Position", presentation)
    }

    private fun createListPopup(dataContext: DataContext): ListPopup {
        val toggleTracking = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) = plugin.toggleTracking()
            override fun update(event: AnActionEvent) {
                event.presentation.text = if (state.isTracking) "Stop Tracking" else "Start Tracking"
            }
        }
        val togglePollIdeState = object : CheckboxAction("Poll IDE State") {
            override fun isSelected(event: AnActionEvent) = state.pollIdeState
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enablePollIdeState(value)
        }
        val toggleTrackActions = object : CheckboxAction("Track IDE Actions") {
            override fun isSelected(event: AnActionEvent) = state.trackIdeActions
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackIdeActions(value)
        }
        val toggleTrackKeyboard = object : CheckboxAction("Track Keyboard") {
            override fun isSelected(event: AnActionEvent) = state.trackKeyboard
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackKeyboard(value)
        }
        val toggleTrackMouse = object : CheckboxAction("Track Mouse") {
            override fun isSelected(event: AnActionEvent) = state.trackMouse
            override fun setSelected(event: AnActionEvent, value: Boolean) = plugin.enableTrackMouse(value)
        }
        val openLogInIde = object : AnAction("Open in IDE") {
            override fun actionPerformed(event: AnActionEvent) = plugin.openTrackingLogFile(event.project)
        }
        val openLogFolder = object : AnAction("Open in File Manager") {
            override fun actionPerformed(event: AnActionEvent) = plugin.openTrackingLogFolder()
        }
        val showStatistics = object : AnAction("Show Stats") {
            override fun actionPerformed(event: AnActionEvent) {
                doInBackground("Analysing activity log", {
                    val project = event.project
                    if (trackerLog.isTooLargeToProcess()) {
                        showNotification("Current activity log is too large to process in IDE.")
                    } else if (project != null) {
                        val errors = ArrayList<Pair<String, Exception>>()
                        val events = trackerLog.readEventSequence(onParseError = { line: String, e: Exception ->
                            errors.add(Pair(line, e))
                            if (errors.size > 20) errors.removeAt(0)
                        })

                        val stats = analyze(events)

                        invokeLaterOnEDT {
                            StatsToolWindow.showIn(project, stats, trackerLog.eventsFilePath, parentDisposable)
                        }

                        if (errors.isNotEmpty()) {
                            showNotification("There were ${errors.size} errors parsing log file. See IDE log for details.")
                            errors.forEach { log.warn(it.first, it.second) }
                        }
                    }
                })
            }
        }
        val rollCurrentLog = object : AnAction("Roll Tracking Log") {
            override fun actionPerformed(event: AnActionEvent) {
                val userAnswer = showOkCancelDialog(event.project,
                    "Roll tracking log file?\nCurrent log will be moved into new file.",
                    "Activity Tracker",
                    Messages.getQuestionIcon()
                )
                if (userAnswer != Messages.OK) return

                val rolledFile = trackerLog.rollLog()
                showNotification("Rolled tracking log into <a href=''>${rolledFile.name}</a>") {
                    ShowFilePathAction.openFile(rolledFile)
                }
            }
        }
        val clearCurrentLog = object : AnAction("Clear Tracking Log") {
            override fun actionPerformed(event: AnActionEvent) {
                val userAnswer = showOkCancelDialog(event.project,
                    "Clear current tracking log file?\n(This operation cannot be undone.)",
                    "Activity Tracker",
                    Messages.getQuestionIcon()
                )
                if (userAnswer != Messages.OK) return

                val wasCleared = trackerLog.clearLog()
                if (wasCleared) showNotification("Tracking log was cleared")
            }
        }
        val openHelp = object : AnAction("Help") {
            override fun actionPerformed(event: AnActionEvent) = BrowserUtil.open("https://github.com/dkandalov/activity-tracker#help")
        }

        registerAction("Start/Stop Activity Tracking", toggleTracking)
        registerAction("Roll Tracking Log", rollCurrentLog)
        registerAction("Clear Tracking Log", clearCurrentLog)
        // TODO register other actions

        val actionGroup = DefaultActionGroup().apply {
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
                add(toggleTrackMouse)
            })
            add(openHelp)
        }

        return JBPopupFactory.getInstance().createActionGroupPopup(
                "Activity Tracker",
                actionGroup,
                dataContext,
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true
        )
    }

    companion object {
        val widgetId = "$pluginId-Widget"

        private fun showNotification(message: Any?, onLinkClick: (HyperlinkEvent) -> Unit = {}) {
            invokeLaterOnEDT {
                val messageString = Misc.asString(message)
                val title = ""
                val notificationType = INFORMATION
                val groupDisplayId = pluginId
                val notification = Notification(
                        groupDisplayId, title, messageString, notificationType,
                        NotificationListener { notification, event ->
                            onLinkClick(event)
                        }
                )
                ApplicationManager.getApplication().messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
            }
        }
    }
}