package com.deepseek.dsh

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deepseek.dsh.core.data.SpeechCatalog
import com.deepseek.dsh.core.data.SpeechPreparation
import com.deepseek.dsh.core.data.SpeechProvider
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 语音识别相关调用（Host `speech` Remote）。
 *
 * @param catalog 读目录：服务、语言、准备状态。
 * @param configure 保存服务/语言选择。
 * @param prepare 开始准备（下载/校验/加载模型）。
 * @param cancelPrepare 取消准备。
 */
data class SpeechHooks(
    val catalog: suspend () -> Result<SpeechCatalog>,
    val configure: suspend (providerId: String, language: String) -> Result<Boolean>,
    val prepare: suspend (providerId: String, downloadSource: String?) -> Result<Boolean>,
    val cancelPrepare: suspend (providerId: String) -> Result<Boolean>,
)

/**
 * 「录音输入」插件的原生界面（官方 `plugins.bundle.config` 的 VoicePreparation）。
 *
 * 这里是模型准备卡片：识别服务、识别语言、下载来源、准备进度与失败原因。
 * 文案取自官方 `experimental/client-ui-voice-input/src/client/locales.ts` 的
 * zh 字典；输入区的麦克风是另一块（composer activity），后续单独实现。
 */
internal object VoiceInputPluginUi : PluginUi {
    override val bundle: String = "@deepseek-ai/dsh-experimental-voice-input-bundle"

    @Composable
    override fun Section(hooks: PluginUiHooks) {
        val speech = hooks.speech
        if (speech == null) {
            Text(stringResource(R.string.voice_unavailable), color = LocalDshPanelColors.current.labelTertiary, fontSize = 13.sp)
            return
        }
        VoicePreparationSection(speech)
    }
}

/** 语言代码 → 官方显示名。 */
@Composable
private fun speechLanguageName(code: String): String = when (code) {
    "auto" -> stringResource(R.string.voice_lang_auto)
    "zh" -> stringResource(R.string.voice_lang_zh)
    "en" -> stringResource(R.string.voice_lang_en)
    "yue" -> stringResource(R.string.voice_lang_yue)
    "ja" -> stringResource(R.string.voice_lang_ja)
    "ko" -> stringResource(R.string.voice_lang_ko)
    else -> code
}

@Composable
private fun speechStepName(kind: String): String = when (kind) {
    "check" -> stringResource(R.string.voice_step_check)
    "model" -> stringResource(R.string.voice_step_model)
    "vad" -> stringResource(R.string.voice_step_vad)
    "verify" -> stringResource(R.string.voice_step_verify)
    "load" -> stringResource(R.string.voice_step_load)
    else -> kind
}

@Composable
private fun speechStepStatus(status: String): String = when (status) {
    "pending" -> stringResource(R.string.voice_status_pending)
    "running" -> stringResource(R.string.voice_status_running)
    "done" -> stringResource(R.string.voice_status_done)
    "failed" -> stringResource(R.string.voice_status_failed)
    "cancelled" -> stringResource(R.string.voice_status_cancelled)
    else -> status
}

@Composable
private fun speechPhaseLabel(phase: String): String = when (phase) {
    "ready" -> stringResource(R.string.voice_phase_ready)
    "checking" -> stringResource(R.string.voice_phase_checking)
    "downloading" -> stringResource(R.string.voice_phase_downloading)
    "verifying" -> stringResource(R.string.voice_phase_verifying)
    "loading" -> stringResource(R.string.voice_phase_loading)
    "waking" -> stringResource(R.string.voice_phase_waking)
    "cancelling" -> stringResource(R.string.voice_phase_cancelling)
    "cancelled" -> stringResource(R.string.voice_phase_cancelled)
    "failed" -> stringResource(R.string.voice_phase_failed)
    else -> stringResource(R.string.voice_phase_unprepared)
}

/** 下载来源 → 官方显示名。 */
@Composable
private fun sourceLabel(source: String): String = when (source) {
    "https://huggingface.co" -> "Hugging Face"
    "https://hf-mirror.com" -> stringResource(R.string.voice_source_hf_mirror)
    else -> source
}

/** 这些阶段的准备还在推进，需要轮询目录刷新进度。 */
private val SPEECH_ACTIVE_PHASES = setOf(
    "checking", "downloading", "verifying", "loading", "waking", "cancelling", "preparing",
)

@Composable
private fun VoicePreparationSection(speech: SpeechHooks) {
    val colors = LocalDshPanelColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<SpeechCatalog?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var downloadSource by remember { mutableStateOf("") }

    LaunchedEffect(reload) {
        speech.catalog().fold(
            onSuccess = { catalog = it; loadError = null },
            onFailure = { loadError = context.getString(R.string.voice_unavailable) },
        )
    }
    // 准备过程中轮询：官方是流式状态，这里用目录轮询替代（同一份 Host 状态）。
    val active = catalog?.selected?.preparation?.phase in SPEECH_ACTIVE_PHASES
    LaunchedEffect(active, reload) {
        while (active) {
            delay(1500)
            speech.catalog().onSuccess { catalog = it }
        }
    }

    val current = catalog
    if (loadError != null && current == null) {
        Text(loadError!!, color = colors.labelTertiary, fontSize = 13.sp)
        return
    }
    if (current == null) {
        Text(stringResource(R.string.voice_reading), color = colors.labelTertiary, fontSize = 13.sp)
        return
    }
    val provider = current.selected
    if (provider == null) {
        Text(stringResource(R.string.voice_unavailable), color = colors.labelTertiary, fontSize = 13.sp)
        return
    }

    // 识别服务
    Text(stringResource(R.string.voice_service), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    ChoiceChipFlow(
        options = current.providers.map { item -> item.id to item.name },
        selected = provider.id,
        enabled = !busy,
        onSelect = { providerId ->
            scope.launch {
                busy = true
                speech.configure(providerId, current.language).onSuccess { reload += 1 }
                busy = false
            }
        },
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (provider.location == "host-local") {
            stringResource(R.string.voice_audio_local)
        } else {
            stringResource(R.string.voice_audio_cloud)
        },
        color = colors.labelTertiary,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    )

    // 识别语言
    Spacer(Modifier.height(14.dp))
    Text(stringResource(R.string.voice_language), color = colors.labelPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    ChoiceChipFlow(
        options = provider.languages.map { language -> language to (speechLanguageName(language)) },
        selected = current.language,
        enabled = !busy,
        onSelect = { language ->
            scope.launch {
                busy = true
                speech.configure(provider.id, language).onSuccess { reload += 1 }
                busy = false
            }
        },
    )

    // 准备卡片
    val preparation = provider.preparation
    Spacer(Modifier.height(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgModulePlatform)
            .padding(12.dp),
    ) {
        Text(provider.name, color = colors.labelPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        val phase = preparation?.phase ?: "unprepared"
        Spacer(Modifier.height(4.dp))
        Text(
            text = speechPhaseLabel(phase),
            color = when (phase) {
                "failed" -> colors.stateErrorPrimary
                "ready" -> androidx.compose.material3.MaterialTheme.colorScheme.primary
                else -> colors.labelSecondary
            },
            fontSize = 12.sp,
        )
        if (phase == "unprepared" && provider.location == "host-local") {
            provider.setupEstimate?.let { estimate ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.voice_estimate,
                        estimate.recommendedDiskBytes / 1_000_000_000,
                        estimate.expectedMemoryBytes / 1_000_000_000,
                        estimate.minimumMinutes,
                        estimate.maximumMinutes,
                    ),
                    color = colors.labelTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        preparation?.totalBytes?.let { total ->
            val completed = preparation.completedBytes
            if (total > 0 && completed != null) {
                Spacer(Modifier.height(8.dp))
                val fraction = (completed.toFloat() / total).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.voice_progress,
                        completed / 1_000_000,
                        total / 1_000_000,
                        (fraction * 100).toInt(),
                    ),
                    color = colors.labelTertiary,
                    fontSize = 11.sp,
                )
            }
        }
        if (preparation != null && preparation.steps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            preparation.steps.forEach { step ->
                Text(
                    text = "${speechStepName(step.kind)} · " +
                        (speechStepStatus(step.status)),
                    color = if (step.status == "failed") colors.stateErrorPrimary else colors.labelTertiary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                )
            }
        }
        if (phase == "failed" && preparation?.message != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.voice_prepare_failed, preparation.message.orEmpty()),
                color = colors.stateErrorPrimary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = stringResource(R.string.voice_prepare_hint),
                color = colors.labelTertiary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            preparation.source?.let { source ->
                Text(stringResource(R.string.voice_download_origin, source), color = colors.labelTertiary, fontSize = 11.sp)
            }
        }

        // 下载来源
        if (provider.downloadSources.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.voice_model_source), color = colors.labelPrimary, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            ChoiceChipFlow(
                options = buildList {
                    if (provider.downloadSources.size > 1) add("" to stringResource(R.string.voice_auto_source))
                    provider.downloadSources.forEach { source -> add(source to sourceLabel(source)) }
                },
                selected = downloadSource,
                enabled = !busy,
                onSelect = { downloadSource = it },
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (phase == "unprepared" || phase == "failed" || phase == "cancelled") {
                ActionChip(
                    text = if (phase == "unprepared") stringResource(R.string.voice_download_prepare) else stringResource(R.string.voice_retry_prepare),
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            speech.prepare(provider.id, downloadSource.ifBlank { null })
                            reload += 1
                            busy = false
                        }
                    },
                )
            }
            if (phase in SPEECH_ACTIVE_PHASES) {
                ActionChip(
                    text = stringResource(R.string.voice_cancel_prepare),
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            speech.cancelPrepare(provider.id)
                            reload += 1
                            busy = false
                        }
                    },
                )
            }
        }
    }
}

/** 选项胶囊（选中态用主按钮底色）。 */
@Composable
private fun ChoiceChip(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalDshPanelColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) colors.buttonPrimaryFill else colors.bgModulePlatform)
            .border(
                0.5.dp,
                if (selected) colors.buttonPrimaryFill else colors.borderL2,
                RoundedCornerShape(9.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = if (selected) colors.labelPrimaryForeground else colors.labelSecondary,
            fontSize = 12.sp,
        )
    }
}

/** 动作胶囊（描边按钮）。 */
@Composable
private fun ActionChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalDshPanelColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.bgModulePlatform)
            .border(0.5.dp, colors.borderL2, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(text = text, color = colors.labelSecondary, fontSize = 12.5.sp)
    }
}
