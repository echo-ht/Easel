# Agent 快速路径

简体中文 | [English](agent-quickstart.en.md)

给电脑上的 AI Agent：按下面的固定步骤安装 Easel 并连接手机，不用自己权衡方案。完整说明见[安装](computer-install.md)和[CLI](cli.md)。

## 1. 解析最新版本 tag

```sh
curl -s https://api.github.com/repos/echo-ht/Easel/releases/latest   # 取 tag_name
```

```powershell
(Invoke-RestMethod https://api.github.com/repos/echo-ht/Easel/releases/latest).tag_name
```

下文 `<tag>` 即该值（如 `v0.1.31`），`<ver>` 是去掉 `v` 的版本号（如 `0.1.31`）。

## 2. 选 CLI 还是 Skill

- Agent 支持 Skill：**默认装 Skill**，它自带 CLI 脚本。
- 否则装 CLI：`pipx install 'git+https://github.com/echo-ht/Easel.git@<tag>#subdirectory=tools/easel'`。

## 3. 安装 Skill

`<技能目录>` 是你所用 Agent 的技能目录，按该 Agent 自己的文档确定；Skill 装在它下面的 `easel` 文件夹里。

POSIX：

```sh
curl -L -o easel.zip https://github.com/echo-ht/Easel/archive/refs/tags/<tag>.zip
unzip -q easel.zip -d easel-src
mkdir -p <技能目录> && cp -R easel-src/Easel-<ver>/tools/easel <技能目录>/easel
python3 <技能目录>/easel/scripts/easel_cli.py --version
```

Windows PowerShell：

```powershell
Invoke-WebRequest https://github.com/echo-ht/Easel/archive/refs/tags/<tag>.zip -OutFile easel.zip
Expand-Archive easel.zip -DestinationPath easel-src
New-Item -ItemType Directory -Force "<技能目录>" | Out-Null
Copy-Item -Recurse easel-src\Easel-<ver>\tools\easel "<技能目录>\easel"
py -3 "<技能目录>\easel\scripts\easel_cli.py" --version
```

解释器：Windows 用 `py -3`，其他系统用 `python3`。下文 `easel` 在只装 Skill 时即 `python3 <技能目录>/easel/scripts/easel_cli.py`（Windows `py -3 ...`）。

## 4. 连接

按用户在手机上选的方式连接（设置 › 连接电脑）。Agent 看不到手机：用户没说用哪种时，问一次，不要猜。

1. USB：用已授权的 ADB。
   ```sh
   easel devices --json
   easel connect --device SERIAL --json
   ```
   不止一台在线时问用户用哪台，之后一直用这一台。
2. Wi-Fi / 私有 VPN：用用户给的 HTTP 地址和令牌文件路径（令牌只从文件读，不显示内容）：
   ```sh
   easel --url http://PHONE:8765 --token-file /path/to/token doctor --json
   ```

状态 `unauthorized`：让用户在手机上允许 USB 调试。`offline`：让用户重新插拔数据线。读不到连接信息（`connection_info`）：让用户解锁手机、打开一次 Easel，再运行 `connect`。

## 注意

- 不提交手机任务，不打印、不回贴令牌。
- `connect` / `doctor` 成功只说明连通，不代表任务能成功。
