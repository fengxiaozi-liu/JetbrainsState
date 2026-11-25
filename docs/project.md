# 项目说明

这是针对 Windows 版 GoLand 2026.3（构建号 `GO-263.7307.211`）的本地实验项目，范围包括 JVM 启动配置、授权状态观察及 Go 工具链验证。实验中使用的 Java Agent 会改变 IDE 运行时的授权判断。

| 路径 | 内容 |
| --- | --- |
| `src/` | Java 实验代码及验证工具。 |
| `scripts/configure-exe.ps1` | GoLand 本地启动配置和恢复实现。 |
| `scripts/restore-exe.cmd` | 恢复当前 Windows 用户的 GoLand 启动设置。 |
| `README.txt` | 原有实验记录。 |

`build/` 和 `dist/` 用于保存本地构建产物。`sandbox/`、`exe-check/` 和 `verification/` 中的运行状态、日志和验证结果保存在本地。

恢复步骤见[使用说明](../README.md)。

## GitHub Release 校验

测试 tag 使用 `v` 开头的名称，指向本地 `codex/release-validation` 分支上的独立提交。GitHub Release 会附带 tag 对应的源码归档，因此测试提交里只放发布工作流和 `.gitignore`。

```powershell
git tag v0.0.0-validation.1 codex/release-validation
git push origin refs/tags/v0.0.0-validation.1
```

GitHub Actions 会在运行器上生成 `dist/release-validation.zip`，核对 ZIP 内唯一的清单后，将其作为对应预发布版本的附件。每次校验使用一个新的 tag。
