# FkOopz

## 用途

用于去除 Oopz 开屏广告的 LSPosed 模块，无需修改 Oopz 安装包。当前适配 Oopz `1.1.10`，模块包名为 `cn.plumc.fkoopz`。

## 安装

1. 在已启用 LSPosed 的 Android 设备上，从 [Releases](https://github.com/PlumeIS/Fk-oopz/releases/latest) 下载并安装模块 APK。
2. 在 LSPosed 中启用「Oopz 去开屏」，作用域勾选 Oopz（`com.weilaishanhai.oopz`）。
3. 强制停止 Oopz 后重新打开；框架提示重启时，按提示操作。

模块设置中的「检查 Oopz 版本」默认开启。关闭后可尝试其他版本，但不保证兼容；修改设置后需强制停止并重新打开 Oopz。保存设置需要框架支持 New XSharedPreferences。

从旧包名 `dev.plume.fkoopz` 升级时，先卸载旧模块。

## 开发

环境：Windows PowerShell、JDK 17 或更高版本，无需 Gradle 或 Android Studio。

```powershell
./setup-tools.ps1
./build.ps1
```

构建脚本运行宿主机测试并生成已签名 APK 和 SHA-256 校验文件，输出到 `dist/`。该目录不纳入源码管理；发布时将当前 APK 与 `SHA256SUMS.txt` 上传至 GitHub Release。

本地签名保存在 `.signing/development.jks`，保留它才能覆盖安装后续构建。原始 APK、反编译文件、构建工具和签名文件均不提交。

修改 Hook 时，使用目标 APK 核实真实 DEX 签名，再构建并进行 LSPosed 真机验证。原始样本放在根目录 `oopz-1.1.10.apk` 后，可使用 Python 3.11+ 运行：

```powershell
python tools/verify_sample.py
```

Hook 入口：[OopzHook.java](app/src/main/java/cn/plumc/fkoopz/OopzHook.java)；分析记录：[analysis/NOTES.md](analysis/NOTES.md)。
