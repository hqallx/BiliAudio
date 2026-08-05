package com.biliaudio.ui.theme

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 全局动效 token：统一时长与缓动曲线，保证全应用动效细腻、克制、一致。
 *
 * 参考 Material 3 motion 规范：
 * - Small/Medium 组件用短时长（120~200ms）。
 * - 进入用 [LinearOutSlowInEasing]（先快后慢），退出用 [FastOutLinearInEasing]（先慢后快），
 *   标准（按压/颜色）用 [FastOutSlowInEasing]（两端慢中间快）。
 * - 所有曲线基于 Emphasized 系，避免线性带来的机械感。
 */
object Motion {
    /** 极短：按压反馈、图标切换。 */
    const val DurationShort = 120
    /** 中等：Tab 内容、状态切换、MiniPlayer 显隐。 */
    const val DurationMedium = 220
    /** 较长：页面转场、全屏播放器展开。 */
    const val DurationLong = 300

    /** 标准：按压缩放、颜色过渡。 */
    val EasingStandard = FastOutSlowInEasing
    /** 进入：元素出现。 */
    val EasingEmphasizedDecel = LinearOutSlowInEasing
    /** 退出：元素消失。 */
    val EasingEmphasizedAccel = FastOutLinearInEasing

    /** 按压时的缩放比例（克制，避免过度形变）。 */
    const val PressedScale = 0.97f
}

/**
 * 按压弹性反馈：按下时轻微缩小，松开回弹，配合默认 ripple。
 *
 * 用于列表卡片/行等可点击元素，提供细腻的触感反馈而不喧宾夺主。
 * 缩放绕中心点（[graphicsLayer] 默认 transformOrigin = Center）。
 *
 * 用法：替换 `Modifier.clickable { onClick() }` 为 `Modifier.pressBounce { onClick() }`。
 *
 * @param pressedScale 按下时缩放到的比例，默认 [Motion.PressedScale]。
 * @param enabled 是否启用反馈（用于禁用态点击）。
 * @param onLongClick 长按回调，非 null 时启用长按（用于删除等操作）。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
fun Modifier.pressBounce(
    pressedScale: Float = Motion.PressedScale,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = Motion.DurationShort,
            easing = Motion.EasingStandard
        ),
        label = "pressBounceScale"
    )
    val modifier = this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    if (onLongClick != null) {
        modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = rememberRipple(),
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        modifier.clickable(
            interactionSource = interactionSource,
            indication = rememberRipple(),
            enabled = enabled,
            onClick = onClick
        )
    }
}
