# 风铃code

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Platform](https://img.shields.io/badge/Platform-Android-green.svg)

**跑在你自己手机上的私有 AI Agent。**

风铃code 把 Claude、GPT、Gemini 等大模型装进一个原生 Android 应用，并交给它们一台真正能干活的电脑：设备内运行的 Linux Shell、浏览器自动化、按需加载的技能、跨会话的持久记忆，以及对系统能力的深度调用。免费，完全开源。

最新安装包发布在本仓库的 [Releases](https://github.com/lixfl/FLcode/releases)。

---

## 核心能力

- **自带模型** — 接入多家服务商，用你自己的 API Key 或账号登录。
- **真正的 Linux Shell** — 设备内沙箱化 Alpine Linux（PRoot），可装包、跑脚本、读写真实文件。
- **设备集成** — 相机、定位、日历、通讯录、媒体文件、闹钟、通知、麦克风等，以工具形式暴露给 Agent。
- **浏览器自动化** — 代表你打开网页、抓取内容、与页面交互。
- **技能与记忆** — 按需加载的可扩展技能，加上跨会话保留的持久记忆。
- **工具集** — `shell_execute`、`browser_use`、`memory_write`/`memory_get`、文件读写编辑、图像读取。

## 特色

- **对话内搜索与跳转** — 在当前会话查找关键词，命中后精确滚动定位。
- **上下文进度条** — 实时显示上下文占用与模型窗口上限，接近上限时预警。
- **从此处分叉** — 长按任意消息，把该条之前的对话克隆成新会话，原会话不动，可在分支里换方向继续。
- **沉浸式侧边栏** — 会话按时间分组，长按进入多选 / 重命名 / 删除。
- **主题一键切换** — 浅色 / 深色点按即换，长按回到跟随系统。
- **空会话欢迎页** — 新对话提供灵感提示与最近会话入口。

---

## 技能

一个**技能**就是一个带 `SKILL.md` 的目录——说明文本，可附带脚本、素材。请求命中时 Agent 才按需加载：元数据常驻用于触发，正文与资源真正用到时才载入。为 Claude、Codex 等编写的技能通常可原样运行。技能存于 `fengling-global/skills/<id>/SKILL.md`，元数据记在本地 `skills.db`。

---

## 构建

应用内打包了 Linux 沙箱，原生依赖（PRoot、FFmpeg、LAME）和 Alpine rootfs 均从源码构建。完整指南见 [BUILDING.md](BUILDING.md)。简版：

```sh
git clone --recurse-submodules https://github.com/lixfl/FLcode.git
cd FLcode
./deps/build_proot.sh && ./scripts/prepare_android_sandbox.sh
cd src/android
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/opt/android-sdk
./gradlew :app:assembleDebug      # 调试包
./gradlew :app:assembleRelease    # 签名发布包
```

---

## 仓库结构

```
src/android/      Android 应用（Kotlin / Compose）+ JNI 原生代码
src/shared/       共享资源
deps/             原生依赖构建脚本与内置源码
docs/specs/       架构与接口规格
scripts/          rootfs 准备与开发者工具
```

---

## 致谢

风铃code 建立在大量开源工作之上，完整第三方清单见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。

- **PRoot**（GPLv2）+ **talloc**（LGPLv3+）— Android 沙箱的用户态 chroot。
- **Alpine Linux** — 沙箱启动所用的 minirootfs。
- **FFmpeg**（LGPL-2.1+）、**LAME**（LGPL）、**cppjieba**（MIT）、**KaTeX**（MIT）— 媒体与文本。
- **AndroidX / Jetpack Compose、OkHttp、Coil、kotlinx、ACRA、Shizuku** 等 — 应用层。

---

## 许可

风铃code 采用 **[GNU General Public License v3.0](LICENSE)**。应用链接了 GPL 许可的组件（PRoot，GPLv2），因此组合作品以 GPLv3 分发。

## 反馈

Bug 与功能建议请提交到 [GitHub Issues](https://github.com/lixfl/FLcode/issues)。
