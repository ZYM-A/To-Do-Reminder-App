# 第一版验证记录

日期：2026-09-10

## 已执行

| 检查 | 结果 |
| --- | --- |
| `:app:assembleDebug` | 通过，生成版本 0.1.0 的 APK |
| `:app:testDebugUnitTest` | 14 项通过，0 失败、0 错误、0 跳过 |
| `:app:lintDebug` | 通过，0 错误、0 警告 |
| `:app:assembleDebugAndroidTest` | 通过，生成数据库设备测试包 |
| `apksigner verify --verbose` | 通过，APK v2 签名有效 |
| `aapt dump badging` | 确认包名、启动 Activity、最低 API 26、目标 API 35 和所需权限 |

最后一次联合构建返回 `BUILD SUCCESSFUL`。

## 时间规则测试覆盖

1. 提前提醒。
2. 一次性任务错过提醒后只补发一次。
3. 已完成和关闭提醒的任务不安排闹钟。
4. 每日任务未完成时仍继续提醒。
5. 多日未运行只合并提醒最近一次。
6. 当日提醒时间未到时区分上一期与下一期。
7. 提前提醒落在前一天。
8. 每周重复跨月保持星期。
9. 完成旧的重复任务后跳过已过去日期。
10. 提前完成时仅推进一期。
11. 一次性任务完成后可恢复。
12. 夏令时春季切换保持本地钟点。
13. 夏令时秋季切换保持本地钟点。
14. 系统时间回拨后不重复已发出的提醒。

## 尚未执行

- 未连接真机或启动模拟器，未运行 `connectedDebugAndroidTest`。数据库测试包编译成功不代表设备测试已运行。
- 尚未在设备上做界面截图检查、锁屏/后台通知、重启恢复和权限拒绝后的端到端验证。
- 未做手机品牌专属适配，遵循本次确认的范围。

因此当前交付为可安装的开发测试版，不能把电脑上的构建通过视为所有手机上的提醒效果已验证。真机试用步骤见 README。

## 产物与报告

- App：`app/build/outputs/apk/debug/app-debug.apk`
- 设备测试包：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- 单元测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`
- Lint 报告：`app/build/reports/lint-results-debug.html`

构建工具及缓存只放在项目的 `.tools` 中；Gradle 分发包已核对官方 SHA-256。
