# 日常 · 安卓日程待办

一个使用 Kotlin 和 Jetpack Compose 开发的中文安卓应用。第一版面向个人使用，数据保存在手机本地，不需要账号或服务器。最低支持 Android 8.0（API 26）。

## 下载测试版

- [版本发布与 APK 下载](https://github.com/ZYM-A/To-Do-Reminder-App/releases)
- [v0.1.0 安装包](https://github.com/ZYM-A/To-Do-Reminder-App/releases/download/v0.1.0/richang-v0.1.0-debug.apk)
- [更新记录](CHANGELOG.md)

打开 Releases 中对应版本，在 Assets 中下载 `.apk` 文件。源码下载包不包含安装包；编译工具、缓存、本机配置和签名密钥也不提交到仓库。

## 获取源码与版本管理

```bash
git clone https://github.com/ZYM-A/To-Do-Reminder-App.git
cd To-Do-Reminder-App
```

`main` 保存开发源码，`v0.1.0` 等标签标记发布版本。后续修改可提交并推送：

```bash
git add .
git commit -m "Describe the change"
git push
```

后续发布请先更新应用版本号和更新记录，完成构建与检查，再创建版本标签，将 APK 上传到对应 Release。当前是开发测试签名；持续覆盖安装需要保持签名密钥一致，密钥应安全保存在本机，不上传公开仓库。


## 已实现

- 今日待办与逾期提示；按月份、日期查看日程；全部任务和已完成筛选。
- 新增、编辑、删除任务；标题、备注、日期、时间；完成和恢复一次性任务。
- 准时或提前 5 / 10 / 30 / 60 分钟提醒，也可以关闭单个任务提醒。
- 每天、每周重复。即使没有勾选完成，也会继续按固定日期提醒；勾选完成将任务日期推进到下一次。
- SQLite 本地持久化；数据库操作放在后台线程。
- 系统通知权限和准时提醒权限入口；修改、完成、删除任务时取消对应旧提醒。
- 设备重启并解锁后、应用更新后、系统时间改变后重新安排提醒。
- 通知点击打开对应任务；浅色和深色主题。

## 打开项目

在 Android Studio 中选择 **Open**，打开本目录，等待 Gradle Sync。

构建配置：JDK 17、Android SDK 35、Build Tools 35.0.0、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20。依赖版本已固定，Gradle Wrapper 附带分发包 SHA-256 校验。

`local.properties` 指向本机 SDK，不提交到版本控制。本次工作使用项目内 `.tools/android-sdk`；换电脑时请由 Android Studio 自动生成自己的 SDK 路径。

## 命令行构建

Windows PowerShell：

```powershell
.\scripts\build.ps1
```

脚本优先使用本次已准备好的 `.tools/gradle-8.11.1`，否则使用 Gradle Wrapper。首次运行需要联网下载依赖。构建完成后输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以在已配置 SDK 和 JDK 的环境中运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

如果官方 Gradle 下载重定向不可访问，可使用可访问的镜像下载相同版本，然后核对官方校验值，解压到 `.tools`。不要禁用 TLS 证书校验或忽略校验值。

## 安装和试用

将 debug APK 传到安卓手机，打开文件安装；手机可能要求为传输文件的应用允许安装来源。首次启动后，点击提示条开启通知及准时提醒权限。

快速验收：

1. 添加一个 2 分钟后的任务，设置“准时”，锁屏等待通知。
2. 点通知，确认打开的是对应任务。
3. 修改另一任务的提醒时间，确认旧时间不再提醒。
4. 删除任务，确认不会再收到该任务通知。
5. 退出并重新打开应用，确认任务仍在。
6. 创建每天重复的任务，勾选完成，确认任务移到下一天。
7. 有未来任务时重启并解锁手机，检查提醒恢复。

Debug APK 用于本地试用。发布应用商店前需要配置正式签名、隐私说明及发布版本检查。

## 第一版的规则与边界

- 日历展示任务当前的待办日期，暂不把重复任务的所有未来日期展开，也不保存每次重复完成的历史记录。
- 提醒时间已错过时：一次性任务尽快补发一次；重复任务只补发最近一次，不连续弹出历史通知。
- 未允许准时提醒时，使用系统非精确提醒，界面明确提示可能延迟；未允许通知时不会弹通知，重新授权并回到应用后恢复调度。
- 强行停止应用后，需要重新打开以恢复提醒；关机期间无法提醒。系统省电策略和通知设置仍会影响通知展示。
- 时间依据手机系统时区。跨时区后重新计算后续调度，第一版不提供独立时区设置。
- 数据仅在本机；不申请联网权限；禁用系统云备份。卸载或清除应用数据会删除任务，第一版尚无导出功能。
- 本次不做手机品牌专属适配。

## 代码位置

```text
app/src/main/java/com/richang/todo/
  MainActivity.kt                  生命周期、系统设置和权限入口
  TodoViewModel.kt                 界面状态与异步操作
  TodoApplication.kt               应用级数据仓库
  data/Task.kt                     任务模型与可独立测试的日期规则
  data/TaskDatabase.kt             SQLite 持久化
  data/TaskStore.kt                串行化数据更新和提醒调度
  reminders/ReminderScheduler.kt   系统闹钟与通知
  reminders/ReminderReceiver.kt    提醒触发与重启恢复
  ui/                             Compose 界面与主题
```

## 测试

- `testDebugUnitTest`：在电脑上运行重复规则、跨日期、夏令时和重复通知防护等单元测试。
- `lintDebug`：检查安卓 API 使用、资源和代码问题。
- `connectedDebugAndroidTest`：需要连接测试手机或启动安卓模拟器；包含数据库重开后保存、更新、删除验证，使用测试包的数据库，不读取用户任务。

实际已执行的检查和结果见 [验证记录](docs/VALIDATION.md)。

## 官方参考

- [AGP 8.9 的 JDK、Gradle 和 SDK 兼容性](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [安卓系统提醒调度](https://developer.android.com/develop/background-work/services/alarms)
- [通知运行时权限](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
