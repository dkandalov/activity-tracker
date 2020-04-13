package activitytracker

import activitytracker.liveplugin.currentVirtualFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*

interface PsiPathProvider {
    fun psiPath(project: Project, editor: Editor): String? = null

    companion object {
        var instance: PsiPathProvider = object: PsiPathProvider {}
    }
}

class InitJavaPsiPathProvider {
    init {
        PsiPathProvider.instance = JavaPsiPathProvider()
    }
}

private class JavaPsiPathProvider: PsiPathProvider {
    override fun psiPath(project: Project, editor: Editor): String? {
        val elementAtOffset = project.currentPsiFile()?.findElementAt(editor.caretModel.offset)
        val psiMethod = findPsiParent<PsiMethod>(elementAtOffset) { it is PsiMethod }
        val psiFile = findPsiParent<PsiFile>(elementAtOffset) { it is PsiFile }
        return psiPathOf(psiMethod ?: psiFile)
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

    private fun Project.currentPsiFile(): PsiFile? =
        currentVirtualFile()?.toPsiFile(this)

    private fun VirtualFile.toPsiFile(project: Project): PsiFile? =
        PsiManager.getInstance(project).findFile(this)
}
