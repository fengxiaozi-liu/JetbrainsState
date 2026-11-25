# 产品兼容性与验证

## 产品配置

| 产品 | 精确构建 | 参数选择 | 用户环境变量 | 独立备份目录 |
| --- | --- | --- | --- | --- |
| GoLand | GO-263.7307.211 | GO-263.7307.211 | GOLAND_VM_OPTIONS | %USERPROFILE%/.goland-state-lab/263.7307.211 |
| PyCharm | PY-263.7307.351 | PY-263.7307.351 | PYCHARM_VM_OPTIONS | %USERPROFILE%/.pycharm-state-lab/263.7307.351 |

本机 GoLand 已更新为 GO-263.7307.354。它不在旧组件的适配范围，安装前应被拒绝。
不能通过改构建号或跳过指纹检查使其获得兼容性。

产品入口共享 `configure-exe.ps1`，从 `products.psd1` 读取配置。
`Check` 模式只读；`Install` 在改动配置前验证产品信息、组件 SHA-256 和五个原始类指纹。
随后在 IDE 的 `local-agent` 子目录保存按组件指纹命名的 JAR，实际启动 Java 自检，再写当前用户参数。
恢复操作不需要构建工具，不删除共享 JAR，不修改另一产品的用户参数，也不覆盖他处新改过的设置。
原有 `restore-exe.cmd` 仍默认恢复 GoLand，保持历史入口兼容。

PyCharm 本机的机器级 PYCHARM_VM_OPTIONS 指向已不存在的旧下载目录。
检查模式会报告该问题；安装模式设置有效的当前用户覆盖，保留机器级原值。
恢复后若又暴露出原机器级失效路径，应单独修复该旧设置。

## PyCharm 精确适配

`PyCharmStateAgent` 仅在显式选择 PY-263.7307.351 时注册。
其类指纹与 GoLand 配置分离，按本版本实际方法签名转换；运行时不猜测新版本映射。

| 类 | 本次适配范围 |
| --- | --- |
| com/intellij/ide/M/G/j | 本地状态布尔判断 |
| com/intellij/ide/M/G/Zm | 指定状态计算方法的一处到期结果引用 |
| com/intellij/ide/M/V/i/k | 自动试用窗口的倒计时标题 |
| com/intellij/ide/M/V/Z/Zm | 三条明确过期模型返回，保留缺少日期的错误返回 |
| com/intellij/ide/actions/AboutDialog | 复用仅对英文日期文字加 99 年的显示处理 |

没有改写签名许可证、真实许可证日期或服务端订阅。
没有照搬 GoLand 的启动弹窗修改，因为 PyCharm 对应方法签名和产品流程不同。
PyCharm 自己的 Trial 欢迎页、Trial 标记和通知可能继续显示真实试用状态；共享窗口的修改不覆盖这些产品界面。
“组件启动”“目标类转换”“真实过期后仍可正常工作”是不同层次的验证，不能互相替代。

## 本地构建与验证

```powershell
powershell.exe -NoProfile -File .\scripts\build-agent.ps1
powershell.exe -NoProfile -File .\scripts\configure-exe.ps1 -Product PyCharm -Mode Check
```

构建脚本使用当前 PyCharm 的 JBR 和 ASM，编译两个产品的运行组件，运行 `VerifyPyCharm`：
逐一核对五个类指纹、类结构、精确修改数量、修改外指令不变和篡改输入拒绝。
校验通过后才替换原组件并写入 `agent.sha256`。
`AgentCompatibility` 随 JAR 提供独立只读检查；验证工具和测试探针不打入组件。

本地的 `verification/pycharm-compat` 保存隔离进程日志和测试记录，继续被 Git 忽略。
真实到期、联网响应、长时间使用和所有 Pro 功能尚需各自的运行验证；不能承诺永久或跨版本适用。
本次不更改用户手动整理的 Release 校验工作流。
