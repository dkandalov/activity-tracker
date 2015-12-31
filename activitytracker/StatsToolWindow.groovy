package activitytracker
import com.intellij.icons.AllIcons
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull

import javax.swing.*
import javax.swing.table.DefaultTableModel
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent

import static com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import static liveplugin.implementation.Misc.newDisposable

class StatsToolWindow {
	private static final toolWindowId = "Tracking Log Stats"

	static showIn(Project project, Map timeInEditorByFile, Disposable parentDisposable) {

		def disposable = newDisposable([parentDisposable])
		def actionGroup = new DefaultActionGroup().with{
			add(new AnAction(AllIcons.Actions.Cancel) {
				@Override void actionPerformed(AnActionEvent event) {
					Disposer.dispose(disposable)
				}
			})
			it
		}

		def createToolWindowPanel = {
			JPanel rootPanel = new JPanel().with{
				layout = new GridBagLayout()
				GridBag bag = new GridBag().setDefaultWeightX(1).setDefaultWeightY(1).setDefaultFill(GridBagConstraints.BOTH)

				JBTable table = createTable(timeInEditorByFile)
				add(new JBScrollPane(table), bag.nextLine().next().anchor(GridBagConstraints.NORTH))

				add(new JPanel().with {
					layout = new GridBagLayout()
					def message = "(The above data only includes time spent in IDE editor, " +
							"i.e. time spent in IDE toolwindows, dialogs or other applications is not counted.)"
					add(new JTextArea(message).with{
						editable = false
						lineWrap = true
						wrapStyleWord = true
						background = UIUtil.labelBackground
                        font = UIUtil.labelFont
						UIUtil.applyStyle(UIUtil.ComponentStyle.REGULAR, it)
						it
					}, new GridBag().setDefaultWeightX(1).setDefaultWeightY(1).nextLine().next().fillCellHorizontally().anchor(GridBagConstraints.NORTH))
					it
				}, bag.nextLine().next().anchor(GridBagConstraints.SOUTH))

				it
			}

			def toolWindowPanel = new SimpleToolWindowPanel(true)
			toolWindowPanel.content = rootPanel
			toolWindowPanel.toolbar = ActionManager.instance.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true).component
			toolWindowPanel
		}

		def toolWindow = registerToolWindowIn(project, toolWindowId, disposable, createToolWindowPanel(), RIGHT)
		def doNothing = {} as Runnable
		toolWindow.show(doNothing)
	}

	private static JBTable createTable(timeInEditorByFile) {
		def totalTime = timeInEditorByFile.entrySet().sum(0){ it.value }
		def tableModel = new DefaultTableModel() {
			@Override boolean isCellEditable(int row, int column) { false }
		}
		tableModel.addColumn("File name")
		tableModel.addColumn("Time spent")
		timeInEditorByFile.entrySet().each{
			tableModel.addRow([it.key, secondsToString(it.value)].toArray())
		}
		tableModel.addRow(["Total", secondsToString(totalTime)].toArray())
		def table = new JBTable(tableModel).with{
			striped = true
			showGrid = false
			it
		}
		registerCopyToClipboardShortCut(table, tableModel)
		table
	}

	private static String secondsToString(Integer seconds) {
		seconds.intdiv(60) + ":" + String.format("%02d", seconds % 60)
	}

	private static registerCopyToClipboardShortCut(JTable table, DefaultTableModel tableModel) {
		KeyStroke copyKeyStroke = KeymapUtil.getKeyStroke(ActionManager.instance.getAction(IdeActions.ACTION_COPY).shortcutSet)
		table.registerKeyboardAction(new AbstractAction() {
			@Override void actionPerformed(ActionEvent event) {
				def selectedCells = table.selectedRows.collect{ row ->
					(0..<tableModel.columnCount).collect{ column ->
						tableModel.getValueAt(row, column).toString() }
				}
				def content = new StringSelection(selectedCells.collect{ it.join(",") }.join("\n"))
				ClipboardSynchronizer.instance.setContent(content, content)
			}
		}, "Copy", copyKeyStroke, JComponent.WHEN_FOCUSED)
	}

	private static ToolWindow registerToolWindowIn(@NotNull Project project, String toolWindowId, Disposable disposable,
	                                               JComponent component, ToolWindowAnchor location = RIGHT) {
		newDisposable(disposable) {
			ToolWindowManager.getInstance(project).unregisterToolWindow(toolWindowId)
		}

		def manager = ToolWindowManager.getInstance(project)
		if (manager.getToolWindow(toolWindowId) != null) {
			manager.unregisterToolWindow(toolWindowId)
		}

		def toolWindow = manager.registerToolWindow(toolWindowId, false, location)
		def content = ContentFactory.SERVICE.instance.createContent(component, "", false)
		toolWindow.contentManager.addContent(content)
		toolWindow.setIcon(AllIcons.General.MessageHistory)
		toolWindow
	}

}
