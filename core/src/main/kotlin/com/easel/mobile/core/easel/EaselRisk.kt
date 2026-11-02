package com.easel.mobile.core.easel

enum class RiskLevel { LOW, MEDIUM, HIGH }

data class RiskFinding(
    val level: RiskLevel,
    val code: String,
    val evidence: String,
    val suggestion: String,
)

data class RiskReport(
    val level: RiskLevel,
    val platform: String,
    val findings: List<RiskFinding>,
) {
    val blockCount: Int get() = findings.count { it.level == RiskLevel.HIGH }
    val warnCount: Int get() = findings.count { it.level == RiskLevel.MEDIUM }
}

/**
 * 发布前的确定性文本风控。它不替代平台审核，也不声称查重；
 * 只拦明显泄露、隐私、绝对化承诺和常见金融/医疗高风险措辞。
 */
object EaselRiskScanner {
    private data class Rule(val code: String, val level: RiskLevel, val regex: Regex, val suggestion: String)

    private val rules = listOf(
        Rule(
            "secret",
            RiskLevel.HIGH,
            Regex("""(?i)(\bsk-[a-z0-9_-]{16,}\b|\bghp_[a-z0-9]{20,}\b|\bxox[baprs]-[a-z0-9-]{10,}\b|(?:api[_-]?key|access[_-]?token|secret)\s*[:=]\s*["']?[a-z0-9._-]{12,})"""),
            "删除密钥或令牌，改用环境变量或本地设置，不要把凭据写进正文或截图。",
        ),
        Rule(
            "private_network",
            RiskLevel.HIGH,
            Regex("""(?i)\b(?:https?://)?(?:localhost|127\.0\.0\.1|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})(?::\d+)?\b"""),
            "删掉内网地址、代理地址或本机地址；需要引用资料时换成公开链接。",
        ),
        Rule(
            "private_path",
            RiskLevel.HIGH,
            Regex("""(?i)(/data/user/0/|/storage/emulated/0/Android/data/|C:\\Users\\|/Users/[^/\s]+/|~/\.)"""),
            "删掉本机私有路径，截图里也不要露出文件管理器路径。",
        ),
        Rule(
            "identity_number",
            RiskLevel.HIGH,
            Regex("""(?<!\d)[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?!\d)"""),
            "身份证号属于高敏感信息，发布前删除或打码。",
        ),
        Rule(
            "phone_number",
            RiskLevel.MEDIUM,
            Regex("""(?<!\d)1[3-9]\d{9}(?!\d)"""),
            "手机号可能涉及隐私；确认已获得本人同意，或改成脱敏写法。",
        ),
        Rule(
            "absolute_claim",
            RiskLevel.MEDIUM,
            Regex("""(?i)(全网第一|行业第一|销量第一|第一品牌|全网最低|最好|最强|最高|最有效|最安全|绝对|百分百|100%有效|零风险|包治|治愈|永久有效|保证收益|稳赚|保本|无副作用|国家级|顶级)"""),
            "绝对化或保证性说法容易被平台判定为夸大宣传；改成可核实、有限定条件的事实。",
        ),
        Rule(
            "medical_finance",
            RiskLevel.MEDIUM,
            Regex("""(?i)(治疗|降血糖|降血压|抗癌|减肥药|药到病除|投资收益|年化收益|荐股|炒币|贷款包过)"""),
            "医疗、金融和功效类内容风险较高；补充资质、证据和适用范围，避免承诺结果。",
        ),
    )

    fun scan(text: String, platform: String = ""): RiskReport {
        val findings = rules.flatMap { rule ->
            rule.regex.findAll(text).take(3).map { match ->
                RiskFinding(rule.level, rule.code, mask(match.value), rule.suggestion)
            }
        }
        val level = when {
            findings.any { it.level == RiskLevel.HIGH } -> RiskLevel.HIGH
            findings.any { it.level == RiskLevel.MEDIUM } -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        return RiskReport(level, platform.trim(), findings.distinctBy { it.code to it.evidence })
    }

    private fun mask(value: String): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= 8) return clean
        val head = clean.take(4)
        val tail = clean.takeLast(2)
        return "$head•••$tail"
    }
}
