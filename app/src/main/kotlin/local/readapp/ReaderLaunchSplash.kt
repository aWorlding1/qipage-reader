package local.readapp

import local.readapp.core.T

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay

private val Ink = Color(0xFF1C1712)
private val InkDeep = Color(0xFF0F0C09)
private val Amber = Color(0xFFC09B62)

/**
 * 光栖书斋品牌开屏。
 *
 * 主题「墨香晕染」：双层墨晕缓慢漂移 + 环形扩散 + 白底「书」字章弹性入场 + 光泽扫掠
 * + 光尘粒子 + 文字逐行错峰上浮 + 底部脉冲点。
 * 配色沿阅读气质：深墨底 + 琥珀柔光。动画总时长保持在两秒内，避免影响进入书架的速度。
 */
@Composable
fun ReaderLaunchSplash(onFinished: () -> Unit) {
    // 尊重系统「减少动态效果」：动画缩放为 0 时降级为纯淡入，不再做位移/扩散
    val reduceMotion = rememberReducedMotion()
    var entered by remember { mutableStateOf(false) }
    var captionIn by remember { mutableStateOf(false) }
    var titleIn by remember { mutableStateOf(false) }
    var sloganIn by remember { mutableStateOf(false) }

    val brandAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "brandAlpha"
    )
    // 弹性过冲入场，圆章更有生气；减少动态时退化为纯缩放淡入
    val brandScale by animateFloatAsState(
        targetValue = if (entered) 1f else if (reduceMotion) 0.94f else 0.72f,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "brandScale"
    )
    val brandRotation by animateFloatAsState(
        targetValue = if (entered || reduceMotion) 0f else -7f,
        animationSpec = if (reduceMotion) {
            tween(durationMillis = 320, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "brandRotation"
    )
    val auraAlpha = remember { Animatable(0f) }

    // 减少动态时把所有循环动效冻结在静态相位，只保留静帧式构图
    val motion = if (reduceMotion) 0f else 1f
    val transition = rememberInfiniteTransition(label = "readerAmbient")
    // 主墨晕漂移
    val driftRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )
    // 第二团墨晕反向漂移
    val bloomRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bloom"
    )
    // 环形扩散
    val ringRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "ringFront"
    )
    // 光泽扫掠
    val sweepRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "sweep"
    )
    // 光尘上浮
    val dustRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "dust"
    )
    // 脉冲点
    val pulseRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val drift = 0.5f + (driftRaw - 0.5f) * motion
    val bloom = 0.5f + (bloomRaw - 0.5f) * motion
    val ringFront = if (reduceMotion) 0.35f else ringRaw
    val sweep = if (reduceMotion) 0.5f else sweepRaw
    val dust = if (reduceMotion) 0.4f else dustRaw
    val pulse = if (reduceMotion) 0.5f else pulseRaw

    LaunchedEffect(Unit) { auraAlpha.animateTo(1f, tween(900)) }
    LaunchedEffect(Unit) {
        entered = true
        delay(80)
        captionIn = true
        delay(120)
        titleIn = true
        delay(120)
        sloganIn = true
        delay(SPLASH_HOLD_MILLIS)
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, InkDeep))),
        contentAlignment = Alignment.Center
    ) {
        // 主墨晕：琥珀柔光随 drift 缓慢呼吸漂移
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * (0.32f + 0.36f * drift), size.height * 0.38f)
            val radius = size.minDimension * 0.62f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Amber.copy(alpha = 0.22f * auraAlpha.value), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        // 第二团墨晕：自下而上反向漂移，模拟墨色在宣纸上晕开
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * (0.7f - 0.34f * bloom), size.height * 0.74f)
            val radius = size.minDimension * 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0x33E6C48A).copy(alpha = 0.18f * auraAlpha.value), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        // Canvas 光尘：缓缓上浮的微光点
        Canvas(Modifier.fillMaxSize().alpha(auraAlpha.value * 0.9f)) {
            val seeds = listOf(
                floatArrayOf(0.18f, 0.84f, 1.0f, 0.13f),
                floatArrayOf(0.34f, 0.54f, 1.5f, 0.09f),
                floatArrayOf(0.58f, 0.88f, 0.8f, 0.16f),
                floatArrayOf(0.74f, 0.40f, 1.3f, 0.12f),
                floatArrayOf(0.88f, 0.72f, 0.9f, 0.14f),
                floatArrayOf(0.44f, 0.24f, 1.4f, 0.10f),
                floatArrayOf(0.94f, 0.30f, 1.1f, 0.13f)
            )
            seeds.forEach { s ->
                val phase = (dust * s[2] + s[0]) % 1f
                val y = size.height * (s[1] - phase * 0.34f)
                val x = size.width * s[0] + sin((dust * 6.28f + s[0] * 10f)) * 8f
                val a = (1f - phase) * s[3] * 2.2f
                if (a > 0f) {
                    drawCircle(
                        color = Color(0xFFEBD6AE).copy(alpha = a.coerceIn(0f, 0.36f)),
                        radius = 1.4f + s[3] * 7f,
                        center = Offset(x, y)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                // 环形扩散：两道错峰光环，呼应「砚台涟漪」
                val ringAlpha = brandAlpha
                Canvas(modifier = Modifier.size(200.dp)) {
                    val maxR = size.minDimension / 2f
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    listOf(0f, 0.42f).forEach { offset ->
                        val p = (ringFront + offset) % 1f
                        val r = maxR * (0.42f + p * 0.58f)
                        val a = (1f - p) * 0.32f * ringAlpha
                        if (a > 0f) {
                            drawCircle(
                                color = Amber.copy(alpha = a),
                                radius = r,
                                center = Offset(cx, cy),
                                style = Stroke(width = 2.2f)
                            )
                        }
                    }
                }
                // 书字圆章：弹性过冲 + 微旋转 + 光泽扫掠
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(brandScale)
                        .graphicsLayer { rotationZ = brandRotation }
                        .alpha(brandAlpha)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text("书", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 40.sp, color = Ink)
                    // 斜向光泽扫掠
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val travel = (sweep * 2.4f - 0.7f) * size.width
                        val bandW = size.width * 0.55f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Amber.copy(alpha = 0.6f),
                                    Color.Transparent
                                ),
                                startX = travel,
                                endX = travel + bandW
                            ),
                            topLeft = Offset(0f, 0f),
                            size = size
                        )
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(brandAlpha)) {
                StaggeredText(
                    text = "白云科技",
                    visible = captionIn,
                    fontSize = 15.sp,
                    letterSpacing = 6.sp,
                    color = Color.White.copy(alpha = .92f)
                )
                Spacer(Modifier.height(8.dp))
                StaggeredText(
                    text = "6G 电信",
                    visible = titleIn,
                    fontSize = 12.sp,
                    letterSpacing = 4.sp,
                    color = Color.White.copy(alpha = .55f)
                )
                Spacer(Modifier.height(8.dp))
                StaggeredText(
                    text = "LUMINA · READER",
                    visible = sloganIn,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Medium,
                    color = Amber
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .alpha(brandAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PulseDots(
                progress = pulse,
                onColor = Amber,
                offColor = Amber.copy(alpha = 0.28f)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                T("故事正在落座"),
                color = Color.White.copy(alpha = .5f),
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

/** 逐行错峰上浮淡入：位移 + 透明度组合 */
@Composable
private fun StaggeredText(
    text: String,
    visible: Boolean,
    fontSize: TextUnit,
    color: Color,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified
) {
    val offset by animateFloatAsState(
        targetValue = if (visible) 0f else 16f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "staggerOffset"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "staggerAlpha"
    )
    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .graphicsLayer { translationY = offset }
            .alpha(alpha)
    )
}

/** 底部三点呼吸脉冲：三点错峰点亮 */
@Composable
private fun PulseDots(
    progress: Float,
    onColor: Color,
    offColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val phase = ((progress + index * 0.33f) % 1f)
            val intensity = 0.35f + (1f - abs(phase - 0.5f) * 2f) * 0.65f
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .scale(0.75f + intensity * 0.5f)
                    .clip(CircleShape)
                    .background(lerp(offColor, onColor, intensity))
            )
        }
    }
}

private const val SPLASH_HOLD_MILLIS = 1_650L

/**
 * 是否开启系统「减少动态效果」（无障碍）。
 * 读取 `Settings.Global.ANIMATOR_DURATION_SCALE`：为 0 表示用户关闭了动画。
 * 读取失败时按「未开启」处理，保证开屏始终正常播放（fail-open）。
 */
@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f
        }.getOrDefault(false)
    }
}
