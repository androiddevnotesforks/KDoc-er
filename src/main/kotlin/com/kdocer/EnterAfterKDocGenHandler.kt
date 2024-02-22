package com.kdocer

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.CharArrayUtil
import com.kdocer.generator.ClassKDocGenerator
import com.kdocer.generator.NamedFunctionKDocGenerator
import com.kdocer.generator.PropertyKDocGenerator
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType

class EnterAfterKDocGenHandler : EnterHandlerDelegateAdapter() {

    override fun postProcessEnter(
        file: PsiFile,
        editor: Editor,
        dataContext: DataContext
    ): EnterHandlerDelegate.Result {

        if (file !is KtFile || !CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER) {
            return EnterHandlerDelegate.Result.Continue
        }

        val caretModel = editor.caretModel
        if (!isInKDoc(editor, caretModel.offset)) {
            return EnterHandlerDelegate.Result.Continue
        }

        val project = file.project
        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.commitAllDocuments()

        val elementAtCaret = file.findElementAt(caretModel.offset)
        val kdoc = PsiTreeUtil.getParentOfType(elementAtCaret, KDoc::class.java)
            ?: return EnterHandlerDelegate.Result.Continue
        val kdocSection = kdoc.getChildOfType<KDocSection>() ?: return EnterHandlerDelegate.Result.Continue
        if (kdocSection.text.trim() != "*") {
            return EnterHandlerDelegate.Result.Continue
        }

        ApplicationManager.getApplication().runWriteAction {
            val kDocElementFactory = KDocElementFactory(project)

            val parent = kdoc.parent
            when (parent) {
                is KtNamedFunction -> NamedFunctionKDocGenerator(project, parent)
                is KtClassOrObject -> ClassKDocGenerator(project, parent)
                is KtProperty -> PropertyKDocGenerator(project, parent)
                else -> null
            }?.generate()
                ?.let {
                    kDocElementFactory.createKDocFromText(it)
                        .let { kdoc.replace(it) }
                        .let { CodeStyleManager.getInstance(project).reformat(it) }
                }?.let {
                    it.getChildOfType<KDocSection>()?.let { kdoc ->
                        val document = editor.document
                        val kdocStartOffset = kdoc.textOffset

                        // Check if the first line of KDoc is empty (only contains *)
                        val lineStartOffset = document.getLineStartOffset(document.getLineNumber(kdocStartOffset))
                        var lineEndOffset = document.getLineEndOffset(document.getLineNumber(kdocStartOffset))
                        val lineText = document.getText(TextRange(lineStartOffset, lineEndOffset))

                        if (lineText.trimStart() == "*") {
                            // Insert a space after the asterisk
                            val spaceInsertOffset = lineStartOffset + lineText.indexOf("*") + 1
                            document.insertString(spaceInsertOffset, " ")
                            lineEndOffset++
                        }

                        // Move the caret after the inserted space
                        caretModel.moveToOffset(lineEndOffset)
                    }
                }
        }
        return EnterHandlerDelegate.Result.Continue
    }

    private fun isInKDoc(editor: Editor, offset: Int): Boolean {
        val document = editor.document
        val docChars = document.charsSequence
        var i = CharArrayUtil.lastIndexOf(docChars, "/**", offset)
        if (i >= 0) {
            i = CharArrayUtil.indexOf(docChars, "*/", i)
            return i > offset
        }
        return false
    }
}
