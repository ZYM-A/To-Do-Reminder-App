# v0.2.0 验证记录

日期：2026-09-10

## 已执行并通过

- `:app:assembleDebug`：生成 versionName 0.2.0 / versionCode 2 的 APK。
- `:app:testDebugUnitTest`：31 项测试全部通过，0 失败、0 错误。
  - 14 项原有待办时间规则回归测试。
  - 12 项纪念日计数、每年重复、跨年、闰年和输入校验测试。
  - 3 项 Robolectric SQLite 测试：从旧数据库升级保留待办全部字段；纪念日重开后保存、编辑、删除；无效输入不覆盖已有记录。
  - 2 项 Robolectric Compose 操作测试：空名称拒绝保存、填写后保存年度纪念日；倒数卡片显示及点击编辑。
- `:app:lintDebug`：0 错误、0 警告。
- 与 GitHub 已发布的 v0.1.0 APK 比较，新版签名证书 SHA-256 一致。
- `git diff --check`：通过。

最终构建返回 BUILD SUCCESSFUL。

## 升级与范围

数据库版本从 1 升级至 2，仅创建 anniversaries 表，不修改或删除 tasks 表。覆盖安装无需卸载旧版。

Robolectric 测试使用模拟 Android 9（API 28）环境。测试数据库是隔离的测试数据，不读取手机上的真实任务。
尚未在实体手机上检查页面布局、安装升级和锁屏通知；电脑上的模拟测试不等同于真机验收。

纪念日使用公历自然日计数，今天为 0 天。2 月 29 日每年重复时，平年落在 2 月 28 日。此功能不发送系统到点通知，原有待办提醒逻辑未改动。

## 报告

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/outputs/apk/debug/app-debug.apk`
