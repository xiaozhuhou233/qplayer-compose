/**
 * ClaudeSpinIcon.kt —— Jetpack Compose 版"旋转 + 微小形变"图标
 *
 * 对应 claude-旋转图标.html 的 Compose 实现:
 *   外层 graphicsLayer:绕中心匀速连续旋转(4s 一圈,LinearEasing)
 *   内层 graphicsLayer:微小形变 —— scaleX/scaleY 反相呼吸(±5%)+ 轻微歪摆(±1.5°),
 *   与旋转周期错开,产生"果冻自转"的 Q 弹感。
 * 分成内外两层是为了和 CSS 版一致:形变发生在图标自身坐标系里,再被整体旋转。
 *
 * 使用步骤:
 *  1. 准备图标:下载 https://unpkg.com/@lobehub/icons-static-svg@latest/icons/claude-color.svg,
 *     在 Android Studio 中右键 res/drawable → New → Vector Asset → Local file 选择该 SVG,
 *     命名为 ic_claude_color(复杂 SVG 转换失败时,见文件末尾的 Coil 方案)。
 *  2. 把本文件复制进 app 模块,package 改成你自己的包名。
 *  3. 无需额外依赖:动画 API 来自 androidx.compose.animation(Compose BOM 自带)。
 *  4. 在任意 Composable 中调用:
 *     SpinningDeformIcon(
 *         painter = painterResource(R.drawable.ic_claude_color),
 *         contentDescription = "Claude",
 *     )
 */
package dev.t1m3.qplayer.android.md3eui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
internal fun SpinningDeformIcon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    /** 一圈用时(毫秒),对应 CSS 的 4s */
    spinDurationMillis: Int = 4_000,
    /** 形变周期(毫秒),对应 CSS 的 1.6s */
    deformDurationMillis: Int = 1_600,
    /** 最大形变幅度:scaleX/scaleY 各自偏离 1 的幅度,±0.05 = ±5% */
    maxDeform: Float = 0.05f,
    /** 轻微歪摆角度,近似 CSS 版 skewX ±1.5° 的俏皮感 */
    maxWobbleDegrees: Float = 1.5f,
) {
    if (!LocalMd3eRenderingActive.current || LocalMd3eMotionActive.current) {
        Image(painter, contentDescription, modifier.size(size))
        return
    }
    val lowSpec = Md3eLowSpecMode.current || LocalMd3eReducedEffects.current
    val transition = rememberInfiniteTransition(label = "claude-spin")

    // 外层:0 → 360 匀速旋转,永不停止
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = spinDurationMillis, easing = LinearEasing),
        ),
        label = "angle",
    )

    // 内层:形变相位 0..1 来回摆动(RepeatMode.Reverse 等价于 CSS 的 ease-in-out 往复)
    val deform = if (!lowSpec) transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = deformDurationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "deform",
    ) else null

    // scaleX / scaleY 反相呼吸(挤压一边时另一边拉伸,近似体积守恒,最"Q 弹");
    // sin(2πt) 在 t=0 与 t=1 处同为 0,Reverse 往返时无跳变。
    // Read animation state only in graphicsLayer so frames do not recompose the icon.

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = angle.value // 外层:绕中心连续旋转(transformOrigin 默认就是中心)
            },
    ) {
        Image(
            painter = painter,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val phase = (deform?.value ?: 0f) * 2.0 * Math.PI
                    val wobble = maxDeform * sin(phase).toFloat()
                    scaleX = 1f + wobble
                    scaleY = 1f - wobble
                    rotationZ = maxWobbleDegrees * sin(phase + Math.PI / 2).toFloat()
                },
        )
    }
}
