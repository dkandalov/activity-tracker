package activitytracker

import activitytracker.liveplugin.newDisposable
import activitytracker.liveplugin.registerProjectListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerTopics

interface CompilationTracker {
    fun startActionListener(
        parentDisposable: Disposable,
        callback: (eventType: TrackerEvent.Type, originalEventData: String) -> Unit
    )

    companion object {
        val instance = object: CompilationTracker {
            override fun startActionListener(
                parentDisposable: Disposable,
                callback: (eventType: TrackerEvent.Type, originalEventData: String) -> Unit
            ) {
                if (haveCompilation()) {
                    registerCompilationListener(parentDisposable, object: CompilationStatusListener {
                        override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
                            callback(TrackerEvent.Type.CompilationFinished, errors.toString())
                        }
                    })
                }
            }

            private fun registerCompilationListener(disposable: Disposable, listener: CompilationStatusListener) {
                registerProjectListener(disposable) { project ->
                    project.messageBus
                        .connect(newDisposable(disposable, project))
                        .subscribe(CompilerTopics.COMPILATION_STATUS, listener)
                }
            }
        }


        private fun haveCompilation() = isOnClasspath("com.intellij.openapi.compiler.CompilationStatusListener")

        private fun isOnClasspath(className: String) =
            ActivityTracker::class.java.classLoader.getResource(className.replace(".", "/") + ".class") != null
    }
}