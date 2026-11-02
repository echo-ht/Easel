package com.wangzan101.mika.core.cap.easel

import com.wangzan101.mika.core.easel.EaselCardAudit
import com.wangzan101.mika.core.easel.EaselCardRuntime
import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.ArgError
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolDef
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.tool.bool
import com.wangzan101.mika.core.tool.fail
import com.wangzan101.mika.core.tool.int
import com.wangzan101.mika.core.tool.ok
import com.wangzan101.mika.core.tool.str
import com.wangzan101.mika.core.workspace.SandboxError
import com.wangzan101.mika.core.workspace.SandboxPaths
import com.wangzan101.mika.core.workspace.WorkspaceFileGate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val MAX_HTML_BYTES = 2 * 1024 * 1024
private const val MAX_PNG_BYTES = 48 * 1024 * 1024
private const val MAX_OUTPUT_SIDE = 4096
private const val MAX_OUTPUT_PIXELS = 12_000_000L

internal object RenderCard : AgentTool {
    override val verb = Verb.RenderCard
    override val risk = Risk.Write
    override val offScreen = true
    override val capability = EASEL_CARDS
    override val def = ToolDef(
        "render_card",
        Prompts.tool("render_card"),
        mapOf(
            "htmlPath" to str("HTML file inside /workspace, e.g. /workspace/cards/quote.html. It must be self-contained: inline CSS, system fonts, inline SVG and data: images only."),
            "outPath" to str("PNG file to write inside /workspace, e.g. /workspace/cards/quote.png. The extension must be .png."),
            "width" to int("CSS viewport width in pixels, 200-4096. Default 1080.", 200, MAX_OUTPUT_SIDE),
            "height" to int("CSS viewport height in pixels, 200-4096. Default 1440.", 200, MAX_OUTPUT_SIDE),
            "scale" to int("Device pixel ratio, 1-3. Output is width*scale by height*scale. Default 2.", 1, 3),
            "waitMs" to int("Extra wait after the page reports loaded, 0-5000 ms. Default 250.", 0, 5000),
            "overwrite" to bool("Set true to replace an existing PNG. Default false."),
        ),
        listOf("htmlPath", "outPath"),
    )

    override fun available(ctx: ToolContext): Boolean = ctx.easelCardRuntime != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val runtime = ctx.easelCardRuntime ?: return fail("render_unavailable", "The phone's card renderer is not available.", recoverable = false)
        val htmlPath = workspacePath(a.str("htmlPath", max = 4096)!!, write = false)
            ?: return fail("path_outside_workspace", "htmlPath must be inside /workspace.", recoverable = false)
        val outPath = workspacePath(a.str("outPath", max = 4096)!!, write = true)
            ?: return fail("path_outside_workspace", "outPath must be inside /workspace.", recoverable = false)
        if (!outPath.endsWith(".png", ignoreCase = true)) throw ArgError("outPath", "must end with .png")
        if (htmlPath == outPath) throw ArgError("outPath", "must be different from htmlPath")

        val width = a.int("width", 200, MAX_OUTPUT_SIDE) ?: 1080
        val height = a.int("height", 200, MAX_OUTPUT_SIDE) ?: 1440
        val scale = a.int("scale", 1, 3) ?: 2
        val waitMs = (a.int("waitMs", 0, 5000) ?: 250).toLong()
        val overwrite = a.bool("overwrite", false)
        val outputWidth = width * scale
        val outputHeight = height * scale
        if (outputWidth > MAX_OUTPUT_SIDE || outputHeight > MAX_OUTPUT_SIDE || outputWidth.toLong() * outputHeight > MAX_OUTPUT_PIXELS) {
            throw ArgError("scale", "output ${outputWidth}x$outputHeight is too large; keep each side <= $MAX_OUTPUT_SIDE and the image <= ${MAX_OUTPUT_PIXELS / 1_000_000} MP")
        }

        val workspace = runtime.workspace()
        val html = try {
            readHtml(runtime, workspace, htmlPath)
        } catch (e: SandboxError) {
            return refused(htmlPath, e)
        }
        val rendered = try {
            runtime.renderer.render(html, width, height, scale, waitMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return fail("render_failed", e.message ?: "The card renderer failed.", recoverable = true)
        }
        if (rendered.png.size > MAX_PNG_BYTES) {
            return fail("file_too_large", "The rendered PNG is ${rendered.png.size / (1024 * 1024)} MB; at most ${MAX_PNG_BYTES / (1024 * 1024)} MB is allowed.", recoverable = true)
        }
        if (!isPng(rendered.png)) return fail("render_failed", "The renderer did not return a PNG.", recoverable = false)

        try {
            writePng(runtime, workspace, outPath, rendered.png, overwrite)
        } catch (e: SandboxError) {
            if (e.code == WorkspaceFileGate.FILE_EXISTS) {
                return fail("file_exists", "$outPath already exists. Choose another outPath or set overwrite=true.", recoverable = true)
            }
            return refused(outPath, e)
        }
        return ok {
            put("path", outPath)
            put("width", width)
            put("height", height)
            put("scale", scale)
            put("outputWidth", outputWidth)
            put("outputHeight", outputHeight)
            put("bytes", rendered.png.size)
            put("audit", auditJson(rendered.audit))
            if (!rendered.audit.pass) put("hint", "Layout audit failed. Adjust the HTML and render again before sharing the PNG; do not deliver it silently.")
        }
    }

    private fun workspacePath(raw: String, write: Boolean): String? {
        val path = SandboxPaths.normalize(raw) ?: return null
        if (!path.startsWith("${SandboxPaths.WORKSPACE}/")) return null
        if (write && !SandboxPaths.isWritable(path)) return null
        return path
    }

    private fun readHtml(runtime: EaselCardRuntime, workspace: String, path: String): String {
        val bytes = runtime.read(workspace, path, MAX_HTML_BYTES)
        if (bytes.isEmpty()) throw SandboxError("invalid_arguments", "$path is empty.", recoverable = true)
        return String(bytes, Charsets.UTF_8)
    }

    private fun writePng(runtime: EaselCardRuntime, workspace: String, path: String, bytes: ByteArray, overwrite: Boolean) {
        runtime.write(workspace, path, bytes, overwrite)
    }

    private fun isPng(bytes: ByteArray): Boolean = bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() && bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte()

    private fun auditJson(audit: EaselCardAudit) = buildJsonObject {
        put("pass", audit.pass)
        put("fill", audit.fill)
        put("span", audit.span)
        put("bottomDead", audit.bottomDead)
        put("maxGap", audit.maxGap)
        put("bands", JsonArray(audit.bands.map(::JsonPrimitive)))
        put("problems", JsonArray(audit.problems.map(::JsonPrimitive)))
    }

    private fun refused(path: String, e: SandboxError): ToolExecution = when (e.code) {
        WorkspaceFileGate.FILE_NOT_FOUND -> fail(e.code, "$path does not exist.", recoverable = true)
        WorkspaceFileGate.NOT_A_FILE -> fail(e.code, "$path is not a regular file.", recoverable = true)
        WorkspaceFileGate.OUTSIDE -> fail(e.code, "$path must stay inside /workspace.", recoverable = false)
        else -> fail(e.code, "$path: ${e.message}", e.recoverable)
    }
}

internal object EaselCardGroup {
    val tools: List<AgentTool> = listOf(RenderCard)
}

internal val ToolContext.easelCardRuntime: EaselCardRuntime?
    get() = this[EaselCardCapability.KEY]
