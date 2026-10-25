# 样本分析记录

日期：2026-10-25。分析工具：JADX 1.5.3、Android Build Tools 35.0.0，以及直接读取 DEX 类定义/字段定义/方法定义的 `tools/verify_sample.py`。

样本文件：`oopz-1.1.10.apk`，218659970 字节；包名 `com.weilaishanhai.oopz`，versionName `1.1.10`，versionCode `90013`。SHA-256 见 README。

## 已验证的调用关系

`MainActivity` 继承 `FlutterActivity`，其 AndroidX SplashScreen 逻辑是系统启动画面的淡出，不是广告入口。

`classes4.dex` 中的 `com.example.flutter_cjadsdk_plugin.FlutterCjadsdkPlugin` 注册 MethodChannel `flutter_cjadsdk_plugin`。`FlutterCjadsdkPluginKt` 给出了下列方法与事件字符串：

| Dart 到原生 | 原生实现 |
| --- | --- |
| `cjSdkSetup` | 协程中调用 `CJMobileAd.init`，转发 `setupSuccess` / `setupFailed` |
| `cjLoadAndShowSplashMethod` | `loadSplashAd(false, advertId)` |
| `cjPreLoadSplashMethod` | `loadSplashAd(true, advertId)` |
| `cjShowSplashMethod` | `showSplashAd()` |

`loadSplashAd` 创建 `CJSplash`，读取当前 Activity，创建 `contentView`、`splashContentView` 等布局，再调用 `CJSplash.loadAd`。监听器 `onLoad` 发送 `splashAdLoadSuccess`；非预加载时自动调用 `showSplashAd`。`showSplashAd` 将覆盖层添加到窗口，再调用 `CJSplash.showAd`。`onShow` 发送 `splashAdOnShow`。`onClose` 移除覆盖层、发送 `splashAdOnClose`、调用 `destorySplash`。

错误路径：加载监听器 `onError` 发送包含 `code` / `message` 的 `splashAdLoadFailed`，然后销毁；缺少广告或 Activity 时 `showSplashAd` 发送 `splashAdShowError`。在加载入口前接管可避免先创建覆盖层再清理的时序问题。

`CJMobileAd.init` 存在配置缓存与 HTTP 初始化路径，失败可能发送 `initFailed`，插件再转为 `setupFailed`。模块保留实际初始化，给 Dart 提前发送成功并过滤这个插件通道的原始初始化结果，不修改全局 HTTP 层。

## 运行时符号（不是 JADX 自动别名）

| 符号 | DEX 签名 |
| --- | --- |
| 插件 `loadSplashAd` | `(ZLjava/lang/String;)V` |
| 插件 `showSplashAd` | `()V` |
| 插件 `destorySplash`（原始拼写） | `()V` |
| 插件 `onMethodCall` | `(Lq6/h;Lq6/i$d;)V` |
| 插件 `onDetachedFromEngine` | `(Lm6/a$b;)V` |
| 插件字段 `channel` | `Lq6/i;` |
| MethodCall 字段 `q6.h.a` | `Ljava/lang/String;` |
| MethodChannel `q6.i.c` | `(Ljava/lang/String;Ljava/lang/Object;)V` |

JADX 展示的 `f59514a` 是为了避免冲突生成的字段别名；实际字段为 `a`。这些符号均由 DEX 定义表验证。

## 已执行的验证与边界

- 样本 SHA-256 及 19 个方法/字段符号全部匹配，包括新增的 SDK 加载/展示/有效性/销毁、监听器回调、Result.success 和插件 splashAd 字段。
- JVM 上通过 37 项状态断言，覆盖立即展示、预加载、重复展示、重复初始化、取消、加载替换、引擎销毁、不同会话隔离、桥接指令分类和独立缓存展示。
- 宿主机 Hook 桩调用实际 OopzHook 代码，通过 19 项接线断言：原分发被阻止、MethodChannel 回应、非开屏指令保留、只过滤指定通道初始化、SDK 加载/展示被阻止、插件转发 SDK 关闭、取消和引擎销毁。不能验证 ART 内联行为。
- 模块完成 Java 编译、D8 转换、AAPT2 打包、zipalign 对齐及 APK v2/v3 签名校验。
- Manifest 包含模块标识、最低 Xposed 版本、单包作用域；`assets/xposed_init` 包含正确入口。
- 没有 Android/LSPosed 运行结果；没有还原 Dart AOT 控制流，没有证实具体退出触发点。伪造本地生命周期回调是否满足所有 Dart 状态检查仍需真机验证。

反编译片段留在本地 analysis 目录并被 .gitignore 排除；构建只使用模块原创源码和编译 API，不打包样本资源或反编译代码。

## 1.1.0：冷启动残留反馈

用户提供全屏广告截图，并确认发生在完全退出后重新启动 Oopz。截图能说明仍有开屏展示，但没有提供对应设备日志，不能据广告素材或角标断言具体 SDK、Hook 是否生效或唯一根因。

再次核对 GeneratedPluginRegistrant，只发现 `FlutterCjadsdkPlugin` 这一广告 Flutter 插件。`MyApplication` 调用的 `privacyCompliance` 最终仅设置静态标记，没有独立的开屏展示逻辑。Dart AOT 可读字符串包括 `cjPreLoadSplashMethod`、`splashNoPreload` 和开屏状态事件，未完整还原 Dart 控制流。

旧版主要 Hook 插件内部 load/show 方法，对指令入口只处理初始化。运行时内联可能绕过被 Hook 的被调方法，这是 [LSPlant 官方说明](https://lsposed.org/LSPlant/index.html) 描述的已知情况；它在此处仍是待验证假设。

新版直接在 onMethodCall 消费三个精确开屏指令，新增聚合 SDK `CJSplash` 的 load/show 兜底，不按广告素材、品牌或域名匹配。独立的模块加载、主 Hook 安装、SDK 安装、桥接命中、SDK 命中日志用于区分设备上的实际执行情况。

应用包名、源码命名空间和 Xposed 入口统一迁移至 `cn.plumc.fkoopz`；构建前清理经过路径校验的 classes/tests/dex 目录，避免旧命名空间残留进入 APK。

## 1.1.1：已证实的版本门禁根因

用户提供设备日志：`[dev.plume.fkoopz,...] [FkOopz] Unsupported target 1.1.10/90015; no hooks installed`。这证实该次运行加载了旧包名模块，但其版本检查拒绝设备上的 90015，所有广告 Hook 没有安装。90013 是本地 APK 的构建号，不能等同于所有同版本渠道包的构建号。之前关于运行时内联的讨论仅为假设，不能替代这个实际证据。

修改：保留 versionName `1.1.10` 范围，取消固定 versionCode。安装前从实际插件的字段和方法解析 Flutter 的类型、指令字段、响应方法与事件发送方法。严格要求唯一匹配和所需开屏签名；不再硬编码 q6/m6 名称。成功日志输出真实设备版本，例如 `Active for 1.1.10/90015`。

新增 12 项回归检查：通过实际 `installFor` 路径分别传入 90013、90015 后均验证 Hook 已安装、开屏原分发未执行且完成回调；不同 versionName 仍被拒绝；重命名 Flutter 类型可以解析；歧义接口拒绝匹配。加上原有 37 项状态和 19 项接线检查，总计 68 项宿主机断言通过。

样本内 `cj.mobile.blk.p1`、`com.byazt.wos.xc` 等广告 SDK 确有震动调用，其中包含传感器互动逻辑。未获取 90015 APK 或设备调用栈，不能确认截图震动的具体实现。此次修复首先使广告拦截真正有机会执行，不增加全局禁用震动的副作用。90015 的真实类结构和最终展示效果仍需设备日志/实测确认。

## 1.2.0：版本检测开关与用户验证

用户已反馈 1.1.1 的开屏广告已去除；这是用户设备反馈，并非本机自动化真机结果，不能外推为所有版本均已适配。

新增独立设置 Activity，默认开启「检查 Oopz 版本」，通过 LSPosed New XSharedPreferences 传递一个非敏感布尔值。设置页使用 MODE_WORLD_READABLE 保存；如果框架未支持或未启用，则禁用开关并显示原因，不回退写入目标进程无法读取的私有偏好文件。目标进程使用包名和文件名构造 XSharedPreferences，在安装 Hook 前读取；缺失或读取失败时保留原版本门禁。

关闭开关仅跳过 versionName 检查，不改变作用域或运行时接口检查。新的回归用例验证关闭后未知版本进入 Hook、重新开启恢复拒绝，以及读取失败时保持检测。当前为 37 项状态 + 19 项接线 + 17 项兼容性检查，共 73 项主机断言。设置界面与跨进程持久化尚待用户设备验证。
