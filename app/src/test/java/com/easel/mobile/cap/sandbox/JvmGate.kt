package com.easel.mobile.cap.sandbox

import com.easel.mobile.core.workspace.NioFdSys
import com.easel.mobile.core.workspace.WorkspaceFileGate

/** JVM 测试里的关口(按路径的近似;手机上是 AndroidFdSys.gate)。 */
internal val JVM_GATE = WorkspaceFileGate(NioFdSys)
