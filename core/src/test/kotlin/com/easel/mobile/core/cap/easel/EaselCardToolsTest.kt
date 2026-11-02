package com.easel.mobile.core.cap.easel

import com.easel.mobile.core.assembly.toolDispatcher
import com.easel.mobile.core.cap.sandbox.FakeSandbox
import com.easel.mobile.core.easel.EaselCardAudit
import com.easel.mobile.core.easel.EaselCardRender
import com.easel.mobile.core.easel.EaselCardRenderer
import com.easel.mobile.core.model.Part
import com.easel.mobile.core.tool.FakeScreen
import com.easel.mobile.core.tool.execute
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EaselCardToolsTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)
    private val passAudit = EaselCardAudit(true, 1.0, 1.0, 0.0, 0.0, listOf(1.0, 1.0, 1.0, 1.0), emptyList())

    private class FakeRenderer(
        private val result: EaselCardRender,
    ) : EaselCardRenderer {
        var calls = 0
        var html = ""
        var width = 0
        var height = 0
        var scale = 0
        var waitMs = 0L

        override suspend fun render(html: String, width: Int, height: Int, scale: Int, waitMs: Long): EaselCardRender {
            calls++
            this.html = html
            this.width = width
            this.height = height
            this.scale = scale
            this.waitMs = waitMs
            return result
        }
    }

    private fun setUp(renderer: FakeRenderer = FakeRenderer(EaselCardRender(png, passAudit))): Pair<FakeSandbox, FakeRenderer> {
        val sandbox = FakeSandbox()
        val dir = File(sandbox.workspaceDir("w"), "cards").apply { mkdirs() }
        File(dir, "quote.html").writeText("<html><body><h1>Hello</h1></body></html>")
        return sandbox to renderer
    }

    private suspend fun call(sandbox: FakeSandbox, renderer: EaselCardRenderer, args: String) =
        toolDispatcher(FakeScreen(), sandbox = sandbox, workspace = { "w" }, easelCards = renderer)
            .execute(Part.ToolCall("c", "render_card", args))

    @Test fun toolOnlyExistsWithBothSandboxAndRenderer() {
        assertFalse(toolDispatcher(FakeScreen()).definitions().any { it.name == "render_card" })
        assertFalse(toolDispatcher(FakeScreen(), sandbox = FakeSandbox(), workspace = { "w" })
            .definitions().any { it.name == "render_card" })
        assertTrue(toolDispatcher(FakeScreen(), sandbox = FakeSandbox(), workspace = { "w" },
            easelCards = FakeRenderer(EaselCardRender(png, passAudit)))
            .definitions().any { it.name == "render_card" })
    }

    @Test fun rendersHtmlFromTheWorkspaceIntoTheRequestedPng() = runTest {
        val (sandbox, renderer) = setUp()
        val ok = call(sandbox, renderer,
            """{"htmlPath":"/workspace/cards/quote.html","outPath":"/workspace/cards/quote.png","width":1200,"height":900,"scale":2,"waitMs":300}""")
        assertNull(ok.content["error"])
        assertEquals("/workspace/cards/quote.png", ok.content["path"]!!.jsonPrimitive.content)
        assertEquals("2400", ok.content["outputWidth"]!!.jsonPrimitive.content)
        assertEquals("1800", ok.content["outputHeight"]!!.jsonPrimitive.content)
        assertEquals(true, ok.content["audit"]!!.jsonObject["pass"]!!.jsonPrimitive.content.toBoolean())
        assertTrue(renderer.html.contains("<h1>Hello</h1>"))
        assertEquals(1200, renderer.width)
        assertEquals(900, renderer.height)
        assertEquals(2, renderer.scale)
        assertEquals(300L, renderer.waitMs)

        val written = File(sandbox.workspaceDir("w"), "cards/quote.png")
        assertTrue(written.isFile)
        assertArrayEquals(png, written.readBytes())
        assertEquals(11, ok.content["bytes"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun existingPngNeedsExplicitOverwrite() = runTest {
        val (sandbox, renderer) = setUp()
        val body = """{"htmlPath":"/workspace/cards/quote.html","outPath":"/workspace/cards/quote.png"}"""
        assertTrue(call(sandbox, renderer, body).content["ok"]!!.jsonPrimitive.content.toBoolean())
        val refused = call(sandbox, renderer, body)
        assertEquals("file_exists", refused.content["error"]?.jsonPrimitive?.content)
        assertEquals(11, File(sandbox.workspaceDir("w"), "cards/quote.png").readBytes().size)

        val replaced = call(sandbox, renderer,
            """{"htmlPath":"/workspace/cards/quote.html","outPath":"/workspace/cards/quote.png","overwrite":true}""")
        assertTrue(replaced.content["ok"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(3, renderer.calls)
    }

    @Test fun rejectsPathsOutsideTheWorkspaceAndNonPngOutput() = runTest {
        val (sandbox, renderer) = setUp()
        val outside = call(sandbox, renderer,
            """{"htmlPath":"/etc/passwd","outPath":"/workspace/cards/quote.png"}""")
        assertEquals("path_outside_workspace", outside.content["error"]?.jsonPrimitive?.content)

        val notPng = call(sandbox, renderer,
            """{"htmlPath":"/workspace/cards/quote.html","outPath":"/workspace/cards/quote.jpg"}""")
        assertEquals("invalid_arguments", notPng.content["error"]?.jsonPrimitive?.content)
    }

    @Test fun nonPngRendererOutputIsRejected() = runTest {
        val (sandbox, _) = setUp()
        val renderer = FakeRenderer(EaselCardRender(byteArrayOf(1, 2, 3), passAudit))
        val result = call(sandbox, renderer,
            """{"htmlPath":"/workspace/cards/quote.html","outPath":"/workspace/cards/quote.png"}""")
        assertEquals("render_failed", result.content["error"]?.jsonPrimitive?.content)
        assertFalse(File(sandbox.workspaceDir("w"), "cards/quote.png").exists())
    }
}
