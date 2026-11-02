package com.wangzan101.mika.core.easel

import kotlin.math.round

/**
 * 卡片成图的确定性版式审计。输入是已经按同一比例缩过的灰度行，阈值与原 Easel
 * 的版式审计一致：内容纵向跨度不足、底部死空白或中段大空洞都会判失败。
 */
data class EaselCardAudit(
    val pass: Boolean,
    val fill: Double,
    val span: Double,
    val bottomDead: Double,
    val maxGap: Double,
    val bands: List<Double>,
    val problems: List<String>,
)

object EaselLayoutAudit {
    const val SPAN_MIN = 0.80
    const val DEAD_BAND_MAX = 0.15
    const val EDGE_TOL = 12
    const val ROW_EDGE_FRAC = 0.006

    /** [rows] 是同一宽度的灰度行，0 为黑、255 为白；只按横向边缘判断内容，纯色和竖向渐变算空。 */
    fun analyze(rows: Array<IntArray>): EaselCardAudit {
        require(rows.isNotEmpty()) { "rows must not be empty" }
        val width = rows.first().size
        require(width > 0) { "rows must not be empty" }
        require(rows.all { it.size == width }) { "every row must have the same width" }
        val height = rows.size
        val has = BooleanArray(height)
        for (y in 0 until height) {
            val row = rows[y]
            var edges = 0
            for (x in 1 until width) {
                if (kotlin.math.abs(row[x] - row[x - 1]) > EDGE_TOL) edges++
            }
            has[y] = width > 1 && edges.toDouble() / (width - 1) > ROW_EDGE_FRAC
        }

        val contentRows = has.count { it }
        val fill = contentRows.toDouble() / height
        val first = has.indexOfFirst { it }
        val last = has.indexOfLast { it }
        val span = if (contentRows == 0) 0.0 else (last - first + 1).toDouble() / height

        var bottomEmpty = 0
        for (y in height - 1 downTo 0) {
            if (has[y]) break
            bottomEmpty++
        }
        val bottomDead = bottomEmpty.toDouble() / height

        var maxGap = 0
        var current = 0
        if (contentRows > 0) {
            for (y in first..last) {
                if (has[y]) current = 0 else maxGap = maxOf(maxGap, ++current)
            }
        }
        val maxGapFraction = maxGap.toDouble() / height

        val bands = (0 until 4).map { band ->
            val start = band * height / 4
            val end = (band + 1) * height / 4
            if (end <= start) 0.0 else has.copyOfRange(start, end).count { it }.toDouble() / (end - start)
        }

        val problems = buildList {
            if (bottomDead > DEAD_BAND_MAX) add("Bottom dead space is ${percent(bottomDead)} (> 15%); the frame is underfilled.")
            if (maxGapFraction > DEAD_BAND_MAX) add("Largest inner gap is ${percent(maxGapFraction)} (> 15%); fill the hole or change the layout.")
            if (span < SPAN_MIN) add("Vertical content span is ${percent(span)} (< 80%); content does not reach the top and bottom margins.")
            if (bands[3] < 0.10 && bands[0] > 0.30) add("Top-heavy: the bottom quarter is nearly empty.")
        }
        return EaselCardAudit(
            pass = problems.isEmpty(),
            fill = rounded(fill),
            span = rounded(span),
            bottomDead = rounded(bottomDead),
            maxGap = rounded(maxGapFraction),
            bands = bands.map(::rounded),
            problems = problems,
        )
    }

    private fun rounded(value: Double): Double = round(value * 1000) / 1000
    private fun percent(value: Double): String = "${round(value * 100).toInt()}%"
}

/** 渲染器输出的一帧 PNG 和它自己的版式审计。 */
data class EaselCardRender(val png: ByteArray, val audit: EaselCardAudit)

/**
 * HTML -> PNG 的手机侧实现。输入已经是自包含 HTML；实现必须禁用脚本、网络和本地文件访问，
 * 只让内联样式、Android 系统字体、内联 SVG 和 data: 图片生效。
 */
interface EaselCardRenderer {
    suspend fun render(html: String, width: Int, height: Int, scale: Int, waitMs: Long): EaselCardRender
}

/**
 * 一个任务里的卡片运行环境。文件读写由装配层接进来，核心层不直接认识沙箱能力；
 * [read] 和 [write] 抛出的错误沿用 workspace 包的 SandboxError，工具按错误码回给模型。
 */
internal class EaselCardRuntime(
    val workspace: () -> String,
    val read: (workspace: String, path: String, maxBytes: Int) -> ByteArray,
    val write: (workspace: String, path: String, bytes: ByteArray, overwrite: Boolean) -> Unit,
    val renderer: EaselCardRenderer,
)
