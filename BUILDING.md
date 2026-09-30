# 构建风铃code（Android）

风铃code 在应用内打包了一个 Linux 沙箱，因此首次构建不只是"打开工程点运行"：原生依赖（PRoot、FFmpeg、LAME）和 Alpine rootfs 都是**由 `deps/` 下的脚本从源码构建**，而非以二进制提交。首次构建约需 30–60 分钟；之后产物缓存在磁盘上，常规构建很快。

开始前端到端读完本文件——步骤按依赖顺序排列，跳步会在后面产生难以定位的链接错误。

---

## 通用准备

用带子模块的方式克隆——PRoot 是子模块，缺了它会在原生构建那一步失败：

```sh
git clone --recurse-submodules https://github.com/lixfl/FLcode.git
cd FLcode

# 已经克隆过但没加 --recurse-submodules？
git submodule update --init --recursive
```

| 子模块 | 仓库 | 用途 |
|---|---|---|
| `deps/proot` | [OpenMinis/proot](https://github.com/OpenMinis/proot) | Android 沙箱 |

### 构建期自定义

有些值在构建期注入，**不在**本仓库里。构建前先复制模板：

```sh
cp src/android/app/provider-customization.properties.example \
   src/android/app/provider-customization.properties
```

留空也可以——**应用能编译、能运行**。某个值只被用到它的那个功能要求，缺失时该功能会在运行时明确报错。基于 API Key 的登录无需任何自定义即可使用。

### `ANTHROPIC_OAUTH_IDENTIFIER_PROMPT`

只有在你想用 **Claude OAuth 凭据登录**（而非 Anthropic API Key）时才相关。

当请求用 OAuth 鉴权时，Anthropic 端点期望 system prompt 以 Claude Code 自身发送的标识行开头，缺失则请求被拒。构建从该值注入这一行，因此它为空时 OAuth 登录会在运行时失败。

我们不随包提供该值，需要这条路径时请自备。其他开源项目（例如
[claude-relay-service](https://github.com/Wei-Shaw/claude-relay-service)）
会声明同样的标识，可查阅其确切措辞。

其余一切——Anthropic API Key，以及所有其他服务商——无需设置该项即可工作。

---

## Android

### 环境要求

| 工具 | 版本 / 说明 |
|---|---|
| JDK | **17**（`sourceCompatibility`/`targetCompatibility` 为 17） |
| Android SDK | **compileSdk 36**，targetSdk 35，**minSdk 26** |
| Android NDK | **r28+**——设置 `$ANDROID_NDK_HOME`，或通过 Android Studio 安装 |
| CMake | 3.22.1（通过 SDK Manager 安装） |
| Shell 工具 | `curl`、`tar`、`make`、`awk`、`sed` |

Gradle 本身由 wrapper 提供（Gradle 8.11.1、AGP 8.7.3、Kotlin 2.1.0）——不要单独安装。

仅构建 `arm64-v8a`（`abiFilters`），因此请使用 arm64 设备或模拟器镜像。

### 1. 构建原生依赖

```sh
./deps/build_proot.sh              # → assets/proot-aarch64, jniLibs/arm64-v8a/*.so
./scripts/prepare_android_sandbox.sh   # → assets/alpine-minirootfs.tar.gz
```

- **`build_proot.sh`** 用 NDK 交叉编译一个静态的 `libtalloc` 和 `deps/proot` fork，然后安装进应用的 `assets/` 和 `jniLibs/arm64-v8a/`：proot 可执行文件本身，加上 **`libproot-loader.so` 和 `libproot-loader32.so`**。脚本最后会逐一校验，任一缺失即让构建失败。

  这些 loader 是必需项，不是可选项。Android 10+ 对 `untrusted_app` 强制 W^X：任何被标记为 `app_data_file` 的东西——包括解到应用 `files/` 目录下的整个 Alpine rootfs——都不能被执行。只有从 APK 里 `lib/**/*.so` 填充的 `nativeLibraryDir` 可执行，因此 loader 放在那里，自行映射 guest 可执行文件，而不依赖内核的 `execve`。（proot 通常能在运行时解出一个内嵌 loader，但在 Android 上它会解到应用临时目录，撞上同样的 W^X 墙。）这也是为什么它们虽是可执行文件却带 `.so` 后缀——只有 `*.so` 会被解出。

  产物在不同 NDK 版本之间**并非逐字节一致**；不同 toolchain 代际生成的 loader 代码有差异。功能等价——别指望校验和能和别人的构建对上。
- **`prepare_android_sandbox.sh`** 把 Alpine aarch64 minirootfs 下载到 `assets/`。

两者都写入 `src/android/app/src/main/`，其产物已被 gitignore——它们是构建产物，请重跑脚本而不是提交它们。

`src/main/cpp/` 里的小型 JNI 库（`pty_bridge`、崩溃处理器、`jieba_jni`）由 CMake 在常规 Gradle 构建中一并生成，无需单独步骤。

### 2. 构建应用

```sh
cd src/android
./gradlew :app:assembleDebug          # → app/build/outputs/apk/debug/
./gradlew :app:installDebug           # 安装到已连接的设备
```

发布构建（`assembleRelease`）按签名配置产出分发用 APK。

### 测试

```sh
./gradlew :app:testDebugUnitTest        # JVM 单元测试
./gradlew :app:connectedAndroidTest     # instrumented，需要设备/模拟器
```

---

## 故障排查

**`deps/proot` 为空**——子模块未初始化：`git submodule update --init --recursive`。

**`Android NDK not found`**——把 `ANDROID_NDK_HOME` 指向你的 NDK r28+ 安装位置，例如
`export ANDROID_NDK_HOME=~/Library/Android/sdk/ndk/28.0.12433566`。

**应用能启动但 Shell 不行**——缺少沙箱资源。重跑 `./deps/build_proot.sh` 和
`./scripts/prepare_android_sandbox.sh`，然后重新构建。

**每条命令都返回 `[Shell not running] (exit code: -1)`**——APK 里缺少 proot 的 ELF loader。检查
`src/android/app/src/main/jniLibs/arm64-v8a/` 是否包含 `libproot-loader.so`
（以及 `libproot-loader32.so`）；若没有，重跑 `./deps/build_proot.sh`（它会做校验），然后重新构建。
用 `unzip -l app-debug.apk | grep libproot` 确认——三个 `.so` 必须都在。

这个故障从外面刻意难以察觉：proot 仍会启动并打印它的 `native_offload` 初始化日志，所以沙箱看着是健康的，任何"能不能启动？"的检查都会通过。只有第一次 `execve("/bin/sh")` 会失败，在 logcat 的 `PRootStderr` 标签下报 `Permission denied`。改动这块时，请通过**运行一条命令并断言 exit code 为 0** 来验证——只把沙箱拉起来是不够的。

**某个功能因缺少配置值而抛错**——该值来自自定义文件，见 [构建期自定义](#构建期自定义)。

---

## 许可说明

风铃code 是 **GPLv3**，因为它链接了 PRoot（GPLv2）。如果你改动原生依赖的构建方式，请把 FFmpeg 保持在其 LGPL 配置，并保留内置的 `LICENSE` 文件。见 [LICENSE](LICENSE) 与 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。
