package com.deepseek.dsh

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deepseek.dsh.core.designsystem.DshDialog
import com.deepseek.dsh.core.designsystem.DshDialogButton
import com.deepseek.dsh.core.designsystem.DshTextInput
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** 解析后的一道问题。 */
private data class ParsedQuestion(
    val id: String,
    val text: String,
    val detail: String?,
    val options: List<String>,
    val multi: Boolean,
)

/**
 * 官方 `user-questions/request` 的原生回答弹窗。
 *
 * 结果按 `{answers:[{id,selected:[label],custom?}]}` 回给 Host，与官方
 * `AskUserQuestionAnswer` 一致。
 */
@Composable
fun UserQuestionDialog(
    questions: JsonArray,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val parsed = remember(questions) {
        questions.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            ParsedQuestion(
                id = (obj["id"] as? JsonPrimitive)?.content.orEmpty(),
                text = (obj["question"] as? JsonPrimitive)?.content.orEmpty(),
                detail = (obj["detail"] as? JsonPrimitive)?.content,
                options = (obj["options"] as? JsonArray)?.mapNotNull { option ->
                    val label = (option as? JsonObject)?.get("label") as? JsonPrimitive
                    label?.content
                } ?: emptyList(),
                multi = obj["multiSelect"]?.toString() == "true",
            )
        }
    }
    val selection = remember(questions) { mutableStateMapOf<String, Set<String>>() }
    val custom = remember(questions) { mutableStateMapOf<String, String>() }
    val ready = parsed.isNotEmpty() && parsed.all { question ->
        !selection[question.id].isNullOrEmpty() || !custom[question.id].isNullOrBlank()
    }

    fun answersJson(): String = buildJsonObject {
        put("answers", buildJsonArray {
            parsed.forEach { question ->
                add(buildJsonObject {
                    put("id", question.id)
                    put("selected", buildJsonArray {
                        selection[question.id].orEmpty().forEach { add(JsonPrimitive(it)) }
                    })
                    custom[question.id]?.takeIf { it.isNotBlank() }?.let { put("custom", it) }
                })
            }
        })
    }.toString()

    DshDialog(
        onDismiss = onDismiss,
        title = stringResource(R.string.question_request_title),
        closeLabel = stringResource(R.string.action_close),
        footer = {
            DshDialogButton(
                text = stringResource(R.string.action_cancel),
                primary = false,
                enabled = true,
                onClick = onDismiss,
            )
            Spacer(modifier = Modifier.width(8.dp))
            DshDialogButton(
                text = stringResource(R.string.question_submit),
                primary = true,
                enabled = ready,
                onClick = { onSubmit(answersJson()) },
            )
        },
        content = {
            Column {
                parsed.forEach { question ->
                    Text(text = question.text)
                    question.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                        Text(text = detail)
                    }
                    val selected = selection[question.id].orEmpty()
                    question.options.forEach { label ->
                        val on = label in selected
                        TextButton(
                            onClick = {
                                selection[question.id] = when {
                                    question.multi && on -> selected - label
                                    question.multi -> selected + label
                                    else -> setOf(label)
                                }
                            },
                        ) {
                            Text(text = if (on) "\u2713 $label" else label)
                        }
                    }
                    if (question.options.isEmpty()) {
                        DshTextInput(
                            value = custom[question.id].orEmpty(),
                            onValueChange = { custom[question.id] = it },
                            placeholder = stringResource(R.string.question_custom_placeholder),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
    )
}
