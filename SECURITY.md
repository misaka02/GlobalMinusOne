# 安全策略 · Security Policy

- **项目仓库**：[https://github.com/misaka02/GlobalMinusOne](https://github.com/misaka02/GlobalMinusOne)
- **维护者**：[misaka02](https://github.com/misaka02)

## 安全边界说明

GlobalMinusOne 运行于悬浮窗与可选的高权限辅助场景中（且主要由 AI 辅助与主导开发），核心安全边界包括：

1. **本地持久化与跨应用传输**：面板配置与剪贴板/待办记录仅存储于应用私有目录；通过 `FileProvider` 共享文件前需校验规范路径边界。
2. **外部配置 / Panels 备份导入**：解析压缩包与 JSON 时执行单文件/总解压上限、嵌套深度上限及路径穿越/符号链接检查，防止恶意畸形档案触发 OOM 或任意文件覆盖。
3. **内置 WebView**：普通浏览与无痕模式（当系统 WebView Provider 支持 `MULTI_PROFILE` 时）隔离 Profile 与 Cookie，退出/重启残留标记仅清理专用无痕 Profile。
4. **Shizuku / Root 能力**：仅在用户明确配置的快捷操作或开关触发时使用，不对外导出无权限校验的执行接口。

## 漏洞报告方式

如果您发现潜在安全隐患（如权限绕过、路径穿越、Intent 注入、WebView 越权或提权风险）：

- **请勿**在公开 Issue 中直接披露可被立即利用的 PoC 细节；
- 请通过 [GitHub Security Advisories](https://github.com/misaka02/GlobalMinusOne/security/advisories) 私密提交，或在 [Issues](https://github.com/misaka02/GlobalMinusOne/issues) 发起标题为 `[Security] ...` 的简要说明以联系维护者建立私密沟通渠道；
- 报告中请尽可能包含受影响的版本号、复现步骤与影响范围分析。
