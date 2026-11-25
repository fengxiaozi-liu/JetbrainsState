# 使用说明

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
