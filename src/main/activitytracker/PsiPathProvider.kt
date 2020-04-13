package activitytracker

import activitytracker.liveplugin.currentFileIn
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

class PsiPathProvider {
    private var hasPsiClasses: Boolean? = null

    fun psiPath(project: Project, editor: Editor): String? {
        return if (hasPsiClasses(project)) {
            val elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
            val psiMethod = findPsiParent<PsiMethod>(elementAtOffset) { it is PsiMethod }
            val psiFile = findPsiParent<PsiFile>(elementAtOffset) { it is PsiFile }
            val currentElement = psiMethod ?: psiFile
            psiPathOf(currentElement)
        } else {
            null
        }
    }

    private fun hasPsiClasses(project: Project): Boolean {
        if (hasPsiClasses == null && !DumbService.getInstance(project).isDumb) {
            hasPsiClasses = isOnClasspath("com.intellij.psi.PsiMethod")
        }
        return hasPsiClasses ?: false
    }

    private fun psiPathOf(psiElement: PsiElement?): String =
        when (psiElement) {
            null, is PsiFile          -> ""
            is PsiAnonymousClass      -> {
                val parentName = psiPathOf(psiElement.parent)
                val name = "[${psiElement.baseClassType.className}]"
                if (parentName.isEmpty()) name else "$parentName::$name"
            }
            is PsiMethod, is PsiClass -> {
                val parentName = psiPathOf(psiElement.parent)
                val name = (psiElement as PsiNamedElement).name ?: ""
                if (parentName.isEmpty()) name else "$parentName::$name"
            }
            else                      -> psiPathOf(psiElement.parent)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> findPsiParent(element: PsiElement?, matches: (PsiElement) -> Boolean): T? =
        when {
            element == null  -> null
            matches(element) -> element as T?
            else             -> findPsiParent(element.parent, matches)
        }

    private fun currentPsiFileIn(project: Project): PsiFile? =
        psiFile(currentFileIn(project), project)

    private fun psiFile(file: VirtualFile?, project: Project): PsiFile? {
        file ?: return null
        return PsiManager.getInstance(project).findFile(file)
    }

    private fun isOnClasspath(className: String) =
        ActivityTracker::class.java.classLoader.getResource(className.replace(".", "/") + ".class") != null
}
