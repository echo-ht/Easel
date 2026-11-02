# 横版金句卡示例

文案：

> Markdown 是给作者爽的，HTML 是给读者爽的。
>
> — 一位把内部文档换成 HTML 的工程师，2026

## 可改写的自包含 HTML 结构

```html
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<style>
  * { box-sizing: border-box; }
  html, body { margin: 0; width: 1600px; height: 900px; }
  body { background: #15140f; color: #fafaf7; font-family: sans-serif; }
  .card { width: 1600px; height: 900px; padding: 72px 88px; display: flex; flex-direction: column; justify-content: space-between; position: relative; overflow: hidden; }
  .grid { position: absolute; inset: 0; opacity: .06; background-image: linear-gradient(#fff 1px, transparent 1px), linear-gradient(90deg, #fff 1px, transparent 1px); background-size: 48px 48px; }
  .header, .hero, .footer { position: relative; }
  .header { display: flex; justify-content: space-between; align-items: center; }
  .tag { font-family: monospace; font-size: 20px; letter-spacing: .2em; color: #e9b94a; text-transform: uppercase; }
  .brand { font-family: monospace; font-size: 20px; opacity: .55; }
  .hero { border-top: 1px solid rgba(255,255,255,.28); border-bottom: 1px solid rgba(255,255,255,.28); padding: 70px 0; }
  .quote { margin: 0; font-family: serif; font-size: 92px; line-height: 1.2; font-weight: 400; letter-spacing: .01em; }
  .quote em { color: #e9b94a; font-style: normal; }
  .footer { display: flex; justify-content: space-between; align-items: flex-end; font-size: 24px; color: #cfc7b8; }
  .source { font-family: monospace; font-size: 20px; opacity: .7; }
</style>
</head>
<body>
  <div class="card">
    <div class="grid"></div>
    <div class="header"><div class="tag">Quote</div><div class="brand">示例账号</div></div>
    <div class="hero">
      <p class="quote">Markdown 是给<em>作者</em>爽的，<br>HTML 是给<em>读者</em>爽的。</p>
    </div>
    <div class="footer"><div>一位把内部文档换成 HTML 的工程师</div><div class="source">2026</div></div>
  </div>
</body>
</html>
```

渲染后用审计结果检查中段是否出现大空洞。若金句太短，补一行解释或把署名区做成账本行，而不是放大字体硬撑。
