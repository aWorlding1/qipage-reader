package local.readapp.feature

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext

/**
 * 光栖书斋统一动效语言。
 *
 * 设计原则（对齐动效规范）：
 * - 反馈类动效（按下、选中）短促克制：100~200ms；
 * - 状态类动效（展开、切换）200~320ms，进出场有节奏；
 * - 退场快于入场（约 75%）；
 * - 默认不使用弹跳/回弹，仅在“确认感”场景使用轻阻尼弹簧；
 * - 全量尊重系统「减少动态效果」：动画缩放为 0 时退化为纯淡入或无动画。
 */
internal object ReaderMotion {
    const val PRESS_IN_MILLIS = 110
    const val PRESS_OUT_MILLIS = 220
    const val STATE_MILLIS = 260
    const val EXIT_MILLIS = 190
    const val STAGGER_STEP_MILLIS = 40

    /** 卡片/封面按压比例。纸感材质按压幅度更含蓄。 */
    const val PRESS_SCALE = 0.972f

    /** 对话框/面板入场时长。 */
    const val ENTER_MILLIS = 300

    /** 把动效时长降级为 0：供 animateXxxAsState 直接使用。 */
    fun motionMillis(reduceMotion: Boolean, millis: Int): Int = if (reduceMotion) 0 else millis
}

/**
 * 探测系统是否开启了「减少动态效果」（动画缩放为 0）。
 * 读取失败时保守返回 false，保持动效。
 */
@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * 卡片/封面按压反馈：按下轻微缩小，抬起带回弹。
 * 需与 clickable 共用同一个 interactionSource。
 */
internal fun Modifier.readerPressScale(
    interactionSource: MutableInteractionSource,
    reduceMotion: Boolean,
    pressedScale: Float = ReaderMotion.PRESS_SCALE
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) pressedScale else 1f,
        animationSpec = if (pressed) {
            tween(durationMillis = ReaderMotion.PRESS_IN_MILLIS, easing = FastOutSlowInEasing)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        },
        label = "reader_press_scale"
    )
    this.scale(scale.value)
}

/** 为可点击元素创建统一的按压交互源。 */
@Composable
internal fun rememberReaderInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/** 布尔切换进度（0f~1f）：用于收藏、选中态的颜色/缩放插值。 */
@Composable
internal fun rememberSwitchProgress(target: Boolean, reduceMotion: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (target) 1f else 0f,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 0)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "reader_switch_progress"
    )

/** 连续数值平滑跟随：用于阅读进度等连续变化量，消除跳变。 */
@Composable
internal fun rememberSmoothedFloat(target: Float, reduceMotion: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = target,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 0)
        } else {
            tween(durationMillis = ReaderMotion.STATE_MILLIS, easing = LinearOutSlowInEasing)
        },
        label = "reader_smoothed_float"
    )
