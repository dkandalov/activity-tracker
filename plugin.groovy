import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerAdapter
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import com.intellij.util.Consumer
import groovy.time.TimeCategory
import org.jetbrains.annotations.NotNull

import javax.swing.*
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference

import static EventsAnalyzer.aggregateByElement
import static EventsAnalyzer.aggregateByFile
import static liveplugin.PluginUtil.*

def trackerLog = new TrackerLog(pluginPath + "/stats")
def isTracking = "ActionTrackerII.isTracking"
def trackingDisposable = "ActionTrackerII.disposable"

if (isIdeStartup) {
	setGlobalVar(isTracking, true)
	startTracking(trackerLog, pluginDisposable)
	show("Action tracking: ON")
}
if (!isIdeStartup) show("Reloaded ActionTracker II")

def registerStatusBarWidget(Project project, StatusBarWidget widget, String anchor = "", Disposable disposable = null) {
	invokeOnEDT {
		def statusBar = WindowManager.instance.getStatusBar(project)
		if (statusBar.getWidget(widget.ID()) != null) {
			statusBar.removeWidget(widget.ID())
		}
		if (disposable != null) {
			statusBar.addWidget(widget, anchor, disposable)
		} else {
			statusBar.addWidget(widget, anchor)
		}
		statusBar.updateWidget(widget.ID())
	}
}

def widget = new StatusBarWidget() {
	@Override String ID() {
		"ActionTrackerIIWidget"
	}

	@Override StatusBarWidget.WidgetPresentation getPresentation(@NotNull StatusBarWidget.PlatformType platformType) {
		new StatusBarWidget.TextPresentation() {
			@NotNull @Override String getText() {
				def trackingIsOn = getGlobalVar(isTracking, false)
				"Action tracker: " + (trackingIsOn ? "on" : "off")
			}

			@NotNull @Override String getMaxPossibleText() {
				"Action tracker: ___"
			}

			@Override String getTooltipText() {
				"Click to start/stop tracking actions."
			}

			@Override Consumer<MouseEvent> getClickConsumer() {
				new Consumer<MouseEvent>() {
					@Override void consume(MouseEvent mouseEvent) {
						// TODO same as code in action below
						def trackingIsOn = changeGlobalVar(isTracking, false) { !it }
						if (trackingIsOn) {
							def disposable = new Disposable() {
								@Override void dispose() {
									setGlobalVar(isTracking, false)
								}
							}
							Disposer.register(pluginDisposable, disposable)
							setGlobalVar(trackingDisposable, disposable)

							startTracking(trackerLog, disposable)
						} else {
							def disposable = getGlobalVar(trackingDisposable) as Disposable
							if (disposable != null) {
								Disposer.dispose(disposable)
							}
						}
						ProjectManager.instance.openProjects.each {
							WindowManager.instance.getStatusBar(it).updateWidget(ID())
						}
					}
				}
			}

			@Override float getAlignment() {
				Component.CENTER_ALIGNMENT
			}
		}
	}

	@Override void install(@NotNull StatusBar statusBar) {}

	@Override void dispose() {}
}
ProjectManager.instance.openProjects.each {
	registerStatusBarWidget(it, widget, "before Position", pluginDisposable)
}
ProjectManager.instance.addProjectManagerListener(new ProjectManagerAdapter() {
	@Override void projectOpened(Project project) {
		registerStatusBarWidget(project, widget, "before Position", pluginDisposable)
	}
})


registerAction("ActionTrackerIIPopup", "ctrl shift alt O", "", "Action Tracker II Popup") { AnActionEvent actionEvent ->
    def trackCurrentFile = new AnAction() {
        @Override void actionPerformed(AnActionEvent event) {
            def trackingIsOn = changeGlobalVar(isTracking, false) { !it }
            if (trackingIsOn) {
	            def disposable = new Disposable() {
		            @Override void dispose() {
			            setGlobalVar(isTracking, false)
		            }
	            }
	            Disposer.register(pluginDisposable, disposable)
	            setGlobalVar(trackingDisposable, disposable)

	            startTracking(trackerLog, disposable)
            } else {
	            def disposable = getGlobalVar(trackingDisposable) as Disposable
	            if (disposable != null) {
		            Disposer.dispose(disposable)
	            }
            }
	        ProjectManager.instance.openProjects.each {
		        WindowManager.instance.getStatusBar(it).updateWidget(widget.ID())
	        }
            show("Action tracking: " + (trackingIsOn ? "ON" : "OFF"))
        }

        @Override void update(AnActionEvent event) {
	        event.presentation.text = (getGlobalVar(isTracking, false) ? "Stop tracking" : "Start tracking")
        }
    }
	def rollTrackingLog = new AnAction("Roll tracking log") {
		@Override void actionPerformed(AnActionEvent event) {
			trackerLog.rollFile()
		}
	}
    def statistics = new AnAction("Last 30 min stats") {
        @Override void actionPerformed(AnActionEvent event) {
            def history = trackerLog.readHistory(minus30minutesFrom(now()), now())
	        if (history.empty) show("There is no recorded history to analyze")
	        else {
		        show(EventsAnalyzer.asString(aggregateByFile(history)))
		        show(EventsAnalyzer.asString(aggregateByElement(history)))
	        }
        }
    }
    def deleteAllHistory = new AnAction("Delete all history") {
        @Override void actionPerformed(AnActionEvent event) {
            trackerLog.resetHistory()
            show("All history was deleted")
        }
    }

    JBPopupFactory.instance.createActionGroupPopup(
			"Action Tracker II",
			new DefaultActionGroup().with {
                add(trackCurrentFile)
				addSeparator()
                add(rollTrackingLog)
                add(statistics)
                add(deleteAllHistory)
				it
			},
			actionEvent.dataContext,
			JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
			true
	).showCenteredInCurrentWindow(actionEvent.project)
}

void startTracking(TrackerLog trackerLog, Disposable parentDisposable) {
	ActionManager.instance.addAnActionListener(new AnActionListener() {
		@Override void afterActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {
			def actionId = ActionManager.instance.getId(anAction)
			trackerLog.append(createLogEvent(actionId))
		}

		@Override void beforeActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {}
		@Override void beforeEditorTyping(char c, DataContext dataContext) {}
	}, parentDisposable)

	IdeEventQueue.instance.addPostprocessor(new IdeEventQueue.EventDispatcher() {
		@Override boolean dispatch(AWTEvent awtEvent) {
			if (awtEvent instanceof MouseEvent && awtEvent.getID() == MouseEvent.MOUSE_CLICKED) {
				trackerLog.append(createLogEvent("MouseEvent:" + awtEvent.button + ":" + awtEvent.modifiers))

//			} else if (awtEvent instanceof MouseWheelEvent && awtEvent.getID() == MouseEvent.MOUSE_WHEEL) {
//				trackerLog.append(createLogEvent("MouseWheelEvent:" + awtEvent.scrollAmount + ":" + awtEvent.wheelRotation))

			} else if (awtEvent instanceof KeyEvent && awtEvent.getID() == KeyEvent.KEY_PRESSED) {
				//show("KeyEvent:" + (awtEvent.keyChar as int) + ":" + awtEvent.modifiers)
				trackerLog.append(createLogEvent("KeyEvent:" + (awtEvent.keyChar as int) + ":" + awtEvent.modifiers))
			}
//			show(awtEvent)
			false
		}
	}, parentDisposable)

	// consider using com.intellij.openapi.actionSystem.impl.ActionManagerImpl#addTimerListener
	def isDisposed = new AtomicReference<Boolean>(false)
	new Thread({
		while (!isDisposed.get()) {
			AtomicReference logEvent = new AtomicReference<TrackerEvent>()
			SwingUtilities.invokeAndWait {
				logEvent.set(createLogEvent())
			}
			if (logEvent != null) trackerLog.append(logEvent.get())
			Thread.sleep(1000)
		}
	} as Runnable).start()
	Disposer.register(parentDisposable, new Disposable() {
		@Override void dispose() {
			isDisposed.set(true)
		}
	})
}

Date now() { new Date() }
Date minus30minutesFrom(Date time) {
	use(TimeCategory) { time - 30.minutes }
}

TrackerEvent createLogEvent(String actionId = "", Date time = now()) {
	IdeFrame activeFrame = WindowManager.instance.allProjectFrames.find{it.active}
	// this tracks project frame as inactive during refactoring
	// (e.g. when "Rename class" frame is active)
	def project = activeFrame?.project
	if (project == null) return new TrackerEvent(time, "", "", "", actionId)
	def editor = currentEditorIn(project)
	if (editor == null) return new TrackerEvent(time, project.name, "", "", actionId)

	def elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
	PsiMethod psiMethod = findParent(elementAtOffset, {it instanceof PsiMethod})
	PsiFile psiFile = findParent(elementAtOffset, {it instanceof PsiFile})
	def currentElement = (psiMethod == null ? psiFile : psiMethod)

	// this doesn't take into account time spent in toolwindows
	// (when the same frame is active but editor doesn't have focus)
	def file = currentFileIn(project)
	// don't try to shorten file name by excluding project because different projects might have same files
	def filePath = (file == null ? "" : file.path)
	new TrackerEvent(time, project.name, filePath, fullNameOf(currentElement), actionId)
}

private static String fullNameOf(PsiElement psiElement) {
	if (psiElement == null || psiElement instanceof PsiFile) ""
	else if (psiElement in PsiAnonymousClass) {
		def parentName = fullNameOf(psiElement.parent)
		def name = "[" + psiElement.baseClassType.className + "]"
		parentName.empty ? name : (parentName + "::" + name)
	} else if (psiElement instanceof PsiMethod || psiElement instanceof PsiClass) {
		def parentName = fullNameOf(psiElement.parent)
		parentName.empty ? psiElement.name : (parentName + "::" + psiElement.name)
	} else {
		fullNameOf(psiElement.parent)
	}
}

private def <T> T findParent(PsiElement element, Closure matches) {
	if (element == null) null
	else if (matches(element)) element as T
	else findParent(element.parent, matches)
}

