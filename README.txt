GoLand 2026.3 本地授权状态实验

适配版本：GO-263.7307.211
安装位置：C:\Program Files\JetBrains\GoLand 2026.3

使用入口
  scripts\install-for-exe.cmd：以管理员身份执行一次性安装；以后直接打开原 goland64.exe 或快捷方式。
  scripts\restore-exe.cmd：恢复上述安装之前的用户启动配置。
  scripts\repair-startup.cmd：复核 Agent 加载并重建当前用户的 EXE 启动配置。
  scripts\launch.cmd：启动使用独立配置的 GoLand。
  scripts\test-ide.cmd：打开随附 smoke-project，便于复测运行、断点和单步调试。
  scripts\verify-toolchain.cmd：自动验证独立 Go 编译器和 IDE 随附 Delve 调试器。
  scripts\build.ps1：重新编译 Agent 并验证类指纹和转换结果。

直接 EXE 模式
  执行 scripts\install-for-exe.cmd 后，先保存工作并完全退出已有 GoLand 进程，
  再打开 C:\Program Files\JetBrains\GoLand 2026.3\bin\goland64.exe。
  无需每次经过 scripts\launch.cmd。旧终端等启动器可能需要重开以更新环境。
  Agent 存放于 C:\Program Files\JetBrains\GoLand 2026.3\local-agent，
  启动配置和恢复记录存放于 %USERPROFILE%\.goland-state-lab\263.7307.211，
  不再依赖下载文件夹的位置。配置使用原用户的项目和设置，不使用 sandbox。
  安装会备份原用户配置，设置当前用户 GOLAND_VM_OPTIONS，并写入当前用户
  goland64.exe.vmoptions 作为回退；不会修改 GoLand EXE 或系统级环境变量。
  所有用户范围的旧配置保持原样，当前用户的新配置优先。
  安装器核对实际落盘路径，避免 MSIX 宿主把 AppData 重定向到私有目录。
  从旧版升级时会迁移原始恢复记录；直接重新运行安装或修复入口即可。
  写入安装目录需要管理员权限。请使用当前 Windows 账号的管理员提升执行，
  不要切换为另一个管理员账号，否则用户配置会写到另一个账号下。
  无权限或组件检查失败时，脚本停止，现有启动配置保持原样。
  安装目录中的 local-agent 是新增的独立子目录；原始 EXE/JAR 不改写。
  旧用户目录中的 Agent 保留，供尚未退出的旧进程使用。
  卸载或更换 IDE 安装目录后需重新运行安装脚本；恢复入口只还原用户配置，
  不删除安装目录中的共享 Agent 文件。

独立实验启动器（scripts\launch.cmd/scripts\test-ide.cmd）只为新进程设置 GOLAND_VM_OPTIONS，配置、插件、索引和日志放在 sandbox。
不会修改安装目录或系统持久环境变量，也不加载原 ja-netfilter.jar 或激活码文件。
已经启动的普通 GoLand 实例不会应用本实验；请从上述入口启动实验实例。
同一实验实例仍在运行时，启动器会拒绝重复启动，避免覆盖日志或占用配置目录。

机制
  对两个原始类核对 SHA-256 后，在内存中修改本地授权可用性判断，
  并跳过 EAP 启动登录弹窗调用，保留其他初始化及完成逻辑。
  源码位于 src\LocalStateAgent.java。版本升级或类指纹不符时不会应用补丁。
  这不是官方许可证；许可证管理界面仍可能显示未授权。

Revision 2：试用到期分支
  对当前构建的许可证状态计算增加一处修改，将明确的 TrialExpired 返回值
  替换为该构建的可用状态。其他验证错误处理和构建期限判断未改动。
  安装器按组件指纹保存独立 JAR，已有进程继续使用旧文件，重启后加载新文件。
  未修改官方许可证日期、账号或服务端订阅。
  本次通过独立测试 Agent 注入原程序的试用到期异常，确认实际进入到期处理，
  并验证 IDE 编译、运行、断点、变量、单步和继续执行。没有修改系统时间。
  这是到期异常路径重放，不等同于跨日、联网或长时间运行的完整到期测试。
  ExpiryReplayAgent.java 和 expiry-replay-test-only.jar 仅用于隔离测试，
  不写入正式用户启动参数，也不包含在分发 ZIP 中。

Revision 3：关于窗口日期显示
  仅在 AboutDialog 生成窗口文字时，将英文“Subscription is active until”
  后面的日期增加 99 个日历年，例如 December 10, 2026 → December 10, 2125。
  同一窗口的“复制信息”也会使用此显示文字。每次读取原文后计算，不累加。
  通过复制文字列表实现，不修改许可证对象、存储日期、验证条件或服务端订阅。
  此版本保留 Revision 2 的行为；本次新增内容只涉及 About 窗口的显示。
  其他授权窗口仍可能显示原日期；无法识别的日期格式和语言保持原文。
  已通过日期、闰日、重复显示、原列表不变及仅增加一个显示调用点的检查。

Revision 4 / 5：倒计时标题与窗口过期状态
  Revision 4 仅清空自动试用窗口的剩余天数标题，保留按钮和其他内容。
  Revision 5 在该窗口模型中替换三条明确的过期返回结果：TrialExpired 状态、
  许可证过期标记以及超过截止时间。返回本地未过期模型，天数使用整数上限。
  缺少截止日期、许可证缺失及其他错误路径保留；没有改写实际许可证日期。
  这是窗口模型处理，主授权流程的一处到期结果处理仍由 Revision 2 提供。
  五个原始类均校验 SHA-256；不匹配时不应用相应转换。
  隔离进程以 -Xverify:all 启动，强制窗口输入为 TrialExpired，日志返回 tL
  而非 t2，实际窗口不显示倒计时或已过期标题，按钮仍显示。
  测试组件 ModelExpiryProbe 和 TrialDisplayAgent 不包含在正式组件或分发包中。
  本次强制输入覆盖 TrialExpired 路径；另外两条路径经过字节码范围检查，
  尚未做各自的真实到期运行测试。其他通知及授权窗口仍可能显示原状态。

调试连接修正
  本机默认 Temp 路径会导致 JBR AF_UNIX 套接字连接报 Invalid argument。
  独立 Java 探针在不加载 Agent 时也能复现，因此不是授权补丁导致。
  启动器为本次 IDE 进程设置短临时目录 C:\jb-lab-sockets，并设置
  jdk.net.unixdomain.tmpdir。全局 TEMP、TMP 和防火墙设置不会改变。
  本机修正后内部 HTTP 服务正常启动，GoLand 内断点、变量查看和单步调试通过。

2026-11-11 验证结果
  正常退出并重新启动 IDE：通过；项目、运行配置和断点保留，无登录/激活弹窗。
  GoLand 内编译和运行：输出 smoke result=42，退出码 0。
  GoLand 内调试：main.go 第 7 行命中断点，读取 value=21、result=42；
  F8 回到调用位置，F9 继续后 Debugger finished with the exit code 0。
  独立工具链：单元测试、编译、运行、断点、变量读取、单步、正常退出均通过。
  详细记录：verification\验证结果.txt、verification\toolchain-report.json。

验证边界
  上述短时工作流没有发现新的授权拦截，不代表未来任何时间、任何功能都无限制。
  没有验证跨系统重启、超过订阅/构建期限、长时间运行、升级及所有第三方插件。
  AI、云端服务等仍需要服务端认可的订阅和账号权益，本实验不会提供这些权益。
  scripts\verify-toolchain.cmd 验证独立工具链；IDE 界面测试结果单独记录，不能混同。

若已安装直接 EXE 模式，执行 scripts\restore-exe.cmd 后重启 GoLand，恢复安装前的启动方式。
未安装直接 EXE 模式时，退出独立实验实例后继续使用原快捷方式即可。
