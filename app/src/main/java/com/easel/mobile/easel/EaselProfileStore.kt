package com.easel.mobile.easel

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Easel 账号画像:一个 markdown 文件,跟着每次内容任务注入模型上下文。
 * 与 Easel 的短句记忆分开,这里放定位、风格、受众、平台、偏好红线和长期经验。
 */
class EaselProfileStore(context: Context) {
    private val dir = File(context.filesDir, "easel")
    private val file = File(dir, "profile.md")

    @Synchronized fun read(): String {
        if (!file.isFile) return ""
        return runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("").take(MAX_CHARS)
    }

    @Synchronized fun write(text: String) {
        dir.mkdirs()
        val normalized = text.replace("\r\n", "\n").trim().take(MAX_CHARS)
        val tmp = File(dir, "profile.md.tmp")
        tmp.writeText(normalized.ifBlank { DEFAULT_PROFILE }, Charsets.UTF_8)
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun exists(): Boolean = file.isFile

    companion object {
        const val MAX_CHARS = 20_000

        val DEFAULT_PROFILE = """
            # 账号画像

            ## 定位
            [待补充] 这个账号是谁、提供什么价值、和别人有什么不同。

            ## 内容方向
            [待补充] 主要题材、栏目、内容支柱。

            ## 风格
            [待补充] 语气、开头方式、结构、视觉偏好、标志性元素。

            ## 受众
            [待补充] 核心人群、兴趣、痛点、互动方式。

            ## 平台
            [待补充] 主要平台、账号名、内容形式、更新节奏。

            ## 偏好与红线
            [待补充] 想做什么、不做什么、合规底线、不能碰的话题。

            ## 长期记忆
            [待补充] 已验证有效的做法、失败教训、历史表现。
        """.trimIndent()
    }
}
