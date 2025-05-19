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
import liveplugin.PluginUtil.*

//import com.intellij.psi.PsiClass


registerAction(
    id = "Separate-line formatter",
    keyStroke = "ctrl shift alt ENTER",
    action = SeparateLineFormatter()
)

registerAction(
    id = "Field sorter",
    keyStroke = "ctrl shift alt O",
    action = FieldSorter()
)

class SeparateLineFormatter : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val editor = CommonDataKeys.EDITOR.getData(event.dataContext)
        if (project == null || editor == null) return

        val document = editor.document
        val cursorOffset = editor.caretModel.offset
        val range = findEnclosingBrackets(document.text, cursorOffset) ?: return
        val text = document.getText(range)
//        show(text)
        val modifiedText = if (text.contains("\n")) {
            joinParametersIntoSingleLine(text)
        } else {
            wrap(text)
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

        return if (closePos != -1) TextRange(openPos, closePos + 1) else null
    }

    private fun wrap(text: String): String {
        val result = StringBuilder()
        var level = 0
        for (i in text.indices) {
            when (text[i]) {
                '(', '[', '{' -> {
                    level++
                    if (level == 1) {
                        result.append("${text[i]}\n")
                        continue
                    }
                }

                ')', ']', '}' -> {
                    level--
                    if (level == 0) {
                        result.append("\n${text[i]}")
                        continue
                    }
                }

                ',' -> {
                    if (level == 1) {
                        result.append(",\n")
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

}

fun performReformatting(event: AnActionEvent, document: Document) {
    val project = event.project ?: return
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitDocument(document)
    val psiFile = psiDocumentManager.getPsiFile(document) ?: return

    WriteCommandAction.runWriteCommandAction(project) {
        CodeStyleManager.getInstance(project).reformat(psiFile)
    }
}

class FieldSorter : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val editor = CommonDataKeys.EDITOR.getData(event.dataContext)
        if (project == null || editor == null) return

        val document = editor.document

        val text = document.text
        val modifiedText = sortFields(text)

        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(0, text.length, modifiedText)
        }
    }

    private fun sortFields(text: String): String {
        val lines = text.lines()
        val publicStaticFinalFields = lines.filter {
            val line = it.trim()
            line.startsWith("public static final") && line.endsWith(";")
        }
        val privateStaticFinalFields = lines.filter {
            val line = it.trim()
            line.startsWith("private static final") && line.endsWith(";")
        }
        val privateFinalFields = lines.filter {
            val line = it.trim()
            line.startsWith("private final") && line.endsWith(";")
        }
        val privateFieldsWithAnnotations = arrayListOf<String>()
        val separatePrivateFieldsAndTheirAnnotations = arrayListOf<String>()
        for (i in lines.indices) {
            val line = lines[i].trim()
            if (
                line.startsWith("private")
                && line.endsWith(";")
                && !line.startsWith("private static final")
                && !line.startsWith("private final")
            ) {
                var j = i - 1
                while (
                    !lines[j].trim().endsWith(";")
                    && !lines[j].trim().endsWith("}")
                    && lines[j].trim() != ""
                    && !lines[j].contains("class")
                ) {
                    j--
                }
                val multipleLines = lines.subList(j + 1, i + 1)
                val multipleLinesAsSingleString = multipleLines.joinToString("\n")
                separatePrivateFieldsAndTheirAnnotations.addAll(multipleLines)
                privateFieldsWithAnnotations.add(multipleLinesAsSingleString)
            }
        }
        val otherLines = lines.filter {
            !publicStaticFinalFields.contains(it)
                    && !privateStaticFinalFields.contains(it)
                    && !privateFinalFields.contains(it)
                    && !separatePrivateFieldsAndTheirAnnotations.contains(it)
        }
//        show(publicStaticFinalFields)
//        show(privateStaticFinalFields)
//        show(privateFinalFields)
//        show(privateFieldsWithAnnotations)
//        show(otherLines)
        val modifiedLines = arrayListOf<String>()
        var interruptIndex = -1
        for (i in otherLines.indices) {
            modifiedLines.add(otherLines[i])
            if (otherLines[i].trim().endsWith("{")) {
                interruptIndex = i + 1
                break
            }
        }
        if (otherLines[interruptIndex].trim() == "") {
            modifiedLines.add(otherLines[interruptIndex])
        } else {
            modifiedLines.add("")
            interruptIndex--
        }
        modifiedLines.addAll(
            publicStaticFinalFields.sortedBy { it.length }
        )
        modifiedLines.addAll(
            privateStaticFinalFields.sortedBy { it.length }
        )
        modifiedLines.addAll(
            privateFinalFields.sortedBy { it.length }
        )
        modifiedLines.addAll(
            privateFieldsWithAnnotations.sortedBy { it.substringAfter("private").length }
        )
        if (otherLines[interruptIndex + 1].trim() != "") {
            modifiedLines.add("")
        }
        for (i in interruptIndex + 1 until otherLines.size) {
            modifiedLines.add(otherLines[i])
        }
        return modifiedLines.joinToString("\n")
    }
}
