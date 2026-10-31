package com.huanlab.engine.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * 本地动效方案。
 *
 * 说明：material3 1.4.0（稳定版）中的 MotionScheme / ExperimentalMaterial3ExpressiveApi
 * 仍被标记为 internal，1.5.0-beta02 又要求 Kotlin 2.2 元数据，属于大范围升级 + beta 依赖，
 * 因此这里用稳定公开 API 复刻 Material 3 Expressive 的动效特征：
 *  - 空间动画：低阻尼弹簧，带轻微回弹；
 *  - 效果动画：短时长快速缓动，只影响颜色/透明度。
 * UI 只依赖这层抽象，未来升级到公开 MotionScheme 时可整体替换。
 */
object YoeMotion {

    fun <T> spatial(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> effects(): FiniteAnimationSpec<T> = tween(
        durationMillis = 220,
        easing = FastOutSlowInEasing,
    )
}
