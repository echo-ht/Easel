---
name: content-workflow
display: 内容工作流
description: 从热点或想法走到成稿、发布和复盘的社媒内容全流程，按发现、策划、制作、发布、归因五层推进。
aliases: ["做内容", "发笔记", "写文案", "做视频", "选题", "内容策划", "社媒运营"]
apps: []
sites: []
terms: ["内容", "选题", "文案", "笔记", "发布", "复盘", "账号", "热点"]
---
## 入口

先判断用户这句话属于哪一层，只做需要的层，不要每次把五层全跑一遍：

- 发现：找热点、竞品、受众问题、内容缺口。
- 策划：定位、选题、角度、结构、标题、排期、平台适配。
- 制作：写文案、做图、配音、剪辑、生成完整草稿或成品文件。
- 发布：检查、适配、发布到平台并核实结果。
- 归因：读数据、评论和表现，把有效经验写回画像。

跨两层以上、或明显多步骤的任务，先用三到五行说明计划，再开始执行。单层任务直接做。

## 步骤

1. 读账号画像。画像在「内容工作台」里，包含定位、风格、受众、平台、偏好和红线。用户当前的话优先于画像。
   需要跨天安排、记住选题或回看已发布内容时，读本机内容日历和发布记录：`content_plan_list`、`content_publish_log_list`。
   手机上「内容工作台 → 五层工作流」会显示每一层当前的数量；「发布中心」列出待发布队列，「素材库」管理导入和导出的文件。
2. 用 `skill_search` 找最接近的技能；有就 `skill_read` 后按它的流程做。没有精确匹配就用最接近的，不要凭记忆裸做。
3. 开工前确认：平台、账号、主题、内容形式、目标受众、规格、风格、红线、输出到哪里。能从画像或历史产物推断的就不要重复问。
   用户要排期或跨会话推进时，用 `content_plan_add` 记下计划和阶段；只有用户明确要排期时才写日期。
4. 制作层把中间素材放 `/workspace` 的子目录，成品放清楚命名的文件；需要用户复用的素材用 `share_file` 导出到素材库。交付前检查文件存在、非空、时长/分辨率/画幅/字数符合要求。
   做金句卡、数据卡或其他 HTML 卡片时，读 `card-design` 和对应的卡片技能，写自包含 HTML，再用 `render_card` 出 PNG；按返回的版式审计改到通过。
   视频和音频处理可读 `video-script`、`auto-subtitle`、`video-reframe`、`audio-editing`、`image-editing`；沙箱自带 ffmpeg 和 Pillow，命令都在 `/workspace` 里跑。
5. 成品用 `share_file` 交给用户；在手机上需要发布时，转到发布技能并用手机自动化完成。
6. 发布后在 App 或页面里核实结果。成功或失败都用 `content_publish_log_add` 记下发生的事实；不要提前把计划写成已发布。

## 常用技能分工

- 文案：`copywriting`、`text-polisher`、`hook-title`、`post-formatter`。
- 小红书：`xhs-note`、`xhs-analysis`、`xhs-comment-reply`、`xhs-publish`。
- 其他平台：`douyin-publish`、`kuaishou-publish`、`bilibili-publish`、`zhihu-publish`、`wechat-publish`、`cross-platform-publish`。
- 策划和复盘：`content-strategy`、`trending-topics`、`competitor-analysis`、`content-gap-analysis`、`publish-scheduler`、`data-tracker`、`postmortem`。
- 制作：`card-design`、`card-quote`、`video-script`、`auto-subtitle`、`video-reframe`、`audio-editing`、`image-editing`、`asset-manager`。

## 容易出错的地方

- 只给计划、空壳文件或提示词，不算完成；必须有真实产物。
- 不要编造热点、数据、互动量或平台规则；读不到就说读不到。
- 图片和视频不要混用发布；每个平台的字数、画幅、时长限制不同。
- 不要在公开文案里留下 API Key、内部地址、代理、私有路径、模型名或工具痕迹。
- 付费生图、生视频、音乐、声音克隆先说明范围和费用，等用户确认再发请求。

## 怎样算办成(核实证据)

文本：文件存在且字数、结构、标题符合要求。图片：文件能打开，尺寸和画幅正确。视频：能播放，时长、分辨率、画幅正确。发布：在 App 或页面里看到已发布的内容，而不是只看到「发布」按钮被点过。

## 什么时候停下交给用户

需要登录、验证码、付款、实名认证，或平台风控要求人工处理时，停下来请用户接手；用户处理完再继续核实。
