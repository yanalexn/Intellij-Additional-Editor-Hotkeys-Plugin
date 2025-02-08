import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import liveplugin.registerAction
import java.util.*

registerAction(
    id = "Separate line reformatter",
    keyStroke = "ctrl shift alt ENTER",
    action = SeparateLineReformatter()
)

class SeparateLineReformatter : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val editor = CommonDataKeys.EDITOR.getData(event.dataContext)
        if (project == null || editor == null) return

        val document = editor.document
        val cursorOffset = editor.caretModel.offset
        val range = findEnclosingBrackets(document.text, cursorOffset) ?: return
        val text = document.getText(range)
        val modifiedText = if (text.contains("\n")) {
            joinParametersIntoSingleLine(text)
        } else {
            replaceHighLevelCommas(text)
        }

        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(range.startOffset, range.endOffset, modifiedText)
        }
        performReformatting(event, document)

    }

    private fun findEnclosingBrackets(text: String, cursorOffset: Int): TextRange? {
        val openBrackets = setOf(
            '(',
            '[',
//            '{'
        )
        val closeBrackets = mapOf(
            '(' to ')',
            '[' to ']',
//            '{' to '}'
        )

        var openPos = -1
        var closePos = -1
        val stack = Stack<Char>()

        for (i in cursorOffset downTo 0) {
            if (text[i] in openBrackets) {
                if (stack.isEmpty()) {
                    openPos = i
                    break
                } else {
                    stack.pop()
                }
            } else if (text[i] in closeBrackets.values) {
                if (i != cursorOffset) {
                    stack.push(text[i])
                }
            }
        }

        if (openPos == -1) return null
        val expectedClose = closeBrackets[text[openPos]] ?: return null

        stack.clear()
        for (i in openPos + 1 until text.length) {
            if (text[i] == expectedClose) {
                if (stack.isEmpty()) {
                    closePos = i
                    break
                } else {
                    stack.pop()
                }
            } else if (text[i] == text[openPos]) {
                stack.push(text[i])
            }
        }

        return if (closePos != -1) TextRange(openPos + 1, closePos) else null
    }

    private fun replaceHighLevelCommas(text: String): String {
        val result = StringBuilder()
        var level = 0
        for (i in text.indices) {
            when (text[i]) {
                '(', '[', '{' -> level++
                ')', ']', '}' -> level--
                ',' -> {
                    if (level == 0) {
                        result.append(",\n") // Replace high-level commas with a newline
                        continue
                    }
                }
            }
            result.append(text[i])
        }
        return result.toString()
    }

    private fun joinParametersIntoSingleLine(text: String): String {
        return text.lines().joinToString(" ") { it.trim() }
    }

    private fun performReformatting(event: AnActionEvent, document: Document) {
        val project = event.project ?: return
        val psiDocumentManager = PsiDocumentManager.getInstance(project)
        psiDocumentManager.commitDocument(document)
        val psiFile = psiDocumentManager.getPsiFile(document) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(psiFile)
        }
    }
}
