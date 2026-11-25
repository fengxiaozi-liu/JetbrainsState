# 使用说明

## 选择产品入口

脚本位于 `scripts/`，使用同一目录中的配套文件，不要单独复制一个 CMD。

| 操作 | PyCharm | GoLand |
| --- | --- | --- |
| 只检查，不改设置 | `check-pycharm.cmd` | `check-goland.cmd` |
| 安装配置 | `install-pycharm.cmd` | `install-goland.cmd` |
| 重新检查并修复配置 | `repair-pycharm.cmd` | `repair-goland.cmd` |
| 恢复原配置 | `restore-pycharm.cmd` | `restore-goland.cmd` |

当前适配目标为 PyCharm `PY-263.7307.351` 和旧版 GoLand `GO-263.7307.211`。
本机 GoLand 已升级到 `GO-263.7307.354`，当前检查脚本会拒绝为该构建安装。

1. 先运行对应产品的 `check-*.cmd`，检查版本与配套文件。
2. 检查通过后，保存工作并退出对应 IDE，以当前 Windows 账号右键选择“以管理员身份运行”安装入口。
3. 看到 `Configured PyCharm` 或 `Configured GoLand` 后，再从原 EXE 或快捷方式启动。
4. 如安装报错，请保留错误输出；不要把打开 CMD 窗口当作安装成功。

如果缺少根目录的 `local-state-agent.jar` 或 `agent.sha256`，先在项目目录执行：

```powershell
powershell.exe -NoProfile -File .\scripts\build-agent.ps1
```

默认使用本机 `C:\Program Files\JetBrains\PyCharm 2026.3.0.2` 中的构建工具。
当前 Release 的 `release-validation.zip` 仍是发布流程的校验附件，不是上述脚本的安装包。

恢复时使用安装时的 Windows 账号。PyCharm 和 GoLand 的恢复入口各自处理对应产品。

## 恢复 GoLand 的原有启动设置

1. 使用原来运行配置脚本的 Windows 账号，保存工作并退出 GoLand。
2. 在项目目录运行 `scripts/restore-exe.cmd`。
3. 看到 `Original current-user GoLand configuration restored` 后，从原有快捷方式重新启动 GoLand。
4. 如需激活 GoLand，在 IDE 中打开许可证管理界面，使用有效的 JetBrains 许可证登录或激活。

如果提示找不到备份，或提示启动配置在安装后被修改，请先核对 Windows 账号和现有 VM 选项，再根据手头的备份恢复。

## 查看发布校验结果

1. 在 GitHub 的 Releases 页面打开对应的测试版本，下载 Assets 中的 `release-validation.zip`。
2. 解压并打开 `release-validation.json`，核对 `tag` 和 `commit`。

[项目说明](docs/project.md)
