package actiontracker2
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Consumer
import org.jetbrains.annotations.NotNull

import java.awt.*
import java.awt.event.MouseEvent

import static liveplugin.PluginUtil.*

class PluginUI {
	private static final widgetId = "ActionTrackerII-Widget"
	private final ActionTrackerPlugin plugin
	private boolean trackingIsOn

	PluginUI(ActionTrackerPlugin plugin) {
		this.plugin = plugin
		plugin.pluginUI = this
	}

	def init(Disposable parentDisposable) {
		registerWidget(parentDisposable)
		registerPopup(parentDisposable)
		this
	}

	def update(boolean trackingIsOn) {
		if (this.trackingIsOn != trackingIsOn) {
			show("Action tracking: " + (trackingIsOn ? "ON" : "OFF"))
		}
		this.trackingIsOn = trackingIsOn
		updateWidget(widgetId)
	}

	private registerPopup(Disposable parentDisposable) {
		registerAction("ActionTrackerII-Popup", "ctrl shift alt O", "", "Action Tracker II Popup", parentDisposable) { AnActionEvent actionEvent ->
			createListPopup(actionEvent.dataContext).showCenteredInCurrentWindow(actionEvent.project)
		}
	}

	private ListPopup createListPopup(DataContext dataContext) {
		def toggleTracking = new AnAction() {
			@Override void actionPerformed(AnActionEvent event) {
				plugin.toggleTracking()
			}
			@Override void update(AnActionEvent event) {
				event.presentation.text = trackingIsOn ? "Stop tracking" : "Start tracking"
			}
		}
		def openCurrentLog = new AnAction("Open tracking log file") {
			@Override void actionPerformed(AnActionEvent event) {
				plugin.openTrackingLogFile(event.project)
			}
		}
		def openLogFolder = new AnAction("Open log in file manager") {
			@Override void actionPerformed(AnActionEvent event) {
				plugin.openTrackingLogFolder()
			}
		}
		def rollTrackingLog = new AnAction("Roll tracking log") {
			@Override void actionPerformed(AnActionEvent event) {
				// TODO trackerLog.rollFile()
			}
		}
		def statistics = new AnAction("Last 30 min stats") {
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
		def deleteAllHistory = new AnAction("Delete all history") {
			@Override void actionPerformed(AnActionEvent event) {
				// TODO
				return
				trackerLog.resetHistory()
				show("All history was deleted")
			}
		}

		def actionGroup = new DefaultActionGroup().with {
			add(toggleTracking)
			addSeparator()
			add(openLogFolder)
			add(openCurrentLog)
			add(rollTrackingLog)
			add(statistics)
			add(deleteAllHistory)
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
				"Action tracker: " + (PluginUI.this.trackingIsOn ? "on" : "off")
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
