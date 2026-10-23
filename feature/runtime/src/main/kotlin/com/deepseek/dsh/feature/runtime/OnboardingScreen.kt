package com.deepseek.dsh.feature.runtime

import androidx.activity.compose.BackHandler
import com.deepseek.dsh.core.designsystem.DshDialogButton
import com.deepseek.dsh.core.designsystem.DshDialog
import com.deepseek.dsh.core.designsystem.DshPanelDefaults
import com.deepseek.dsh.core.designsystem.LocalDshPanelColors
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Phone adaptation of the DeepSeek Harness desktop first-run onboarding.
 *
 * The flow, copy, icons and preference semantics are copied from the
 * client (`ui-settings-account` DesktopOnboarding): welcome → credit → purpose →
 * process, with the same back/skip footer and confirmation dialogs. Only the
 * layout, spacing and artwork presentation are adapted to a portrait phone:
 * single-column pages, full-width touch targets, safe-area padding, and the
 * artwork scaled into a phone-friendly hero.
 *
 * On completion the caller receives an [OnboardingResult] that maps to the same
 * preferences the desktop client applies (work-detail mode, usage, developer tools).
 *
 * @param onComplete invoked once the user finishes or explicitly skips the flow.
 * @param onRecharge recharge page action; null disables every "add credits" control
 *   exactly like the client without a platform page channel.
 * @param funded whether the account already holds positive credit; selects the
 *   credit page's action order like the `hasOnboardingCredit` check.
 * @param modifier external layout modifier.
 */
@Composable
fun OnboardingScreen(
    onComplete: (OnboardingResult) -> Unit,
    onRecharge: (() -> Unit)? = null,
    funded: Boolean = true,
    /** 未登录且没有 API Key 时，先展示官方「开始使用」授权页。 */
    authenticated: Boolean = true,
    onSignIn: () -> Unit = {},
    onAddApiKey: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = rememberOnboardingPalette()
    // 官方 WelcomePage：已有登录态/API Key 的用户直接跳过授权页；没有凭据时
    // 先展示「登录 / 添加 API Key」页。
    var step by remember { mutableStateOf(if (authenticated) OnboardingStep.WELCOME else OnboardingStep.AUTH) }
    var purpose by remember { mutableStateOf<OnboardingPurpose?>(null) }
    var process by remember { mutableStateOf<OnboardingProcess?>(null) }
    var confirmation by remember { mutableStateOf<OnboardingConfirmation?>(null) }

    var creditFunded by remember { mutableStateOf<Boolean?>(null) }
    // The credit page freezes its action order at first entry; a balance
    // update while the page is open must not swap the primary and secondary actions.
    LaunchedEffect(step) {
        if (step == OnboardingStep.CREDIT && creditFunded == null) creditFunded = funded
    }
    val effectiveFunded = creditFunded ?: funded
    val canRecharge = onRecharge != null

    fun finish(skipped: Boolean) {
        onComplete(OnboardingResult.of(purpose, process, skipped))
    }

    // 登录/添加 API Key 是异步完成的：凭据就绪后自动进入欢迎页。
    LaunchedEffect(authenticated) {
        if (authenticated && step == OnboardingStep.AUTH) step = OnboardingStep.WELCOME
    }

    fun goBack() {
        step = when (step) {
            OnboardingStep.AUTH, OnboardingStep.WELCOME -> OnboardingStep.WELCOME
            OnboardingStep.CREDIT -> OnboardingStep.WELCOME
            OnboardingStep.PURPOSE -> OnboardingStep.CREDIT
            OnboardingStep.PROCESS -> OnboardingStep.PURPOSE
        }
    }

    // The surface captures Escape/back and moves one page back; while a
    // confirmation is open it closes that dialog instead.
    BackHandler(enabled = confirmation == null && step != OnboardingStep.WELCOME && step != OnboardingStep.AUTH) { goBack() }
    BackHandler(enabled = confirmation != null) { confirmation = null }

    CompositionLocalProvider(LocalOnboardingPalette provides palette) {
        Surface(color = palette.background, modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .then(if (confirmation != null) Modifier.blur(10.dp) else Modifier),
            ) {
                AnimatedContent(
                    targetState = step,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    transitionSpec = {
                        // 前进/后退用不同方向：新页面从对应方向滑入，旧页面反向轻微滑出。
                        val forward = targetState.ordinal >= initialState.ordinal
                        val enter = slideInHorizontally(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                        ) { width -> if (forward) width / 5 else -width / 5 } +
                            fadeIn(tween(240, delayMillis = 30))
                        val exit = slideOutHorizontally(
                            animationSpec = tween(240, easing = FastOutSlowInEasing),
                        ) { width -> if (forward) -width / 8 else width / 8 } +
                            fadeOut(tween(160))
                        enter togetherWith exit
                    },
                    label = "onboarding-page",
                ) { current ->
                    when (current) {
                        OnboardingStep.AUTH -> AuthPage(
                            onSignIn = onSignIn,
                            onAddApiKey = onAddApiKey,
                        )

                        OnboardingStep.WELCOME -> WelcomePage(
                            onStart = { step = OnboardingStep.CREDIT },
                        )

                        OnboardingStep.CREDIT -> CreditPage(
                            funded = effectiveFunded,
                            canRecharge = canRecharge,
                            onPrimary = {
                                if (effectiveFunded) {
                                    step = OnboardingStep.PURPOSE
                                } else {
                                    confirmation = null
                                    onRecharge?.invoke()
                                }
                            },
                            onSecondary = {
                                if (effectiveFunded) {
                                    onRecharge?.invoke()
                                } else {
                                    // `later`: an unfunded balance asks before
                                    // leaving the credit page.
                                    confirmation = OnboardingConfirmation.CREDIT
                                }
                            },
                        )

                        OnboardingStep.PURPOSE -> PurposePage(
                            purpose = purpose,
                            onPurposeChange = { purpose = it },
                            onContinue = {
                                // An office-only purpose finishes here with
                                // the compact preference and never shows process.
                                if (purpose == OnboardingPurpose.OFFICE) {
                                    finish(skipped = false)
                                } else {
                                    step = OnboardingStep.PROCESS
                                }
                            },
                        )

                        OnboardingStep.PROCESS -> ProcessPage(
                            process = process,
                            onProcessChange = { process = it },
                            onEnter = { finish(skipped = false) },
                        )
                    }
                }

                // 页脚只在离开欢迎页后出现：淡入并从下方轻推上来。
                AnimatedVisibility(
                    visible = step != OnboardingStep.WELCOME && step != OnboardingStep.AUTH,
                    enter = fadeIn(tween(220, delayMillis = 60)) +
                        slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 3 },
                    exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 4 },
                ) {
                    OnboardingFooter(
                        onBack = { goBack() },
                        onSkip = {
                            confirmation = if (step == OnboardingStep.CREDIT) {
                                OnboardingConfirmation.CREDIT_SKIP
                            } else {
                                OnboardingConfirmation.SKIP
                            }
                        },
                    )
                }
            }
        }

        confirmation?.let { kind ->
            OnboardingConfirmationDialog(
                kind = kind,
                canRecharge = canRecharge,
                onClose = { confirmation = null },
                onContinueSetup = { confirmation = null },
                onUnderstood = {
                    confirmation = null
                    if (kind == OnboardingConfirmation.CREDIT) {
                        step = OnboardingStep.PURPOSE
                    } else {
                        finish(skipped = true)
                    }
                },
                onEnterApp = {
                    confirmation = null
                    finish(skipped = true)
                },
                onRecharge = {
                    confirmation = null
                    onRecharge?.invoke()
                },
            )
        }
    }
}

private enum class OnboardingStep { AUTH, WELCOME, CREDIT, PURPOSE, PROCESS }

private enum class OnboardingConfirmation { CREDIT, CREDIT_SKIP, SKIP }

// ---------------------------------------------------------------------------
// Palette: ui-theme onboarding tokens (design-platform.css + onboarding.css).
// ---------------------------------------------------------------------------

private data class OnboardingPalette(
    val background: Color,
    val labelPrimary: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelPrimaryForeground: Color,
    val brandPrimary: Color,
    val borderL2: Color,
    val borderL3: Color,
    val cardFill: Color,
    val secondaryFill: Color,
    val checkboxBorder: Color,
    val dialogSurface: Color,
    val accent: Color,
)

private val LightOnboardingPalette = OnboardingPalette(
    background = Color(0xFFFFFFFF),
    labelPrimary = Color(0xFF0F1115),
    labelSecondary = Color(0xFF61666B),
    labelTertiary = Color(0xFF81858C),
    labelPrimaryForeground = Color(0xFFFFFFFF),
    brandPrimary = Color(0xFF0F1115),
    borderL2 = Color(0x1A000000),
    borderL3 = Color(0x1F000000),
    cardFill = Color(0xFFFFFFFF),
    secondaryFill = Color(0xFFFFFFFF),
    checkboxBorder = Color(0x330F1115),
    dialogSurface = Color(0xFFFFFFFF),
    accent = Color(0xFF3964FE),
)

private val DarkOnboardingPalette = OnboardingPalette(
    background = Color(0xFF151517),
    labelPrimary = Color(0xFFF9FAFB),
    labelSecondary = Color(0xFFCFD3D6),
    labelTertiary = Color(0xFFADB2B8),
    labelPrimaryForeground = Color(0xFF0F1115),
    brandPrimary = Color(0xFFF9FAFB),
    borderL2 = Color(0x1FFFFFFF),
    borderL3 = Color(0x29FFFFFF),
    cardFill = Color(0xCC2C2C2E),
    secondaryFill = Color(0xFF48494C),
    checkboxBorder = Color(0x1FFFFFFF),
    dialogSurface = Color(0xFF2C2C2E),
    accent = Color(0xFF3964FE),
)

private val LocalOnboardingPalette = staticCompositionLocalOf<OnboardingPalette> {
    error("The onboarding palette is only available inside OnboardingScreen.")
}

@Composable
private fun rememberOnboardingPalette(): OnboardingPalette =
    if (com.deepseek.dsh.core.designsystem.LocalDshDarkTheme.current) DarkOnboardingPalette else LightOnboardingPalette

// Conic border stops (--dsw-gradient-onboarding-*-stops), seam included.
private val BlueCardBorder = listOf(
    0f to Color(0xFF3964FE),
    0.1875f to Color(0xFF3964FE),
    0.5178f to Color(0xFF398EFE),
    0.86252f to Color(0xFF6DCCFF),
    1f to Color(0xFF3964FE),
)

private val VioletCardBorder = listOf(
    0f to Color(0xFF2A2FB6),
    0.33102f to Color(0xFF2A2FB6),
    0.50954f to Color(0xFF8B76F6),
    0.85326f to Color(0xFFC5C0FF),
    1f to Color(0xFF2A2FB6),
)

private val CyanCardBorder = listOf(
    0f to Color(0xFF0293B4),
    0.21154f to Color(0xFF0293B4),
    0.50954f to Color(0xFF2DC8EB),
    0.85326f to Color(0xFFAFF0FF),
    1f to Color(0xFF0293B4),
)

// FileTypeIcon sets shown when a purpose card is selected.
private val OfficeFileIcons = listOf(
    R.drawable.onb_file_word,
    R.drawable.onb_file_ppt,
    R.drawable.onb_file_excel,
    R.drawable.onb_file_pdf,
    R.drawable.onb_file_image,
)

private val CodeFileIcons = listOf(
    R.drawable.onb_code_typescript,
    R.drawable.onb_code_javascript,
    R.drawable.onb_code_sql,
    R.drawable.onb_code_python,
    R.drawable.onb_code_cpp,
)

// iconEnter stagger: 0 / 30 / 50 / 62 / 70 ms.
private val FileIconBurstDelays = listOf(0L, 30L, 50L, 62L, 70L)

// ---------------------------------------------------------------------------
// Pages
// ---------------------------------------------------------------------------

/**
 * 官方桌面 WelcomePage 的 entry 版：品牌字标 + 「欢迎使用 DeepSeek Harness」+
 * 「组装无限可能，共探智能上限」+ 登录 / 添加 API Key 两个动作。
 * 没有「稍后」——官方这一页必须选择一种授权方式；API Key 页自身才提供稍后。
 */
@Composable
private fun AuthPage(
    onSignIn: () -> Unit,
    onAddApiKey: () -> Unit,
) {
    val palette = LocalOnboardingPalette.current
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(
                    if (com.deepseek.dsh.core.designsystem.LocalDshDarkTheme.current) {
                        R.drawable.onb_brand_dark
                    } else {
                        R.drawable.onb_brand_light
                    },
                ),
                contentDescription = stringResource(R.string.onb_brand),
                modifier = Modifier.fillMaxWidth(0.6f),
            )
            Spacer(Modifier.height(40.dp))
            OnboardingHeadline(prefix = stringResource(R.string.onb_welcome), centered = true)
            Spacer(Modifier.height(10.dp))
            OnboardingBody(text = stringResource(R.string.onb_welcome_desc), centered = true)
            Spacer(Modifier.height(48.dp))
            OnboardingButton(
                text = stringResource(R.string.onb_auth_login),
                primary = true,
                enabled = true,
                onClick = onSignIn,
                fillWidth = false,
                modifier = Modifier.width(232.dp),
            )
            Spacer(Modifier.height(12.dp))
            OnboardingButton(
                text = stringResource(R.string.onb_auth_add_key),
                primary = false,
                enabled = true,
                onClick = onAddApiKey,
                fillWidth = false,
                modifier = Modifier.width(232.dp),
            )
        }
    }
}

@Composable
private fun WelcomePage(onStart: () -> Unit) {
    OnboardingPage {
        OnboardingArtwork(welcome = true, maxHeight = 240.dp)
        Spacer(Modifier.height(28.dp))
        OnboardingHeadline(prefix = stringResource(R.string.onb_welcome))
        Spacer(Modifier.height(12.dp))
        OnboardingBody(text = stringResource(R.string.onb_introduction))
        Spacer(Modifier.height(36.dp))
        OnboardingButton(
            text = stringResource(R.string.onb_start),
            primary = true,
            enabled = true,
            onClick = onStart,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CreditPage(
    funded: Boolean,
    canRecharge: Boolean,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    OnboardingPage {
        OnboardingArtwork(welcome = false, maxHeight = 300.dp)
        Spacer(Modifier.height(26.dp))
        OnboardingPlainHeadline(text = stringResource(R.string.onb_credit))
        Spacer(Modifier.height(12.dp))
        OnboardingBody(text = stringResource(R.string.onb_credit_description))
        Spacer(Modifier.height(30.dp))
        OnboardingButton(
            text = stringResource(if (funded) R.string.onb_continue else R.string.onb_top_up),
            primary = true,
            enabled = funded || canRecharge,
            onClick = onPrimary,
        )
        Spacer(Modifier.height(12.dp))
        OnboardingButton(
            text = stringResource(if (funded) R.string.onb_funded_top_up else R.string.onb_later),
            primary = false,
            enabled = !(funded && !canRecharge),
            onClick = onSecondary,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PurposePage(
    purpose: OnboardingPurpose?,
    onPurposeChange: (OnboardingPurpose?) -> Unit,
    onContinue: () -> Unit,
) {
    val officeSelected = purpose == OnboardingPurpose.OFFICE || purpose == OnboardingPurpose.BOTH
    val developmentSelected = purpose == OnboardingPurpose.DEVELOPMENT || purpose == OnboardingPurpose.BOTH

    fun toggle(target: OnboardingPurpose) {
        val office = if (target == OnboardingPurpose.OFFICE) !officeSelected else officeSelected
        val development = if (target == OnboardingPurpose.DEVELOPMENT) !developmentSelected else developmentSelected
        onPurposeChange(
            when {
                office && development -> OnboardingPurpose.BOTH
                office -> OnboardingPurpose.OFFICE
                development -> OnboardingPurpose.DEVELOPMENT
                else -> null
            },
        )
    }

    OnboardingPage {
        OnboardingHeadline(
            prefix = stringResource(R.string.onb_purpose_prefix),
                suffix = stringResource(R.string.onb_purpose_suffix),
                centered = true,
            )
            Spacer(Modifier.height(10.dp))
            OnboardingBody(
            text = stringResource(R.string.onb_purpose_description),
            centered = true,
            lineHeight = 22.sp,
        )
            Spacer(Modifier.height(26.dp))
            PurposeCard(
                purpose = OnboardingPurpose.OFFICE,
                checked = officeSelected,
                onToggle = { toggle(OnboardingPurpose.OFFICE) },
            )
            Spacer(Modifier.height(14.dp))
            PurposeCard(
                purpose = OnboardingPurpose.DEVELOPMENT,
                checked = developmentSelected,
                onToggle = { toggle(OnboardingPurpose.DEVELOPMENT) },
            )
            Spacer(Modifier.height(26.dp))
            ReservedCtaSlot(visible = purpose != null) {
                OnboardingButton(
                    text = stringResource(R.string.onb_continue),
                    primary = true,
                    enabled = true,
                    onClick = onContinue,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
}

@Composable
private fun ProcessPage(
    process: OnboardingProcess?,
    onProcessChange: (OnboardingProcess) -> Unit,
    onEnter: () -> Unit,
) {
    OnboardingPage {
        OnboardingPlainHeadline(text = stringResource(R.string.onb_process), centered = true)
            Spacer(Modifier.height(10.dp))
            OnboardingBody(
            text = stringResource(R.string.onb_process_description),
            centered = true,
            lineHeight = 22.sp,
        )
            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProcessCard(
                    value = OnboardingProcess.COMPACT,
                    selected = process == OnboardingProcess.COMPACT,
                    onSelect = { onProcessChange(OnboardingProcess.COMPACT) },
                )
                ProcessCard(
                    value = OnboardingProcess.STANDARD,
                    selected = process == OnboardingProcess.STANDARD,
                    onSelect = { onProcessChange(OnboardingProcess.STANDARD) },
                )
                ProcessCard(
                    value = OnboardingProcess.DETAILED,
                    selected = process == OnboardingProcess.DETAILED,
                    onSelect = { onProcessChange(OnboardingProcess.DETAILED) },
                )
            }
            Spacer(Modifier.height(24.dp))
            ReservedCtaSlot(visible = process != null) {
                OnboardingButton(
                    text = stringResource(R.string.onb_enter),
                    primary = true,
                    enabled = true,
                    onClick = onEnter,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
}

// ---------------------------------------------------------------------------
// Shared content
// ---------------------------------------------------------------------------

/**
 * Center one page's content vertically when it fits and scroll when it does not.
 *
 * The pages are vertically centered with the primary action below the
 * content; a phone needs a scroll fallback for short screens, so the page
 * measures its own content and splits the leftover viewport height evenly.
 *
 * @param content page content, centered horizontally.
 */
@Composable
private fun OnboardingPage(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val density = LocalDensity.current
        var contentHeightPx by remember { mutableStateOf(0) }
        val contentHeight = with(density) { contentHeightPx.toDp() }
        val verticalPadding = if (contentHeightPx > 0 && contentHeight < viewportHeight) {
            (viewportHeight - contentHeight) / 2
        } else {
            0.dp
        }
        // 页面本体的入场：淡入并向上浮一小段（与页面滑动叠加，避免生硬切换）。
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            enter.animateTo(1f, animationSpec = tween(360, easing = FastOutSlowInEasing))
        }
        val riseOffset = with(density) { 14.dp.toPx() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * riseOffset
                }
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(verticalPadding))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .onSizeChanged { contentHeightPx = it.height },
                horizontalAlignment = Alignment.Start,
                content = content,
            )
            Spacer(Modifier.height(verticalPadding))
        }
    }
}

@Composable
private fun OnboardingArtwork(welcome: Boolean, maxHeight: Dp) {
    val dark = com.deepseek.dsh.core.designsystem.LocalDshDarkTheme.current
    val locales = LocalConfiguration.current.locales
    val chinese = locales.size() > 0 && locales.get(0).language == "zh"
    val artwork = when {
        welcome && chinese && dark -> R.drawable.onb_welcome_dark_zh
        welcome && chinese -> R.drawable.onb_welcome_light_zh
        welcome && dark -> R.drawable.onb_welcome_dark_en
        welcome -> R.drawable.onb_welcome_light_en
        chinese && dark -> R.drawable.onb_credit_dark_zh
        chinese -> R.drawable.onb_credit_light_zh
        dark -> R.drawable.onb_credit_dark_en
        else -> R.drawable.onb_credit_light_en
    }
    // 主视觉轻微漂浮：幅度很小（±3dp / 4.2s），只让首屏看起来“活着”。
    val floatTransition = rememberInfiniteTransition(label = "onboarding-artwork-float")
    val floatProgress by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "onboarding-artwork-offset",
    )
    val density = LocalDensity.current
    val floatOffset = with(density) { 3.dp.toPx() }
    Image(
        painter = painterResource(artwork),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .graphicsLayer { translationY = (floatProgress - 0.5f) * 2f * floatOffset },
    )
}

@Composable
private fun OnboardingHeadline(
    prefix: String,
    suffix: String? = null,
    centered: Boolean = false,
    showBrand: Boolean = true,
) {
    val palette = LocalOnboardingPalette.current
    // `.brand { white-space: nowrap }`: keep the product name on one line.
    val brand = stringResource(R.string.onb_brand).replace(' ', '\u00A0')
    val locales = LocalConfiguration.current.locales
    val chinese = locales.size() > 0 && locales.get(0).language == "zh"
    val text = buildAnnotatedString {
        append(prefix)
        if (showBrand) {
            append('\u0020')
            pushStyle(SpanStyle(fontWeight = FontWeight.Medium))
            append(brand)
            pop()
        }
        if (suffix != null) {
            // A Chinese sentence has no spaces at the wrap point; an explicit break
            // keeps the brand on the first line instead of splitting "帮你".
            append(if (chinese) '\n' else '\u0020')
            append(suffix)
        }
    }
    Text(
        text = text,
        color = palette.labelPrimary,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.2).sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = (if (centered) Modifier.fillMaxWidth() else Modifier).semantics { heading() },
    )
}

@Composable
private fun OnboardingPlainHeadline(text: String, centered: Boolean = false) {
    val palette = LocalOnboardingPalette.current
    Text(
        text = text,
        color = palette.labelPrimary,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.2).sp,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = (if (centered) Modifier.fillMaxWidth() else Modifier).semantics { heading() },
    )
}

@Composable
private fun OnboardingBody(
    text: String,
    centered: Boolean = false,
    lineHeight: TextUnit = 28.sp,
) {
    val palette = LocalOnboardingPalette.current
    Text(
        text = text,
        color = palette.labelTertiary,
        fontSize = 16.sp,
        lineHeight = lineHeight,
        fontWeight = FontWeight.Light,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = if (centered) Modifier.fillMaxWidth() else Modifier,
    )
}

@Composable
private fun OnboardingButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = true,
) {
    val palette = LocalOnboardingPalette.current
    val shape = RoundedCornerShape(12.dp)
    val sizeModifier = if (fillWidth) {
        Modifier.fillMaxWidth().height(50.dp)
    } else {
        Modifier.heightIn(min = 44.dp)
    }
    // 按压反馈：轻微缩小 + 变淡，松手回弹（与桌面客户端的按钮手感一致）。
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(90),
        label = "onboarding-button-scale",
    )
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.88f else 1f,
        animationSpec = tween(90),
        label = "onboarding-button-alpha",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        interactionSource = interaction,
        color = if (primary) palette.brandPrimary else palette.secondaryFill,
        contentColor = if (primary) palette.labelPrimaryForeground else palette.labelPrimary,
        border = if (primary) null else BorderStroke(0.5.dp, palette.borderL3),
        modifier = modifier
            .then(sizeModifier)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha * (if (enabled) 1f else 0.4f)
            },
    ) {
        Box(
            modifier = if (fillWidth) Modifier.fillMaxSize() else Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/**
 * Reserve the primary action's height so revealing it does not shift the page.
 *
 * The page absolutely positions its action below the cards; a phone
 * keeps that stability by composing the button inside a fixed slot.
 *
 * @param visible whether the primary action is available.
 * @param content primary action content.
 */
@Composable
private fun ReservedCtaSlot(visible: Boolean, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(50.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(150)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
        ) {
            content()
        }
    }
}

@Composable
private fun OnboardingFooter(onBack: () -> Unit, onSkip: () -> Unit) {
    val palette = LocalOnboardingPalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.onb_icon_back),
                contentDescription = null,
                tint = palette.labelTertiary,
                modifier = Modifier.size(width = 18.dp, height = 15.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.onb_back),
                color = palette.labelTertiary,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Light,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.onb_skip),
                color = palette.labelTertiary,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun PurposeCard(
    purpose: OnboardingPurpose,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val palette = LocalOnboardingPalette.current
    val office = purpose == OnboardingPurpose.OFFICE
    OnboardingCard(
        selected = checked,
        borderStops = if (office) VioletCardBorder else BlueCardBorder,
        checkbox = true,
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIconBurst(
                baseIcon = if (office) R.drawable.onb_icon_office else R.drawable.onb_icon_code,
                icons = if (office) OfficeFileIcons else CodeFileIcons,
                expanded = checked,
            )
            Spacer(Modifier.weight(1f))
            OnboardingCheckbox(checked = checked)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(if (office) R.string.onb_office else R.string.onb_development),
            color = palette.labelPrimary,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(
                if (office) R.string.onb_office_description else R.string.onb_development_description,
            ),
            color = palette.labelTertiary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun ProcessCard(
    value: OnboardingProcess,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val palette = LocalOnboardingPalette.current
    val border = when (value) {
        OnboardingProcess.COMPACT -> VioletCardBorder
        OnboardingProcess.STANDARD -> BlueCardBorder
        OnboardingProcess.DETAILED -> CyanCardBorder
    }
    val icon = when (value) {
        OnboardingProcess.COMPACT -> R.drawable.onb_icon_compact
        OnboardingProcess.STANDARD -> R.drawable.onb_icon_standard
        OnboardingProcess.DETAILED -> R.drawable.onb_icon_detailed
    }
    val iconSize = if (value == OnboardingProcess.COMPACT) 40.dp else 36.dp
    val title = when (value) {
        OnboardingProcess.COMPACT -> R.string.onb_compact
        OnboardingProcess.STANDARD -> R.string.onb_standard
        OnboardingProcess.DETAILED -> R.string.onb_detailed
    }
    val description = when (value) {
        OnboardingProcess.COMPACT -> R.string.onb_compact_description
        OnboardingProcess.STANDARD -> R.string.onb_standard_description
        OnboardingProcess.DETAILED -> R.string.onb_detailed_description
    }
    OnboardingCard(
        selected = selected,
        borderStops = border,
        checkbox = false,
        onClick = onSelect,
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(title),
            color = palette.labelPrimary,
            fontSize = 20.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Normal,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(description),
            color = palette.labelTertiary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun OnboardingCard(
    selected: Boolean,
    borderStops: List<Pair<Float, Color>>,
    checkbox: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalOnboardingPalette.current
    val cornerRadius = 24.dp
    val shape = RoundedCornerShape(cornerRadius)
    val angle = if (selected) {
        val transition = rememberInfiniteTransition(label = "onboarding-card-border")
        val value by transition.animateFloat(
            initialValue = 90f,
            targetValue = 450f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 14000, easing = LinearEasing)),
            label = "onboarding-card-border-angle",
        )
        value
    } else {
        0f
    }
    val interaction = if (checkbox) {
        Modifier.toggleable(value = selected, role = Role.Checkbox) { onClick() }
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.accent.copy(alpha = 0.10f),
                spotColor = palette.accent.copy(alpha = 0.08f),
            )
            .clip(shape)
            .background(palette.cardFill)
            .then(
                if (selected) {
                    Modifier.onboardingSweepBorder(borderStops, angle, cornerRadius)
                } else {
                    // `.card::before`: a hairline border on unselected cards.
                    Modifier.border(0.5.dp, palette.borderL2, shape)
                },
            )
            .then(interaction),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun OnboardingCheckbox(checked: Boolean) {
    val palette = LocalOnboardingPalette.current
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(shape)
            .background(if (checked) palette.brandPrimary else Color.Transparent)
            .border(2.dp, if (checked) palette.brandPrimary else palette.checkboxBorder, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(R.drawable.onb_icon_check),
                contentDescription = null,
                tint = palette.labelPrimaryForeground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun FileIconBurst(baseIcon: Int, icons: List<Int>, expanded: Boolean) {
    if (!expanded) {
        Image(
            painter = painterResource(baseIcon),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEachIndexed { index, icon ->
            val progress = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(FileIconBurstDelays.getOrElse(index) { 70L })
                progress.animateTo(1f, animationSpec = tween(150))
            }
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        alpha = progress.value
                        scaleX = 0.8f + 0.2f * progress.value
                        scaleY = scaleX
                    },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Confirmation dialogs
// ---------------------------------------------------------------------------

@Composable
private fun OnboardingConfirmationDialog(
    kind: OnboardingConfirmation,
    canRecharge: Boolean,
    onClose: () -> Unit,
    onContinueSetup: () -> Unit,
    onUnderstood: () -> Unit,
    onEnterApp: () -> Unit,
    onRecharge: () -> Unit,
) {
    val credit = kind != OnboardingConfirmation.SKIP
    val title = stringResource(if (credit) R.string.onb_no_credit_title else R.string.onb_skip_title)
    val description = stringResource(
        if (credit) R.string.onb_no_credit_description else R.string.onb_skip_description,
    )
    val secondaryText = stringResource(if (credit) R.string.onb_understood else R.string.onb_keep_setting)
    val primaryText = stringResource(if (credit) R.string.onb_go_top_up else R.string.onb_enter)
    val secondaryAction = if (credit) onUnderstood else onContinueSetup
    val primaryAction = if (credit) onRecharge else onEnterApp
    DshDialog(
        onDismiss = onClose,
        title = title,
        closeLabel = stringResource(R.string.onb_close),
        width = DshPanelDefaults.DialogWidth,
        content = {
            Text(
                text = description,
                color = LocalDshPanelColors.current.labelPrimary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
            )
        },
        footer = {
            DshDialogButton(
                text = secondaryText,
                primary = false,
                enabled = true,
                onClick = secondaryAction,
                modifier = Modifier.widthIn(min = 72.dp),
            )
            DshDialogButton(
                text = primaryText,
                primary = true,
                enabled = !credit || canRecharge,
                onClick = primaryAction,
                modifier = Modifier.widthIn(min = 136.dp),
            )
        },
    )
}

// ---------------------------------------------------------------------------
// Animated conic-gradient border
// ---------------------------------------------------------------------------

/**
 * Draw the rotating conic stroke around a rounded card.
 *
 * The gradient is rotated inside a ring clip so the rounded geometry stays put
 * while the color sweep moves, matching `borderSweep` in the desktop stylesheet.
 *
 * @param stops conic color stops.
 * @param angle current sweep angle in degrees.
 * @param cornerRadius card corner radius in dp.
 */
private fun Modifier.onboardingSweepBorder(
    stops: List<Pair<Float, Color>>,
    angle: Float,
    cornerRadius: Dp,
): Modifier = drawWithContent {
    drawContent()
    val stroke = 2.dp.toPx()
    val radius = (cornerRadius.toPx() - stroke / 2f).coerceAtLeast(0f)
    val outer = RoundRect(0f, 0f, size.width, size.height, radius, radius)
    val inner = RoundRect(stroke, stroke, size.width - stroke, size.height - stroke, radius, radius)
    val ring = Path().apply {
        addRoundRect(outer)
        val hole = Path().apply { addRoundRect(inner) }
        op(this, hole, PathOperation.Difference)
    }
    clipPath(ring) {
        rotate(angle, pivot = center) {
            val extent = size.maxDimension
            drawRect(
                brush = Brush.sweepGradient(*stops.toTypedArray(), center = center),
                topLeft = Offset(center.x - extent, center.y - extent),
                size = Size(extent * 2f, extent * 2f),
            )
        }
    }
}
