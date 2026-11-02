package com.wangzan101.mika.core.cap.easel

import com.wangzan101.mika.core.capability.Capability
import com.wangzan101.mika.core.capability.CapabilityKey
import com.wangzan101.mika.core.capability.CapabilityStatus
import com.wangzan101.mika.core.capability.PromptEnv
import com.wangzan101.mika.core.easel.EaselCardRuntime
import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.ToolContext

internal const val EASEL_CARDS = "easel-cards"

/** 手机原生卡片渲染；HTML 和 PNG 都在本任务工作区里，渲染不联网、不执行脚本。 */
internal class EaselCardCapability(private val runtime: EaselCardRuntime?) : Capability {
    override val id = EASEL_CARDS

    override fun status(): CapabilityStatus = if (runtime != null) CapabilityStatus.Available
        else CapabilityStatus.Unavailable("no local card renderer")

    override fun tools(): List<AgentTool> = EaselCardGroup.tools

    override fun sections(env: PromptEnv): List<String> =
        if (env.isOpen(id) && EaselCardGroup.tools.any { it.name in env.offered }) listOf(Prompts.capability(id)) else emptyList()

    override fun onTaskStart(ctx: ToolContext) {
        if (runtime != null) ctx[KEY] = runtime
    }

    companion object {
        val KEY = CapabilityKey<EaselCardRuntime>(EASEL_CARDS)
    }
}
