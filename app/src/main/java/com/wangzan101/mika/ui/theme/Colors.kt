package com.wangzan101.mika.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 界面用到的全部颜色。页面一律从 [MikaTheme.colors] 取,不写死色值。
 * 实色只用在强调(主按钮、选中、开关、用户消息)和状态(成功、需要你、出错);图标块一律中性。
 */
@Immutable
class MikaColors(
    /** 页面背景。 */
    val bg: Color,
    /** 卡片、分组。 */
    val surface: Color,
    /** 浅填充:次按钮、分段控件底、代码块底。 */
    val fill: Color,
    /** 文字三级:正文、次要、辅助。 */
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    /** 分隔线。 */
    val hair: Color,
    /** 卡片描边。 */
    val edge: Color,
    val accent: Color,
    val accentSoft: Color,
    /** 强调色上的文字和图标。 */
    val onAccent: Color,
    val ok: Color,
    val okSoft: Color,
    /** 「需要你」:[warn] 用于文字,[warnSolid] 用于圆点、描边。 */
    val warn: Color,
    val warnSolid: Color,
    val warnSoft: Color,
    val bad: Color,
    val badSoft: Color,
    /** 图标块的底。 */
    val neutralSoft: Color,
    /** Mika 回答的底:比页面背景深一点点的灰,和用户的蓝色气泡区分开。 */
    val answer: Color,
    /** 灰色胶囊、关着的开关。 */
    val greySoft: Color,
    /** 悬浮玻璃(输入栏、浮在内容上的按钮)的底和描边。 */
    val glass: Color,
    val glassEdge: Color,
    /** 卡片阴影;深色下卡片只靠描边,为透明。 */
    val cardShadow: Color,
    /** 悬浮元素的阴影。 */
    val floatShadow: Color,
    /** 开关的圆钮。 */
    val thumb: Color,
    /**
     * 图表里分类的颜色(例如每家服务商一种),按固定的先后取;超出的归到 [seriesOther]。
     * 只给图形上色,文字一律用 ink 三级。
     */
    val series: List<Color>,
    val seriesOther: Color,
    /** 图表的网格线。 */
    val grid: Color,
    val isDark: Boolean,
)

val LightColors = MikaColors(
    bg = Color(0xFFF4F1EA),
    surface = Color(0xFFFFFDF8),
    fill = Color(0x0F17211E),
    ink = Color(0xFF17211E),
    ink2 = Color(0xFF4F5B56),
    ink3 = Color(0xFF89938E),
    hair = Color(0x1417211E),
    edge = Color(0x1217211E),
    accent = Color(0xFF0F766E),
    accentSoft = Color(0x1A0F766E),
    onAccent = Color(0xFFFFFFFF),
    ok = Color(0xFF15803D),
    okSoft = Color(0x1F15803D),
    warn = Color(0xFFB45309),
    warnSolid = Color(0xFFF59E0B),
    warnSoft = Color(0x1FF59E0B),
    bad = Color(0xFFDC2626),
    badSoft = Color(0x1ADC2626),
    neutralSoft = Color(0x0C17211E),
    answer = Color(0x0A17211E),
    greySoft = Color(0x1F6B7280),
    glass = Color(0xD9FFFDF8),
    glassEdge = Color(0x1417211E),
    cardShadow = Color(0xFF000000),
    floatShadow = Color(0xFF000000),
    thumb = Color(0xFFFFFFFF),
    series = listOf(Color(0xFF0F766E), Color(0xFFC2410C), Color(0xFFB7791F)),
    seriesOther = Color(0xFFB6B0A5),
    grid = Color(0x1017211E),
    isDark = false,
)

val DarkColors = MikaColors(
    bg = Color(0xFF0D1412),
    surface = Color(0xFF17201D),
    fill = Color(0x12FFFFFF),
    ink = Color(0xFFF0F5F2),
    ink2 = Color(0xFFA7B4AE),
    ink3 = Color(0xFF6E7C76),
    hair = Color(0x1AFFFFFF),
    edge = Color(0x14FFFFFF),
    accent = Color(0xFF4FD1B5),
    accentSoft = Color(0x294FD1B5),
    onAccent = Color(0xFF062D26),
    ok = Color(0xFF5BD68A),
    okSoft = Color(0x245BD68A),
    warn = Color(0xFFF5B454),
    warnSolid = Color(0xFFF59E0B),
    warnSoft = Color(0x29F59E0B),
    bad = Color(0xFFFF7A6E),
    badSoft = Color(0x24FF7A6E),
    neutralSoft = Color(0x12FFFFFF),
    answer = Color(0x0DFFFFFF),
    greySoft = Color(0x386B7280),
    glass = Color(0xD917201D),
    glassEdge = Color(0x14FFFFFF),
    cardShadow = Color.Transparent,
    floatShadow = Color(0xFF000000),
    thumb = Color(0xFFFFFFFF),
    series = listOf(Color(0xFF4FD1B5), Color(0xFFFF8A5B), Color(0xFFE7B75B)),
    seriesOther = Color(0xFF5E6B65),
    grid = Color(0x14FFFFFF),
    isDark = true,
)

/** 没有包在 [MikaTheme] 里时(比如界面测试)按浅色。 */
val LocalMikaColors = staticCompositionLocalOf { LightColors }
