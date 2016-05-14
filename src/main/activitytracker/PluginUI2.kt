package activitytracker

import activitytracker.ActivityTrackerPlugin2.Companion.pluginId
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowFilePathAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import groovy.lang.Closure
import liveplugin.PluginUtil.*
import liveplugin.implementation.Misc
import org.jetbrains.annotations.NotNull
import java.awt.Component
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.event.HyperlinkEvent

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
        val toggleTracking = object : AnAction() {
            override fun actionPerformed(event: AnActionEvent) {
                plugin.toggleTracking()
            }
            override fun update(event: AnActionEvent) {
                event.presentation.text = if (state.isTracking) "Stop Tracking" else "Start Tracking"
            }
        }
        val togglePollIdeState = object : CheckboxAction("Poll IDE State") {
            override fun isSelected(event: AnActionEvent): Boolean { return state.pollIdeState }
            override fun setSelected(event: AnActionEvent, value: Boolean) { plugin.enablePollIdeState(value) }
        }
        val toggleTrackActions = object : CheckboxAction("Track IDE Actions") {
            override fun isSelected(event: AnActionEvent): Boolean { return state.trackIdeActions }
            override fun setSelected(event: AnActionEvent, value: Boolean) { plugin.enableTrackIdeActions(value) }
        }
        val toggleTrackKeyboard = object : CheckboxAction("Track Keyboard") {
            override fun isSelected(event: AnActionEvent): Boolean { return state.trackKeyboard }
            override fun setSelected(event: AnActionEvent, value: Boolean) { plugin.enableTrackKeyboard(value) }
        }
        val toggleTrackMouse = object : CheckboxAction("Track Mouse") {
            override fun isSelected(event: AnActionEvent): Boolean { return state.trackMouse }
            override fun setSelected(event: AnActionEvent, value: Boolean) { plugin.enableTrackMouse(value) }
        }
        val openLogInIde = object : AnAction("Open in IDE") {
            override fun actionPerformed(event: AnActionEvent) {
                plugin.openTrackingLogFile(event.project)
            }
        }
        val openLogFolder = object : AnAction("Open in File Manager") {
            override fun actionPerformed(event: AnActionEvent) {
                plugin.openTrackingLogFolder()
            }
        }
        data class Error(val line: String, val e: Exception)
        val showStatistics = object : AnAction("Show Stats") {
            override fun actionPerformed(event: AnActionEvent) {
                doInBackground("Analysing activity log", toGroovyClosure(this, {
                    val errors = arrayListOf<Error>()
                    val events = trackerLog.readEvents(toGroovyClosure2(this, { line: String, e: Exception ->
                        errors.add(Error(line, e))
                    })) // TODO use kotlin closure

                    if (events.isEmpty()) {
                        showNotification("There are no recorded events to analyze")
                    } else {
                        val secondsInEditorByFile = EventsAnalyzer.secondsInEditorByFile(events)
                        val secondsByProject = EventsAnalyzer.secondsByProject(events)
                        val countByActionId = EventsAnalyzer.countByActionId(events)
                        invokeLaterOnEDT {
                            StatsToolWindow.showIn(
                                    event.project,
                                    secondsInEditorByFile,
                                    secondsByProject,
                                    countByActionId,
                                    parentDisposable
                            )
                        }
                    }
                    if (!errors.isEmpty()) {
                        showNotification("There were ${errors.size} errors parsing log file. See IDE log for details.")
                        errors.take(20).forEach {
                            log.warn(it.line, it.e)
                        }
                    }
                }))
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
            override fun actionPerformed(event: AnActionEvent) {
                BrowserUtil.open("https://github.com/dkandalov/activity-tracker#help")
            }
        }

        registerAction("Start/Stop Activity Tracking", toggleTracking)
        registerAction("Roll Tracking Log", rollCurrentLog)
        registerAction("Clear Tracking Log", clearCurrentLog)
        // TODO register other actions

        val actionGroup = DefaultActionGroup().run {
            add(toggleTracking)
            add(DefaultActionGroup("Current Log", true).run {
                add(showStatistics)
                add(openLogInIde)
                add(openLogFolder)
                addSeparator()
                add(rollCurrentLog)
                add(clearCurrentLog)
                this
            })
            addSeparator()
            add(DefaultActionGroup("Settings", true).run {
                add(toggleTrackActions)
                add(togglePollIdeState)
                add(toggleTrackKeyboard)
                add(toggleTrackMouse)
                this
            })
            add(openHelp)
            this
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

        private fun toGroovyClosure(owner: Any, lambda: (actionEvent: AnActionEvent) -> (Unit)): Closure<Unit> {
            return object : Closure<Unit>(owner) {
                override fun call(vararg args: Any?): Unit? {
                    return lambda(args[0] as AnActionEvent)
                }
            }
        }
        private fun toGroovyClosure2(owner: Any, lambda: (line: String, e: Exception) -> (Unit)): Closure<Unit> {
            return object : Closure<Unit>(owner) {
                override fun call(vararg args: Any?): Unit? {
                    return lambda(args[0] as String, args[1] as Exception)
                }
            }
        }

        private fun invokeLaterOnEDT(callback: () -> Unit) {
            ApplicationManager.getApplication().invokeLater{ callback() }
        }

    }
}