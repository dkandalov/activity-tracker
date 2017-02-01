package activitytracker

import activitytracker.liveplugin.newDisposable
import com.intellij.icons.AllIcons
import com.intellij.icons.AllIcons.Actions.*
import com.intellij.ide.ClipboardSynchronizer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.*
import java.awt.GridBagConstraints.*
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class StatsToolWindow {
    companion object {
        private val toolWindowId = "Tracking Log Stats"

        // TODO change move dataFile into stats; make stats a function so that refresh button actually works
        fun showIn(project: Project, stats: Stats, dataFile: String, parentDisposable: Disposable) {
            val createRootComponent = { ->
                JPanel().apply {
                    layout = GridBagLayout()
                    val bag = GridBag().setDefaultWeightX(1.0).setDefaultWeightY(1.0).setDefaultFill(BOTH)

                    add(JTabbedPane().apply {
                        val fillBoth = GridBag().setDefaultWeightX(1.0).setDefaultWeightY(1.0).setDefaultFill(BOTH).next()

                        addTab("Time spent in editor", JPanel().apply {
                            layout = GridBagLayout()
                            val table = createTable(listOf("File name", "Time"), stats.secondsInEditorByFile.map{secondsToString(it)})
                            add(JBScrollPane(table), fillBoth)
                        })
                        addTab("Time spent in project", JPanel().apply {
                            layout = GridBagLayout()
                            val table = createTable(listOf("Project", "Time"), stats.secondsByProject.map{secondsToString(it)})
                            add(JBScrollPane(table), fillBoth)
                        })
                        addTab("IDE action count", JPanel().apply {
                            layout = GridBagLayout()
                            val table = createTable(listOf("IDE Action", "Count"), stats.countByActionId.map { Pair(it.first, it.second.toString()) })
                            add(JBScrollPane(table), fillBoth)
                        })
                    }, bag.nextLine().next().weighty(4.0).anchor(NORTH))

                    add(JPanel().apply {
                        layout = GridBagLayout()
                        val message =
                                "Results are based on data from '$dataFile'.\n\n" +
                                "Note that time spent in project includes time in IDE toolwindows and dialogs. " +
                                "Therefore, it will be greater than time spent in IDE editor."
                        add(JTextArea(message).apply{
                            isEditable = false
                            lineWrap = true
                            wrapStyleWord = true
                            background = UIUtil.getLabelBackground()
                            font = UIUtil.getLabelFont()
                            UIUtil.applyStyle(UIUtil.ComponentStyle.REGULAR, this)
                        }, GridBag().setDefaultWeightX(1.0).setDefaultWeightY(1.0).nextLine().next().fillCellHorizontally().anchor(NORTH))
                    }, bag.nextLine().next().weighty(0.5).anchor(SOUTH))
                }
            }
            val toolWindowPanel = SimpleToolWindowPanel(true)
            var rootComponent = createRootComponent()
            toolWindowPanel.setContent(rootComponent)

            val disposable = newDisposable(parentDisposable)
            val actionGroup = DefaultActionGroup().apply{
                add(object : AnAction(Cancel) {
                    override fun actionPerformed(event: AnActionEvent) {
                        Disposer.dispose(disposable)
                    }
                })
                add(object : AnAction(Refresh) {
                    override fun actionPerformed(e: AnActionEvent?) {
                        toolWindowPanel.remove(rootComponent)
                        rootComponent = createRootComponent()
                        toolWindowPanel.setContent(rootComponent)
                    }
                })
            }

            val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true)
            toolWindowPanel.setToolbar(actionToolbar.component)

            val toolWindow = registerToolWindowIn(project, toolWindowId, disposable, toolWindowPanel, RIGHT)
            val doNothing = null
            toolWindow.show(doNothing)
        }

        private fun createTable(header: Collection<String>, data: List<Pair<String, String>>): JBTable {
            val tableModel = object : DefaultTableModel() {
                override fun isCellEditable(row: Int, column: Int): Boolean { return false }
            }
            header.forEach {
                tableModel.addColumn(it)
            }
            data.forEach {
                tableModel.addRow(arrayListOf(it.first, it.second).toArray())
            }
            val table = JBTable(tableModel).apply{
                isStriped = true
                setShowGrid(false)
            }
            registerCopyToClipboardShortCut(table, tableModel)
            return table
        }

        private fun registerCopyToClipboardShortCut(table: JTable, tableModel: DefaultTableModel) {
            val copyKeyStroke = KeymapUtil.getKeyStroke(ActionManager.getInstance().getAction(IdeActions.ACTION_COPY).shortcutSet)
            table.registerKeyboardAction(object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent) {
                    val selectedCells = table.selectedRows.map { row ->
                        0.until(tableModel.columnCount).map { column ->
                            tableModel.getValueAt(row, column).toString()
                        }
                    }
                    val content = StringSelection(selectedCells.map{ it.joinToString(",") }.joinToString("\n"))
                    ClipboardSynchronizer.getInstance().setContent(content, content)
                }
            }, "Copy", copyKeyStroke, JComponent.WHEN_FOCUSED)
        }

        private fun registerToolWindowIn(project: Project, toolWindowId: String, disposable: Disposable,
                                         component: JComponent, location: ToolWindowAnchor = RIGHT): ToolWindow {
            newDisposable(disposable) {
                ToolWindowManager.getInstance(project).unregisterToolWindow(toolWindowId)
            }

            val manager = ToolWindowManager.getInstance(project)
            if (manager.getToolWindow(toolWindowId) != null) {
                manager.unregisterToolWindow(toolWindowId)
            }

            val toolWindow = manager.registerToolWindow(toolWindowId, false, location)
            val content = ContentFactory.SERVICE.getInstance().createContent(component, "", false)
            toolWindow.contentManager.addContent(content)
            toolWindow.icon = AllIcons.General.MessageHistory
            return toolWindow
        }

        private fun secondsToString(pair: Pair<String, Int>): Pair<String, String> {
            val seconds = pair.second
            val formatterSeconds = (seconds / 60).toString() + ":" + String.format("%02d", seconds % 60)
            return Pair(pair.first, formatterSeconds)
        }

    }
}