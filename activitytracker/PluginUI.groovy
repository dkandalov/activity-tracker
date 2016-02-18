package activitytracker
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowFilePathAction
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import liveplugin.implementation.Misc
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.event.HyperlinkEvent
import java.awt.*
import java.awt.event.MouseEvent

import static ActivityTrackerPlugin.pluginId
import static com.intellij.notification.NotificationType.INFORMATION
import static com.intellij.openapi.ui.Messages.showOkCancelDialog
import static liveplugin.PluginUtil.*

class PluginUI {
	private final log = Logger.getInstance(PluginUI)

	private static final widgetId = "${pluginId}-Widget"
	private final ActivityTrackerPlugin plugin
	private ActivityTrackerPlugin.State state
	private final TrackerLog trackerLog
	private final Disposable parentDisposable

	PluginUI(ActivityTrackerPlugin plugin, TrackerLog trackerLog, Disposable parentDisposable) {
		this.plugin = plugin
		this.trackerLog = trackerLog
		this.parentDisposable = parentDisposable
	}

	def init() {
		plugin.pluginUI = this
		registerWidget(parentDisposable)
		registerPopup(parentDisposable)
		this
	}

	def update(ActivityTrackerPlugin.State state) {
		this.state = state
		updateWidget(widgetId)
	}

	private registerPopup(Disposable parentDisposable) {
		registerAction("${pluginId}-Popup", "ctrl shift alt O", "", "Activity Tracker Popup", parentDisposable) { AnActionEvent actionEvent ->
			createListPopup(actionEvent.dataContext).showCenteredInCurrentWindow(actionEvent.project)
		}
	}

	private ListPopup createListPopup(DataContext dataContext) {
		def toggleTracking = new AnAction() {
			@Override void actionPerformed(AnActionEvent event) {
				plugin.toggleTracking()
			}
			@Override void update(AnActionEvent event) {
				event.presentation.text = state.isTracking ? "Stop Tracking" : "Start Tracking"
			}
		}
		def togglePollIdeState = new CheckboxAction("Poll IDE State") {
			@Override boolean isSelected(AnActionEvent event) { state.pollIdeState }
			@Override void setSelected(AnActionEvent event, boolean value) { plugin.enablePollIdeState(value) }
		}
		def toggleTrackActions = new CheckboxAction("Track IDE Actions") {
			@Override boolean isSelected(AnActionEvent event) { state.trackIdeActions }
			@Override void setSelected(AnActionEvent event, boolean value) { plugin.enableTrackIdeActions(value) }
		}
		def toggleTrackKeyboard = new CheckboxAction("Track Keyboard") {
			@Override boolean isSelected(AnActionEvent event) { state.trackKeyboard }
			@Override void setSelected(AnActionEvent event, boolean value) { plugin.enableTrackKeyboard(value) }
		}
		def toggleTrackMouse = new CheckboxAction("Track Mouse") {
			@Override boolean isSelected(AnActionEvent event) { state.trackMouse }
			@Override void setSelected(AnActionEvent event, boolean value) { plugin.enableTrackMouse(value) }
		}
		def openLogInIde = new AnAction("Open in IDE") {
			@Override void actionPerformed(AnActionEvent event) {
				plugin.openTrackingLogFile(event.project)
			}
		}
		def openLogFolder = new AnAction("Open in File Manager") {
			@Override void actionPerformed(AnActionEvent event) {
				plugin.openTrackingLogFolder()
			}
		}
		def showStatistics = new AnAction("Show Stats") {
			@Override void actionPerformed(AnActionEvent event) {
				doInBackground("Analysing activity log") {
					def errors = []
					def onParseError = { line, e -> errors << [line: line, e: e] }
					def events = trackerLog.readEvents(onParseError)

					if (events.empty) {
						showNotification("There are no recorded events to analyze")
					} else {
						def secondsInEditorByFile = EventsAnalyzer.secondsInEditorByFile(events)
						def secondsByProject = EventsAnalyzer.secondsByProject(events)
						def countByActionId = EventsAnalyzer.countByActionId(events)
						invokeOnEDT {
							new StatsToolWindow().showIn(
									event.project,
									secondsInEditorByFile,
									secondsByProject,
									countByActionId,
									parentDisposable
							)
						}
					}
					if (!errors.empty) {
						showNotification("There were ${errors.size()} errors parsing log file. See IDE log for details.")
						errors.take(20).each { log.warn(it.line, it.e) }
					}
				}
			}
		}
		def rollCurrentLog = new AnAction("Roll Tracking Log") {
			@Override void actionPerformed(AnActionEvent event) {
				def userAnswer = showOkCancelDialog(event.project,
						"Roll tracking log file?\nCurrent log will be moved into new file.",
						"Activity Tracker",
						Messages.questionIcon
				)
				if (userAnswer != Messages.OK) return

				def rolledFile = trackerLog.rollLog()
				showNotification("Rolled tracking log into <a href=''>${rolledFile.name}</a>") {
					ShowFilePathAction.openFile(rolledFile)
				}
			}
		}
		def clearCurrentLog = new AnAction("Clear Tracking Log") {
			@Override void actionPerformed(AnActionEvent event) {
				def userAnswer = showOkCancelDialog(event.project,
						"Clear current tracking log file?\n(This operation cannot be undone.)",
						"Activity Tracker",
						Messages.questionIcon
				)
				if (userAnswer != Messages.OK) return

				def wasCleared = trackerLog.clearLog()
				if (wasCleared) showNotification("Tracking log was cleared")
			}
		}
		def openHelp = new AnAction("Help") {
			@Override void actionPerformed(AnActionEvent event) {
				BrowserUtil.open("https://github.com/dkandalov/activity-tracker#help")
			}
		}

		registerAction("Start/Stop Activity Tracking", toggleTracking)
		registerAction("Roll Tracking Log", rollCurrentLog)
		registerAction("Clear Tracking Log", clearCurrentLog)
		// TODO register other actions

		def actionGroup = new DefaultActionGroup().with {
			add(toggleTracking)
			add(new DefaultActionGroup("Current Log", true).with {
				add(showStatistics)
				add(openLogInIde)
				add(openLogFolder)
				addSeparator()
				add(rollCurrentLog)
				add(clearCurrentLog)
				it
			})
			addSeparator()
			add(new DefaultActionGroup("Settings", true).with {
				add(toggleTrackActions)
				add(togglePollIdeState)
				add(toggleTrackKeyboard)
				add(toggleTrackMouse)
				it
			})
			add(openHelp)
			it
		}
		JBPopupFactory.instance.createActionGroupPopup(
				"Activity Tracker",
				actionGroup,
				dataContext,
				JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
				true
		)
	}

	private registerWidget(Disposable parentDisposable) {
		def presentation = new StatusBarWidget.TextPresentation() {
			@NotNull @Override String getText() {
				"Activity tracker: " + (PluginUI.this.state.isTracking ? "on" : "off")
			}

			@Override String getTooltipText() {
				"Click to open menu"
			}

			@Override Consumer<MouseEvent> getClickConsumer() {
				new Consumer<MouseEvent>() {
					@Override void consume(MouseEvent mouseEvent) {
						def dataContext = newDataContext().put(PlatformDataKeys.CONTEXT_COMPONENT.name, mouseEvent.component)
						def popup = createListPopup(dataContext)
						def dimension = popup.getContent().getPreferredSize()
						def point = new Point(0, -dimension.height as int)
						popup.show(new RelativePoint(mouseEvent.component, point))
					}
				}
			}

			@Override float getAlignment() {
				Component.CENTER_ALIGNMENT
			}

			@Deprecated @NotNull @Override String getMaxPossibleText() {
				""
			}
		}
		registerWidget(widgetId, parentDisposable, "before Position", presentation)
	}

	private static showNotification(@Nullable message, Closure<?> onLinkClick = {}) {
		invokeLaterOnEDT {
			message = Misc.asString(message)
			def title = ""
			def notificationType = INFORMATION
			def groupDisplayId = pluginId
			def notification = new Notification(groupDisplayId, title, message, notificationType, new NotificationListener() {
				@Override void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
					onLinkClick(event)
				}
			})
			ApplicationManager.application.messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
		}
	}
}
