package activitytracker.liveplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState

fun <T> invokeOnEDT(callback: () -> T): T? {
    var result: T? = null
    ApplicationManager.getApplication().invokeAndWait({
        result = callback()
    }, ModalityState.any())
    return result
}

fun invokeLaterOnEDT(callback: () -> Unit) {
    ApplicationManager.getApplication().invokeLater{ callback() }
}

