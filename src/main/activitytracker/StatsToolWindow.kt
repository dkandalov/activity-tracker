package activitytracker

import activitytracker.EventAnalyzer.Result.*
import activitytracker.liveplugin.createChild
import activitytracker.liveplugin.invokeLaterOnEDT
import activitytracker.liveplugin.showNotification
import activitytracker.liveplugin.whenDisposed
import com.intellij.icons.AllIcons
import com.intellij.icons.AllIcons.Actions.Cancel
import com.intellij.icons.AllIcons.Actions.Refresh
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
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints.*
import java.awt.GridBagLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.util.function.Supplier
import javax.swing.*
import javax.swing.table.DefaultTableModel

object StatsToolWindow {
    private const val toolWindowId = "Tracking Log Stats"

    fun showIn(project: Project, stats: Stats, eventAnalyzer: EventAnalyzer, parentDisposable: Disposable) {
        val toolWindowPanel = SimpleToolWindowPanel(true)
        var rootComponent = createRootComponent(stats)
        toolWindowPanel.setContent(rootComponent)

        val disposable = parentDisposable.createChild()
        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction(Supplier { "Rerun activity log analysis" }, Refresh) {
                override fun actionPerformed(e: AnActionEvent) =
                    eventAnalyzer.analyze(whenDone = { result ->
                        invokeLaterOnEDT {
                            when (result) {
                                is Ok -> {
                                    toolWindowPanel.remove(rootComponent)
                                    rootComponent = createRootComponent(result.stats)
                                    toolWindowPanel.setContent(rootComponent)

                                    if (result.errors.isNotEmpty()) {
                                        showNotification("There were ${result.errors.size} errors parsing log file, see IDE log for details")
                                    }
                                }
                                is AlreadyRunning -> showNotification("Analysis is already running")
                                is DataIsTooLarge -> showNotification("Activity log is too large to process in IDE")
                            }
                        }
                    })
            })
            add(object : AnAction(Supplier { "Close tool window" }, Cancel) {
                override fun actionPerformed(event: AnActionEvent) =
                    Disposer.dispose(disposable)
            })
        }

        val actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actionGroup, true)
        actionToolbar.targetComponent = actionToolbar.component
        toolWindowPanel.toolbar = actionToolbar.component

        registerToolWindowIn(project, toolWindowId, disposable, toolWindowPanel, RIGHT).show()
    }

    private fun createRootComponent(stats: Stats): JComponent =
        JPanel().apply {
            layout = GridBagLayout()
            val bag = GridBag().setDefaultWeightX(1.0).setDefaultWeightY(1.0).setDefaultFill(BOTH)

            add(JTabbedPane().apply {
                val fillBoth = GridBag().setDefaultWeightX(1.0).setDefaultWeightY(1.0).setDefaultFill(BOTH).next()

                addTab("Time spent in editor", JPanel().apply {
                    layout = GridBagLayout()
                    val table = createTable(listOf("File name", "Time"), stats.secondsInEditorByFile.map { secondsToString(it) })
                    add(JBScrollPane(table), fillBoth)
                })
                addTab("Time spent in project", JPanel().apply {
                    layout = GridBagLayout()
                    val table = createTable(listOf("Project", "Time"), stats.secondsByProject.map { secondsToString(it) })
                    add(JBScrollPane(table), fillBoth)
                })
                addTab("Time spent on tasks", JPanel().apply {
                    layout = GridBagLayout()
                    val table = createTable(listOf("Task", "Time"), stats.secondsByTask.map { secondsToString(it) })
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
                val message = "The stats are based on data from '${stats.dataFile}'.\n\n" +
                    "To see the time spent in the editor/project or on tasks, enable Activity Tracker -> Settings -> Poll IDE State.\n\n" +
                    "The time spent on a project includes the time in IDE tool windows and dialogs and, therefore, " +
                    "it will be greater than the time spent in the IDE editor."
                val panelBackground = background
                add(JTextArea(message).apply {
                    isEditable = false
                    lineWrap = true
                    wrapStyleWord = true
                    background = panelBackground
                    font = StartupUiUtil.labelFont
                    UIUtil.applyStyle(UIUtil.ComponentStyle.REGULAR, this)
                }, GridBag().setDefaultWeightX(1.0).setDefaultWeightY(1.0).nextLine().next().fillCellHorizontally().anchor(NORTH))
            }, bag.nextLine().next().weighty(0.5).anchor(SOUTH))
        }

    private fun createTable(header: Collection<String>, data: List<Pair<String, String>>): JBTable {
        val tableModel = object : DefaultTableModel() {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        header.forEach {
            tableModel.addColumn(it)
        }
        data.forEach {
            tableModel.addRow(arrayListOf(it.first, it.second).toArray())
        }
        val table = JBTable(tableModel).apply {
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
                val content = StringSelection(selectedCells.map { it.joinToString(",") }.joinToString("\n"))
                ClipboardSynchronizer.getInstance().setContent(content, content)
            }
        }, "Copy", copyKeyStroke, JComponent.WHEN_FOCUSED)
    }

    private fun registerToolWindowIn(
        project: Project,
        toolWindowId: String,
        disposable: Disposable,
        component: JComponent,
        location: ToolWindowAnchor = RIGHT
    ): ToolWindow {
        disposable.whenDisposed {
            ToolWindowManager.getInstance(project).unregisterToolWindow(toolWindowId)
        }

        val manager = ToolWindowManager.getInstance(project)
        if (manager.getToolWindow(toolWindowId) != null) {
            manager.unregisterToolWindow(toolWindowId)
        }

        val toolWindow = manager.registerToolWindow(toolWindowId, false, location)
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(component, "", false))
        toolWindow.setIcon(AllIcons.Vcs.History)
        return toolWindow
    }

    private fun secondsToString(pair: Pair<String, Int>): Pair<String, String> {
        val seconds = pair.second
        val formatterSeconds = (seconds / 60).toString() + ":" + String.format("%02d", seconds % 60)
        return Pair(pair.first, formatterSeconds)
    }
}