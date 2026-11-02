package com.easel.mobile

import com.easel.mobile.core.action.ActionExecutor
import com.easel.mobile.core.action.ActionRecorder
import com.easel.mobile.core.action.Origin
import com.easel.mobile.core.action.execution
import com.easel.mobile.core.guard.OperationGuards
import com.easel.mobile.core.model.Part
import com.easel.mobile.core.record.RunSink
import com.easel.mobile.core.tool.ToolDispatcher
import com.easel.mobile.core.tool.ToolExecution

/** 设备测试里直接做一个工具调用:和产品一样经执行入口(不配确认关卡,不等锁屏)。 */
suspend fun ToolDispatcher.runAction(call: Part.ToolCall): ToolExecution =
    ActionExecutor(this, OperationGuards(), null, gate = {}, recorder = ActionRecorder(RunSink.NONE)).run(call, Origin.Model(0)).execution()
