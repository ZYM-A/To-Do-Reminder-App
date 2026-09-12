# v0.3.0 验证记录

日期：2026-09-12

## 已执行并通过

- `:app:assembleDebug`：生成 versionName 0.3.0 / versionCode 3 的 APK。
- `:app:testDebugUnitTest`：48 项测试通过，0 失败、0 错误。
  - 14 项待办规则回归测试。
  - 12 项原有纪念日倒数及输入校验测试。
  - 11 项农历测试：香港天文台 2025 / 2026 对照日期、1900—2100 每月首末日往返换算、无效日期拒绝、春节跨年、闰月回退、大小月回退、未来与过去日期、支持范围和中文日期名称。
  - 5 项 SQLite 测试：v1 / v2 升级到 v3 保留数据，纪念日保存编辑删除，农历闰月及历法设置重开后保留，无效输入不覆盖记录。
  - 6 项 Compose 操作测试：原有保存和卡片交互、公农历切换保留日期及其他字段、选闰月后保存、三十切换小月改为廿九且不提供无效日期、切换年份处理缺失闰月。
- `:app:lintDebug`：No issues found（0 错误、0 警告）。
- APK 签名验证通过；与已发布 v0.1.0 安装包证书 SHA-256 一致：
  `9dd6d2ea4af52bd300340038414f861c1aade128e81ff44a88ebfbc6cbfd9940`
- `git diff --check`：通过。

最终构建返回 BUILD SUCCESSFUL。

APK SHA-256：`e0092b59ced4ddb1a38e938efbf3f55051360960acecab3b91311dfa638f534a`

## 日期与升级规则

数据库 v3 增加 `calendar_type` 字段，原记录默认 SOLAR。日期继续保存实际公历日期，选择农历时从该日期还原原农历年月日和闰月标记。历法切换不改变实际日期。

农历每年重复使用农历年，不以公历 1 月 1 日作为跨年边界。没有同名闰月时按同名普通月，三十遇小月按廿九；下一年仍从原始日期计算，不累计回退偏移。未来原始日期不会提前到更早的年份。

支持选择农历 1900—2100 年。换算使用本地 lunar-java 1.7.7，应用不需联网。第三方许可证随 APK 打包。

独立对照来源：[香港天文台 2025](https://www.hko.gov.hk/tc/gts/time/calendar/text/files/T2025c.txt)、[2026](https://www.hko.gov.hk/tc/gts/time/calendar/text/files/T2026c.txt)。

## 验证边界

SQLite 和 Compose 操作测试运行在电脑上的 Robolectric Android 9（API 28）环境。尚未在实体手机上完成布局、安装升级、锁屏通知或重启恢复验证；本次未运行 connectedDebugAndroidTest。

纪念日当前不发送系统到点通知，原待办提醒逻辑未改动。

## 本地报告

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/outputs/apk/debug/app-debug.apk`
