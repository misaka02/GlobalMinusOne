# 贡献指南 · Contributing Guide

欢迎前往 [misaka02/GlobalMinusOne](https://github.com/misaka02/GlobalMinusOne) 通过 [Issues](https://github.com/misaka02/GlobalMinusOne/issues) 和 Pull Request 参与 **GlobalMinusOne** 的改进！

## 开发环境

- JDK 17
- Android SDK Platform 36（Build-Tools 与 Compose 兼容环境）
- 使用项目根目录 `./gradlew`（Gradle 9.1.0）执行构建与检查

## 提交代码前自检

请在提交 PR 前至少确认以下命令在本地（或 Fork 仓库的 GitHub Actions）通过：

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug assembleRelease
```

## 编码与隐私红线

1. **隐私与测试夹具红线（重要）**：
   - 测试资源中**严禁**提交来自真实设备的备份文件、个人应用清单、私有网址、聊天群号、个人邮箱、真实照片或密钥/签名文件。
   - 所有 `.bkp` / JSON / ZIP 测试夹具必须使用纯合成（synthetic）样例数据。
2. **本地无遥测原则**：
   - 不要引入需要远程上报、广告追踪、静默下载或强制登录的 SDK。
3. **跨层兼容性**：
   - 修改配置模型时请保留与 `state_json_v5` 持久化结构的向后兼容与边界校验。
4. **安全敏感边界**：
   - 涉及无障碍服务、文件浏览器路径边界（`FileProvider`）、ZIP 导入（ZIP bomb / 路径穿越）及 Shizuku / Root Shell 执行的改动，必须同步补充单元测试。

## 许可证

向本仓库提交贡献即表示你同意你的代码按照项目根目录下的 [Apache License 2.0](LICENSE) 发布。
