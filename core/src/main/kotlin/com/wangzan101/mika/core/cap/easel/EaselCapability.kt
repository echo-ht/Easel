package com.wangzan101.mika.core.cap.easel

import com.wangzan101.mika.core.capability.Capability
import com.wangzan101.mika.core.capability.CapabilityKey
import com.wangzan101.mika.core.capability.CapabilityStatus
import com.wangzan101.mika.core.capability.PromptEnv
import com.wangzan101.mika.core.easel.EaselContentPort
import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.ToolContext

internal const val EASEL = "easel"

/** 本机内容日历与发布日志；不需要屏幕、不需要沙箱，也不把内容发到外部。 */
internal class EaselCapability(private val port: EaselContentPort?) : Capability {
    override val id = EASEL

    override fun status(): CapabilityStatus = if (port != null) CapabilityStatus.Available
        else CapabilityStatus.Unavailable("no local content workspace")

    override fun tools(): List<AgentTool> = EaselContentGroup.tools

    override fun sections(env: PromptEnv): List<String> =
        if (env.isOpen(id) && EaselContentGroup.tools.any { it.name in env.offered }) listOf(Prompts.capability(id)) else emptyList()

    override fun onTaskStart(ctx: ToolContext) {
        if (port != null) ctx[KEY] = port
    }

    companion object {
        val KEY = CapabilityKey<EaselContentPort>(EASEL)
    }
}
