Render one self-contained HTML card to a PNG on this phone.

Put the HTML under /workspace first, then pass its path and a new .png path under /workspace. The renderer disables JavaScript, network access and local-file access, so external scripts, stylesheets, fonts and images will not load; use inline CSS, system fonts, inline SVG and data: images. The result includes a deterministic layout audit. If audit.pass is false, read the problems, change the HTML and render again instead of delivering the image silently. Use share_file after the card passes when the user wants it in Downloads/Easel.
