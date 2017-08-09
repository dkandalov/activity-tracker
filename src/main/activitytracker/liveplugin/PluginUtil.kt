package activitytracker.liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.WindowManagerListener

fun <T> invokeOnEDT(callback: () -> T): T? {
    var result: T? = null
    ApplicationManager.getApplication()
        .invokeAndWait({ result = callback() }, ModalityState.any())
    return result
}

fun invokeLaterOnEDT(callback: () -> Unit) {
    ApplicationManager.getApplication().invokeLater { callback() }
}

fun registerWindowManagerListener(disposable: Disposable, onFrameCreated: (IdeFrame) -> Unit) {
    val windowManager = WindowManager.getInstance()
    val listener = object: WindowManagerListener {
        override fun frameCreated(ideFrame: IdeFrame) { onFrameCreated(ideFrame) }
        override fun beforeFrameReleased(ideFrame: IdeFrame) {}
    }
    windowManager.addListener(listener)
    newDisposable(disposable) {
        windowManager.removeListener(listener)
    }
}
