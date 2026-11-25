# GlobalMinusOne · 全局负一屏

> **项目来源说明 / Project Origin**  
> 本开源项目灵感与核心交互理念源自**已停更的原 Android 应用《全局负一屏》**（原闭源软件），为延续该工具在现代 Android 系统（`targetSdk 36`）上的可用性与可定制体验而独立重新实现并开源发布。本项目与原闭源软件、原作者及其历史分销渠道无任何隶属、授权或官方延续关系。

> **AI 开发声明 / AI-Developed Notice**  
> 本项目主要由 **AI 辅助与主导完成架构设计、功能开发、重构与测试编写（AI-Developed / AI-Assisted Project）**，由开发者维护并开源发布。请在启用无障碍、悬浮窗或 Root/Shizuku 能力前按需审阅相关源码。

- **开发者**：[`misaka02`](https://github.com/misaka02)
- **项目地址**：[`https://github.com/misaka02/GlobalMinusOne`](https://github.com/misaka02/GlobalMinusOne)
- **下载发布包**：[`Releases`](https://github.com/misaka02/GlobalMinusOne/releases)
- **问题反馈**：[`https://github.com/misaka02/GlobalMinusOne/issues`](https://github.com/misaka02/GlobalMinusOne/issues)
- **开源协议**：[Apache License 2.0](LICENSE)

基于 **Kotlin + Jetpack Compose** 构建的轻量级 Android 全局侧边负一屏与效率工具箱（`minSdk 26` / `targetSdk 36`），完全在本地运行，不包含任何远端统计 SDK、广告或云端账号体系。

---

## 核心特性

- **临界阻尼物理动效**：侧边唤出跟手弹簧、速度感知 VSYNC 驱动与多页首尾循环翻页，支持自定义动画时长（含极速/零动画瞬切），并接入系统 `onTrimMemory` 位图缓存回收。
- **三布局多页画布与嵌套容器**：支持线性流（Linear）、自由网格画布（Free）与规整网格（Grid），支持容器盒及弹窗式文件夹嵌套、自适应极简顶栏与 `92–1200dp` 宽度定制。
- **12 类「单图标弹出」浮舱 & 多样卡片**：
  - 支持单图标弹出的功能/内容卡片：应用抽屉、键盘快启、最近运行、亮度条、音量条、剪贴板、便签、桌面小部件（`AppWidget`）、电池详情、单图卡片、魔方相册（`Photo Cube`）、一言；
  - 以及应用快捷方式、自定义 Intent、网站链接、文件快捷入口、Wi-Fi / 蓝牙 / 手电筒等状态磁贴与实时统计；
  - 面板内浮动文件浏览与文本/音视频/图片预览、内置轻量 WebView 浏览器（支持多标签与无痕 Profile 隔离清理），以及可选本地识屏工具。
- **桌面小部件安全托管**：集成防崩溃 `AppWidgetHostView`、子列表/外层滚动方向仲裁与长按透传编辑。
- **边缘手势与悬浮球**：支持左/右/双侧/顶部/底部边缘滑动、二次防误触滑动、悬浮球 7 向手势绑定与快速设置磁贴。
- **备份与迁移**：原生配置快照（`state_json_v5` / ZIP 备份导入导出）及 Panels 备份结构兼容导入。

---

## 权限与隐私声明

本应用设计为**纯本地离线工具**，所有布局、应用索引、剪贴板与文件操作均在用户设备本地完成：

| 权限 | 用途说明 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 在屏幕上方渲染负一屏悬浮面板、边缘触发区与悬浮球。 |
| `BIND_ACCESSIBILITY_SERVICE` | 可选；识别前台应用包名以启用全屏/游戏防误触避让，及执行一键锁屏、截屏、展开通知栏等系统动作；**不读取屏幕文本或监听输入内容**。 |
| `QUERY_ALL_PACKAGES` | 用于构建本地应用抽屉与快捷启动索引。 |
| `MANAGE_EXTERNAL_STORAGE` / SAF | 可选；面板内嵌文件浏览器浏览与管理本机文件（未授全局管理权限时也可单独授权 SAF 目录）。 |
| `WRITE_SETTINGS` / `MODIFY_AUDIO_SETTINGS` | 仅用于面板内亮度条与音量条卡片调节系统数值。 |
| `INTERNET` | 仅用于内置浏览器打开用户主动输入的网址，或用户手动配置的 OCR/大模型/在线词库请求，无后台遥测、崩溃上报或账号体系。 |
| `moe.shizuku.manager.permission.API_V23` | 可选；仅在用户主动触发需 Shizuku 权限的系统/Shell/识屏操作时请求。 |

---

## 云端自动构建与本地编译

### GitHub Actions（构建与静态检查）
每次向 `main` 分支推送代码或提交 Pull Request 时，GitHub Actions（[`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml)）会自动在云端执行：
- JVM 全量单元测试（`:app:testDebugUnitTest`，`1,100+` 项）
- AndroidTest Kotlin 编译检查（`:app:compileDebugAndroidTestKotlin`）
- Android Lint 静态检查（`:app:lintDebug`）
- Debug 与 Release APK 构建（`:app:assembleDebug`、`:app:assembleRelease`）与 Workflow Artifacts 归档。

> GitHub Releases 中的安装包含兼容历史证书重签校验，由本地签发脚本经人工核验后上传；云端 CI 仅做构建与测试验证，不覆盖已签发的 Release 附件。

### 本地从源码构建
- **JDK**: Java 17
- **Android SDK**: `compileSdk 36` / `minSdk 26` / `targetSdk 36`
- **Gradle**: Wrapper 9.1.0 / AGP 9.0.1 / Kotlin 2.3.20

```bash
# 运行 JVM 单元测试（1,100+ 项）
./gradlew testDebugUnitTest

# 检查 AndroidTest 源码编译
./gradlew compileDebugAndroidTestKotlin

# 运行 Android Lint 静态检查
./gradlew lintDebug

# 编译 Debug APK
./gradlew assembleDebug

# 编译 Release APK（若未配置签名属性，默认回退为 debug 签名）
./gradlew assembleRelease
```

> **提示**：第三方离线依赖 [re2j-1.8.jar](app/libs/re2j-1.8.jar)、`third_party/shizuku-13.1.5/` 及已合入稳定性补丁的 `third_party/libsu-6.0.0/` 均随源码提供溯源校验与完整许可证，详见 [NOTICE](NOTICE) 与 [third_party/ROOT_SHIZUKU_PREP.md](third_party/ROOT_SHIZUKU_PREP.md)。

### Release 签名与包名定制

1. **正式发版签名**：切勿将签名密钥或密码提交至 Git。请在项目根目录新建（已在 `.gitignore` 中忽略）`release-signing.properties`：
   ```properties
   storeFile=path/to/release.keystore
   storePassword=YOUR_STORE_PASSWORD
   keyAlias=YOUR_KEY_ALIAS
   keyPassword=YOUR_KEY_PASSWORD
   ```
   或通过环境变量传入 `GMO_SIGNING_STORE_FILE`、`GMO_SIGNING_STORE_PASSWORD`、`GMO_SIGNING_KEY_ALIAS`、`GMO_SIGNING_KEY_PASSWORD`；本地调试兼容重签可通过 `GMO_COMPAT_DEBUG_KEYSTORE` 指定外部调试密钥库路径。
2. **自定义应用包名**：默认 `applicationId` 为 `com.example.globalminusone`（便于本地兼容既有安装）；若独立分发或上架商店 / F-Droid，请在 `app/build.gradle.kts` 中修改为你自己的反向域名包名。

---

## 开源协议

本项目基于 **[Apache License 2.0](LICENSE)** 协议开源；所集成的第三方组件协议与本地补丁说明请查阅 [NOTICE](NOTICE)。
