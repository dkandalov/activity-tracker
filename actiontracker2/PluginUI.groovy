package actiontracker2

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.CheckboxAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import org.jetbrains.annotations.NotNull

import java.awt.*
import java.awt.event.MouseEvent

import static actiontracker2.ActionTrackerPlugin.pluginId
import static liveplugin.PluginUtil.*

class PluginUI {
	private static final widgetId = "${pluginId}-Widget"
	private final ActionTrackerPlugin plugin
	private ActionTrackerPlugin.State state

	PluginUI(ActionTrackerPlugin plugin) {
		this.plugin = plugin
	}

	def init(Disposable parentDisposable) {
		plugin.pluginUI = this
		registerWidget(parentDisposable)
		registerPopup(parentDisposable)
		this
	}

	def update(ActionTrackerPlugin.State state) {
		if (this.state?.isTracking != state.isTracking) {
			show("Action tracking: " + (state.isTracking ? "ON" : "OFF"))
		}
		this.state = state
		updateWidget(widgetId)
	}

	private registerPopup(Disposable parentDisposable) {
		registerAction("${pluginId}-Popup", "ctrl shift alt O", "", "Action Tracker II Popup", parentDisposable) { AnActionEvent actionEvent ->
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
		def openLogInIde = new AnAction("Open Log in IDE") {
			@Override void actionPerformed(AnActionEvent event) {
				plugin.openTrackingLogFile(event.project)
			}
		}
		def openLogFolder = new AnAction("Open Log in File Manager") {
			@Override void actionPerformed(AnActionEvent event) {
				plugin.openTrackingLogFolder()
			}
		}
		def statistics = new AnAction("Last 30 min Stats") {
			@Override void actionPerformed(AnActionEvent event) {
				// TODO
				return
				def history = trackerLog.readHistory(minus30minutesFrom(now()), now())
				if (history.empty) show("There is no recorded history to analyze")
				else {
					show(EventsAnalyzer.asString(EventsAnalyzer.aggregateByFile(history)))
					show(EventsAnalyzer.asString(EventsAnalyzer.aggregateByElement(history)))
				}
			}
		}
		def rollCurrentLog = new AnAction("Roll Current Log") {
			@Override void actionPerformed(AnActionEvent event) {
				// TODO trackerLog.rollFile()
			}
		}
		def clearCurrentLog = new AnAction("Clear Current Log") {
			@Override void actionPerformed(AnActionEvent event) {
				// TODO
				return
				trackerLog.resetHistory()
				show("All history was deleted")
			}
		}
		def openHelp = new AnAction("Help (GitHub)") {
			@Override void actionPerformed(AnActionEvent event) {
				BrowserUtil.open("https://github.com/dkandalov/action-tracker-2")
			}
		}

		def actionGroup = new DefaultActionGroup().with {
			add(toggleTracking)
			add(statistics)
			addSeparator()
			add(new DefaultActionGroup("Current Log", true).with {
				add(openLogInIde)
				add(openLogFolder)
				add(rollCurrentLog)
				add(clearCurrentLog)
				it
			})
			add(new DefaultActionGroup("Settings", true).with {
				add(toggleTrackActions)
				add(togglePollIdeState)
				add(toggleTrackKeyboard)
				add(toggleTrackMouse)
				it
			})
			addSeparator()
			add(openHelp)
			it
		}
		JBPopupFactory.instance.createActionGroupPopup(
				"Action Tracker II",
				actionGroup,
				dataContext,
				JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
				true
		)
	}

	private registerWidget(Disposable parentDisposable) {
		def presentation = new StatusBarWidget.TextPresentation() {
			@NotNull @Override String getText() {
				"Action tracker: " + (PluginUI.this.state.isTracking ? "on" : "off")
			}

			@Override String getTooltipText() {
				"Click to start/stop tracking actions."
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
}
