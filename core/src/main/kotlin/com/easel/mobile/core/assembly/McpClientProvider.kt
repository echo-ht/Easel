package com.easel.mobile.core.assembly

import com.easel.mobile.core.cap.mcp.McpCapability
import com.easel.mobile.core.cap.mcp.McpConnection
import com.easel.mobile.core.run.RunSpec

internal class McpClientProvider(private val connection: McpConnection) : CapabilityProvider {
    override val id: String = "mcp:${connection.endpoint.serverId}"
    override fun open(spec: RunSpec) = McpCapability(connection, id)
}
