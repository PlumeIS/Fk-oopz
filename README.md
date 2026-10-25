# Oopz 去开屏（LSPosed 模块）

针对根目录 `oopz-1.1.10.apk` 制作的模块，安装包为 [dist/FkOopz-1.2.0.apk](dist/FkOopz-1.2.0.apk)。通过原生 Flutter 广告桥接层跳过开屏请求，并发送加载、展示、关闭回调。不是自动点击跳过按钮。

**当前版本：1.2.0。** 用户已确认 1.1.1 在设备上去除了开屏广告；1.2.0 新增可保存的版本检测开关。新版本通过 73 项主机检查和 APK 签名校验，设置页面及跨进程保存仍需真机验证。

源码仓库：[PlumeIS/Fk-oopz](https://github.com/PlumeIS/Fk-oopz)。仓库不包含原始 Oopz APK、反编译源文件、构建工具或签名私钥。

## 安装

1. **从 1.0.0 升级：先停用或卸载旧模块 `dev.plume.fkoopz`。** 新包名为 `cn.plumc.fkoopz`，不能覆盖安装旧包，不要同时启用两版。无需卸载 Oopz。
2. 在已安装并正常启用 LSPosed 的 Android 设备上安装新版模块 APK。已安装 `cn.plumc.fkoopz` 1.1.0 / 1.1.1 时可直接覆盖升级到 1.2.0。
3. 在 LSPosed 模块列表启用新版 **Oopz 去开屏**，作用域只勾选 **Oopz / com.weilaishanhai.oopz**。
4. 强制停止 Oopz，再重新启动；若框架提示重启，按提示重启设备。
5. 查看 LSPosed 日志，搜索 `[FkOopz]`。确认 `Module 1.2.0 [cn.plumc.fkoopz] loaded`、`Bridge hooks installed`、`Active for 1.1.10/90015` 和 `CJSplash load/show guard installed`（样本设备对应 90013）。

模块提供「Oopz 去开屏」桌面入口，也可从支持模块设置入口的 LSPosed 管理器打开。无需修改或重签 Oopz APK。停用模块并强制停止 Oopz 后，恢复原有行为。

## 版本检测开关

打开模块设置中的 **检查 Oopz 版本**：

- 默认开启：只接受 versionName `1.1.10`，不限制 versionCode，支持用户设备的 `90015`。
- 关闭：允许其他 Oopz 版本尝试安装 Hook，但仍严格检查实际广告接口；关闭检测不代表已适配新版本。
- 自动保存后，**强制停止 Oopz 再重新打开**。设置页有「打开 Oopz 应用信息」按钮，方便进入系统页面操作。
- 日志中的 `Version check: enabled` / `disabled` 显示本次进程使用的设置。

跨进程设置采用 [LSPosed New XSharedPreferences](https://github.com/LSPosed/LSPosed/wiki/New-XSharedPreferences)，需要框架保留对这项功能的支持（LSPosed API 93 起提供）。不支持该功能或未启用模块时，设置页会显示共享设置不可用并禁用开关；默认版本检测仍保持开启。部分新框架分支可能弃用这一旧模块设置接口。

## 适配范围与行为

| 项目 | 值 |
| --- | --- |
| 目标包名 | `com.weilaishanhai.oopz` |
| 目标版本 | 默认 `1.1.10`，允许不同 versionCode；关闭版本检测可尝试其他版本，始终检查运行时接口 |
| 模块包名 | `cn.plumc.fkoopz` |
| Android 最低版本 | Android 7.0 / API 24；框架本身的要求另计 |
| 模块入口 | 传统 Xposed API 82 / `assets/xposed_init` |
| 样本 SHA-256 | `c7286974d65598937a96d11199f52c00acf304cf38671858a122b573dace8445` |

- 在 `FlutterCjadsdkPlugin.onMethodCall` 直接消费 `cjLoadAndShowSplashMethod`、`cjPreLoadSplashMethod`、`cjShowSplashMethod`，不进入原始协程分发，同时完成 MethodChannel 响应。
- 保留 `loadSplashAd` / `showSplashAd` 拦截，并新增 `CJSplash.loadAd` / `showAd` 底层兜底。若调用经过优化路径或使用缓存对象，则通过原有 `CJSplashListener` 回调完成关闭，原始销毁逻辑继续执行。
- 普通加载发送 `splashAdLoadSuccess → splashAdOnShow → splashAdOnClose`，各阶段通过主线程消息队列调度。
- 预加载只发送加载完成；后续 `showSplashAd()` 再发送展示和关闭。新会话中只有缓存展示、没有加载记录的情况也会发送展示和关闭。重复展示、取消、引擎销毁会去重或取消待发回调。
- 对该插件的 `cjSdkSetup` 提前发送本地 `setupSuccess`，使启动流程不等待广告初始化联网结果；屏蔽该通道原本随后发出的初始化成功/失败通知，避免重复或失败分支覆盖启动状态。
- **原始广告 SDK 初始化仍会在后台运行并可能联网。** 本模块不是全局广告域名拦截器，也不承诺阻止所有广告 SDK 网络活动。其他广告类型继续执行原逻辑；提前完成初始化通知可能影响立即请求其他类型广告的时序，需要真机回归。
- 不替换账号、登录、语音等网络请求，不拦截全局 `System.exit`、`killProcess` 或 `Activity.finish`。
- 默认检查 versionName `1.1.10`，可在设置中关闭，不把 versionCode 固定为 `90013`。从插件字段/方法签名解析 MethodChannel、MethodCall、Result 等实际类型，不硬编码 `q6` / `m6` 混淆名称；接口缺失或匹配歧义时不猜测。主桥接层安装失败会撤销该组 Hook，在 `MyApplication.onCreate` 前重试。SDK 兜底单独安装，失败不影响主桥接层。

用户报告的“断开广告连接会退出”尚未在设备复现。已确认原生广告桥接会发送初始化/加载失败事件，但本次没有还原 Flutter AOT 中的完整退出判断。因此回调方案的实际效果需要真机确认，不能把静态分析视为已修复所有退出路径。

## 真机验证

先在正常联网、没有额外广告域名拦截规则的环境验证：

1. 强制停止后启动三次，应进入正常登录页或首页，没有广告覆盖层、跳过按钮和广告外部跳转。
2. 切到后台，再返回前台；测试预加载后的展示是否也直接结束。
3. 检查登录、语音房、通知唤起和系统返回操作。
4. 如需与现有广告网络拦截规则共同使用，再单独测试该环境。断网时 Oopz 自身的业务仍可能需要联网。

预期日志（预加载时 Load 与 Show/Close 可以间隔出现）：

```text
[FkOopz] Module 1.2.0 [cn.plumc.fkoopz] loaded in com.weilaishanhai.oopz
[FkOopz] Target 1.1.10/90015; checking runtime signatures
[FkOopz] Bridge hooks installed; channel=...
[FkOopz] Active for 1.1.10/90015
[FkOopz] CJSplash load/show guard installed
[FkOopz] Startup released from SDK initialization
[FkOopz] Callback: setupSuccess
[FkOopz] Bridge blocked: cjLoadAndShowSplashMethod
[FkOopz] Callback: splashAdLoadSuccess
[FkOopz] Callback: splashAdOnShow
[FkOopz] Callback: splashAdOnClose
```

预加载命令会记录 `Bridge blocked: cjPreLoadSplashMethod`，真正命中底层兜底才出现 `SDK blocked: CJSplash.loadAd` 或 `SDK blocked: CJSplash.showAd`，正常桥接拦截时无需出现 SDK 命中日志。

没有 `Module 1.2.0 [cn.plumc.fkoopz] loaded` 时检查新版模块是否启用、作用域和框架；只有加载日志没有 `Bridge hooks installed` 时检查版本与安装错误。有 `Install failed`、`Callback failed`、`SDK callback failed`，或已命中却仍有广告/退出时，保留 `[FkOopz]` 日志、同时间的应用崩溃日志、Android/LSPosed/Oopz 版本供进一步定位。日志不记录账号、广告标识符或请求内容。

## 构建与验证

Windows PowerShell、JDK 17 或更高版本（本机使用 JDK 25），无需 Gradle 或 Android Studio：

```powershell
./setup-tools.ps1
./build.ps1
# 可选：验证根目录原始 APK 的哈希和所有 Hook 符号；需要 Python 3.11+
python tools/verify_sample.py
```

`setup-tools.ps1` 从官方源下载固定版本的 Android SDK 35 和 Xposed API 82，并核对 SHA-256。`build.ps1` 编译代码、运行 37 项生命周期检查、19 项反射 Hook 接线检查及 17 项兼容性回归检查、生成 DEX、打包资源、对齐和签名，输出 `dist/FkOopz-1.2.0.apk` 与 `dist/SHA256SUMS.txt`。兼容性测试从真实 installFor 入口验证 90013 / 90015 都能安装并消费开屏指令，并检查开关开启/关闭/读取失败、混淆类型重命名及歧义拒绝。接线测试使用宿主机桩验证真实模块代码的入口和回调，不模拟 ART 优化或实际 Flutter 引擎；桩不打包进 APK。

Xposed API 仅用于编译，不打包进 APK。模块不请求任何 Android 权限。

首次构建会生成本地开发签名 `.signing/development.jks`（别名 `fkoopz`，公开开发密码 `android`）。保留此文件才能用后续本地构建覆盖安装。它不是用于公开发布的保密签名；更换签名需先卸载旧模块。

源码入口：[OopzHook.java](app/src/main/java/cn/plumc/fkoopz/OopzHook.java)；回调状态管理：[SplashSession.java](app/src/main/java/cn/plumc/fkoopz/SplashSession.java)；分析依据：[analysis/NOTES.md](analysis/NOTES.md)。

参考：[Xposed 模块开发说明](https://github.com/rovo89/XposedBridge/wiki/Development-tutorial)、[XposedBridge 官方 API](https://api.xposed.info/reference/de/robv/android/xposed/XposedBridge.html)、[JADX](https://github.com/skylot/jadx)。
