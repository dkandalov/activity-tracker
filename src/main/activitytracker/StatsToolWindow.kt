package activitytracker

import activitytracker.liveplugin.newDisposable
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
import com.intellij.openapi.wm.ToolWindowAnchor.RIGHT
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints.*
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class StatsToolWindow2 {
    companion object {
        private val toolWindowId = "Tracking Log Stats"

        fun showIn(project: Project?,
                   secondsInEditorByFile: Map<String, Int>,
                   secondsByProject: Map<String, Int>,
                   countByActionId: Map<String, Int>,
                   parentDisposable: Disposable) {
            val disposable = newDisposable(parentDisposable)
            val actionGroup = DefaultActionGroup().apply{
                add(object : AnAction(AllIcons.Actions.Cancel) {
                    override fun actionPerformed(event: AnActionEvent) {
                        Disposer.dispose(disposable)
                    }
                })
            }

            val createToolWindowPanel = { ->
                val rootPanel = JPanel().apply {
                    layout = GridBagLayout()
                    val bag = GridBag().setDefaultWeightX(1.0).setDefaultWeightY(1.0).setDefaultFill(BOTH)

                    add(JBLabel("Time spent in editor"), bag.nextLine().next().weighty(0.1).fillCellNone().anchor(CENTER))
                    val table1 = createTable(listOf("File name", "Time"), secondsInEditorByFile.mapValues{secondsToString(it.value)})
                    add(JBScrollPane(table1), bag.nextLine().next().weighty(3.0).anchor(NORTH))

                    add(JBLabel("Time spent in project"), bag.nextLine().next().weighty(0.1).fillCellNone().anchor(CENTER))
                    val table2 = createTable(listOf("Project", "Time"), secondsByProject.mapValues{secondsToString(it.value)})
                    add(JBScrollPane(table2), bag.nextLine().next().anchor(SOUTH))

                    add(JBLabel("IDE action count"), bag.nextLine().next().weighty(0.1).fillCellNone().anchor(CENTER))
                    val table3 = createTable(listOf("IDE Action", "Count"), countByActionId.mapValues{it.value.toString()})
                    add(JBScrollPane(table3), bag.nextLine().next().anchor(SOUTH))

                    add(JPanel().apply {
                        layout = GridBagLayout()
                        val message = "(Note that time spent in project includes time in IDE toolwindows and dialogs. " +
                                "Therefore, it will be greater than time spent in IDE editor.)"
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

                val toolWindowPanel = SimpleToolWindowPanel(true)
                toolWindowPanel.setContent(rootPanel)
                toolWindowPanel.setToolbar(ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true).component)
                toolWindowPanel
            }

            if (project == null) return
            val toolWindow = registerToolWindowIn(project, toolWindowId, disposable, createToolWindowPanel(), RIGHT)
            val doNothing = {} as Runnable
            toolWindow.show(doNothing)
        }

        private fun createTable(header: Collection<String>, dataMap: Map<String, String>): JBTable {
            val tableModel = object : DefaultTableModel() {
                override fun isCellEditable(row: Int, column: Int): Boolean { return false }
            }
            header.forEach {
                tableModel.addColumn(it)
            }
            dataMap.entries.forEach{
                tableModel.addRow(arrayListOf(it.key, it.value).toArray())
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

        private fun secondsToString(seconds: Int): String {
            return (seconds / 60).toString() + ":" + String.format("%02d", seconds % 60)
        }

    }
}