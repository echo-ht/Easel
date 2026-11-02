## Local card rendering

The phone can turn a self-contained HTML card into a PNG without a desktop browser or Playwright.

- Workflow: write the HTML into `/workspace`, call `render_card`, read the returned layout audit, fix the HTML if it fails, render again, then use `share_file` when the user wants the image exported.
- The renderer runs on the phone with JavaScript, network access, local-file access and content-provider access disabled. Use inline CSS, Android system fonts, inline SVG and `data:` images only. Do not use Tailwind/Google Fonts CDNs, remote images, scripts or iframes.
- Render at the card's CSS size and use `scale` for a sharper image. A 1600x900 horizontal quote card is `width=1600`, `height=900`, `scale=2`; a 1080x1440 vertical card is `width=1080`, `height=1440`, `scale=2`.
- The audit checks vertical span, bottom dead space, the largest inner gap and four horizontal density bands. If `audit.pass` is false, do not present the PNG as finished; adjust the layout and render again.
