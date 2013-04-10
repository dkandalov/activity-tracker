import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod

import javax.swing.SwingUtilities
import java.text.SimpleDateFormat

import static intellijeval.PluginUtil.*


registerAction("WhatIWorkOnStats", "ctrl shift alt O") { AnActionEvent actionEvent ->
	def isTrackingVarName = "WhatIWorkOnStats.isTracking"

	JBPopupFactory.instance.createActionGroupPopup(
			"Current file statistics",
			new DefaultActionGroup().with{
				add(new AnAction() {
					@Override void actionPerformed(AnActionEvent event) {
						def isTracking = changeGlobalVar(isTrackingVarName, true){ !it }
						show("Tracking current file: " + (isTracking ? "ON" : "OFF"))

						if (isTracking) {
							new Thread({
								while (getGlobalVar(isTrackingVarName)) {
									SwingUtilities.invokeLater{
										log(createLogEvent(new Date()).toCsv())
									}
									Thread.sleep(1000)
								}
							} as Runnable).start()
						}
					}

					@Override void update(AnActionEvent event) {
						def isTracking = getGlobalVar(isTrackingVarName, false)
						event.presentation.text = (isTracking ? "Stop tracking current file" : "Start tracking current file")
					}
				})
				add(new AnAction("Analyze history") {
					@Override void actionPerformed(AnActionEvent event) {
						show("Analyze history") // TODO
					}
				})
				it
			},
			actionEvent.dataContext,
			JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
			true
	).showCenteredInCurrentWindow(actionEvent.project)
}
show("started")

LogEvent createLogEvent(Date now) {
	IdeFrame activeFrame = WindowManager.instance.allProjectFrames.find{it.active}
	// this tracks project frame as inactive during refactoring
	// (e.g. when "Rename class" frame is active)
	if (activeFrame == null) return new LogEvent(now, "", "", "")
	def project = activeFrame.project

	def editor = currentEditorIn(project)
	def elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
	PsiMethod psiMethod = findParent(elementAtOffset, {it instanceof PsiMethod})
	PsiFile psiFile = findParent(elementAtOffset, {it instanceof PsiFile})
	def currentElement = (psiMethod == null ? psiFile : psiMethod)

	// this doesn't take into account time spent in toolwindows
	// (when the same frame is active but editor doesn't have focus)
	def file = currentFileIn(project)
	def filePath = (file == null ? "" : file.path)
	new LogEvent(now, project.name, filePath, fullNameOf(currentElement))
}

private static String fullNameOf(PsiElement psiElement) {
	if (psiElement == null) "null"
	else if (psiElement instanceof PsiFile) ""
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

private def <T > T findParent(PsiElement element, Closure matches) {
	if (element == null) null
	else if (matches(element)) element as T
	else findParent(element.parent, matches)
}

@groovy.transform.Immutable
final class LogEvent {
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("kk:mm:ss dd/MM/yyyy")
	Date time
	String projectName
	String file
	String element

	String toCsv() {
		"${TIME_FORMAT.format(time)},$projectName,$file,$element"
	}
}