# 心迹公开发布检查清单

适用版本：0.3.0  
面向渠道：B 站 / 小红书等侧载分享（暂未上架应用商店）

---

## 0. 发布前必须确认

- [ ] **应用名称与包名不与他人冲突**（见第 7 节，重要）
- [ ] 已确定唯一的发布渠道与联系方式
- [ ] 已备份签名密钥，并确认两台机器都能构建
- [ ] 已确认本次版本号、更新说明与下载文件名
- [ ] 已确认截图和演示数据全部脱敏

---

## 1. 代码与构建

- [ ] `versionCode` / `versionName` 已递增
- [ ] `assembleDebug` 与 `assembleRelease` 均成功
- [ ] 单元测试通过（`./gradlew :app:testDebugUnitTest`）
- [ ] 无遗留 `TODO` / 调试日志 / 测试入口
- [ ] release 使用正式签名，未使用 debug 签名
- [ ] `allowBackup=false`、`dataExtractionRules` 生效
- [ ] Manifest 中没有 `INTERNET` / `ACCESS_NETWORK_STATE` / `FOREGROUND_SERVICE`
- [ ] 未引入广告、统计、推送或崩溃上报 SDK
- [ ] Room schema 版本与迁移策略已确认
- [ ] ProGuard / R8 若开启，已完成全功能回归

## 2. 包体与签名

- [ ] 记录 APK 大小与 SHA-256
- [ ] 用 `apksigner verify --print-certs` 确认签名
- [ ] 用 `aapt dump badging` 确认 package、versionCode、versionName、minSdk、targetSdk
- [ ] 用 `aapt dump permissions` 确认权限清单
- [ ] 在干净设备上安装 release 包（不是只测 debug）
- [ ] 覆盖安装上一版本，确认数据保留

## 3. 功能回归

- [ ] 首次引导 4 步完整
- [ ] 每日打卡保存、修改、补记
- [ ] 日记新增、编辑、删除
- [ ] 药物新增、编辑、服药打卡、按需记录、撤销与逐条删除
- [ ] 定时药物只有「服用 / 已服」切换按钮，再点可撤销误点
- [ ] 底部 5 个标签：今天、药物、趋势、科普、我的
- [ ] 科普：252 条内容（疾病 127 / 症状 23 / 药物 102）、3 个分类、搜索、独立二级详情页、原生表格与横向滚动、来源与 CC BY-SA 4.0 标注
- [ ] 精神分裂症谱系：阳性/阴性症状、关注方向选项、首页/日期详情/CSV 显示强度
- [ ] 任意症状（不要求选择精神分裂症谱系）均可使用 0–10 强度滑块；默认未评分、可清除
- [ ] 数据库 v2 → v3 迁移：覆盖安装旧版本后旧记录可读
- [ ] 24 个量表入口、分类与最近记录
- [ ] PHQ-9 / GAD-7 / PHQ-2 / PHQ-15 / MDQ / OCI-R / PCL-5 / ISI / DASS-21 完整作答与结果
- [ ] 量表授权与来源说明已确认（见 NOTICE.md）
- [ ] 趋势 7/14/30/90 天与空数据状态
- [ ] 中英文切换后，主要页面、日期、通知、量表、科普与 PDF 均正确
- [ ] 诊断记录：ICD-10 / ICD-11 / DSM-5 搜索、自定义、编辑、删除
- [ ] 复诊 PDF 预览、保存、打开、分享；中英文与图表正常
- [ ] 「报告版本」可选择医生详细版 / 一页摘要，预览与导出均使用所选版本
- [ ] 医生详细版：摘要页、症状概览、用药/量表页与 2 列大图分页正确
- [ ] 一页摘要：11 张症状图、诊断、用药、量表与说明在 1 页内
- [ ] 两种版本每个症状都有折线图和平均强度虚线；无「症状频率与平均强度」条形表
- [ ] CSV 在 Excel 中中文正常
- [ ] 加密备份导出、错误密码、恢复、恢复后提醒同步
- [ ] 安全计划与联系人
- [ ] 紧急支持拨号入口（只打开拨号盘）
- [ ] 应用锁、生物识别、自动锁定、阻止截屏
- [ ] 删除全部数据后回到引导
- [ ] 深色 / 浅色模式全部主要页面
- [ ] 大字体 / 显示大小 1.3× 以上
- [ ] 系统导航栏、手势导航、横屏（如支持）

## 4. 提醒专项

- [ ] 通知权限允许 / 拒绝两种状态
- [ ] 通知渠道被关闭时的提示
- [ ] 精确闹钟允许 / 不允许
- [ ] 10 秒测试提醒
- [ ] 每日提醒与用药提醒在后台、锁屏、重启后
- [ ] 点击通知进入正确页面
- [ ] 脱敏通知不显示药名、症状、日记
- [ ] 国产 ROM 自启动 / 电池设置指引与实际一致

## 5. 设备矩阵

至少覆盖：

- [ ] Android 8 / 9（API 26–28）
- [ ] Android 12（API 31，精确闹钟权限变化）
- [ ] Android 13（API 33，通知权限）
- [ ] Android 14 / 15（API 34/35）
- [ ] 一台国产 ROM（小米 / 华为 / OPPO / vivo 任一）
- [ ] 小屏手机与大屏手机
- [ ] 深色模式 + 大字体组合

模拟器只能验证逻辑，**提醒与后台行为必须在真机验证**。

## 6. 文档与隐私

- [ ] `README.md` 版本、功能、构建说明与实际一致
- [ ] `USER_GUIDE.md` 安装、备份、提醒排查完整
- [ ] `PRIVACY.md` 与实际权限、备份范围一致
- [ ] `CHANGELOG.md` 与 `release-notes/v0.3.0.md` 一致
- [ ] `LICENSE` 与 `NOTICE.md` 存在
- [ ] 应用内「使用条款」「隐私说明」「提醒帮助」「问题反馈」可打开
- [ ] 隐私说明包含：不联网、加密、备份范围、卸载丢失、危机资源
- [ ] 发布页提供 SHA-256 与版本号
- [ ] 发布页提供卸载 / 换机备份提醒
- [ ] 发布页提供反馈方式

## 7. 名称与包名冲突（发布前处理）

现状：

- 包名：`com.mindtrace.app`
- 显示名：心迹 / MindTrace

风险：

- Google Play 上已有名为 **MindTrace: CBT Thought Diary** 的应用，包名相近（`com.MindTrace.app`）。Android 包名大小写敏感，技术上不冲突，但品牌容易混淆。
- 中文「心迹」已有多个同名或近名应用，搜索结果中难以区分。
- 如果未来上架应用商店，可能遇到名称审核、商标或用户混淆问题。

建议在公开发布前二选一：

- **A. 更名**：换一个更独特的显示名与包名（包名一经发布不应再改，否则无法覆盖升级）。
- **B. 保留**：在发布页明确标注「心迹是独立开发的开源项目，与同名应用无关」，并保留当前包名。

无论选择哪种，都请：

- [ ] 在应用商店 / 搜索引擎检索名称
- [ ] 确认没有使用他人的商标或图标
- [ ] 在发布页写清楚开发者身份与项目地址
- [ ] 若已发布过旧包名，保留签名密钥以便覆盖升级

## 8. 分发与账号

- [ ] 发布账号使用独立密码与两步验证
- [ ] 下载链接可长期访问（网盘需检查有效期）
- [ ] 网盘文件附带 SHA-256
- [ ] 不在公开仓库提交 `keystore.properties`、`.jks`、`local.properties`
- [ ] 签名密钥至少两处离线备份（密码分开保存）
- [ ] 发布页注明「个人开发、早期版本、可能有 bug」

## 9. 医疗与合规边界

- [ ] 应用内与发布文案均声明「不是医疗器械」
- [ ] 不提供诊断、处方、用药调整或治疗建议
- [ ] 量表说明为筛查工具，不作为诊断
- [ ] 自伤风险有紧急支持入口
- [ ] 危机资源注明适用地区
- [ ] 未成年人提示在监护人知情下使用
- [ ] 不收集任何可识别个人身份的信息
- [ ] 如需在中国大陆上架应用商店，另行评估资质与备案要求
- [ ] 如需上架 Google Play，重新评估 `USE_EXACT_ALARM` 政策与医疗类应用要求

## 10. 发布后

- [ ] 置顶安装、备份、提醒与危机资源说明
- [ ] 收集反馈时要求使用「复制诊断信息」，避免公开个人病情
- [ ] 建立版本更新记录
- [ ] 严重问题（数据丢失、无法启动）优先修复并发布
- [ ] 不承诺响应时间与治疗效果

---

## 附：常用验证命令

```bash
# 构建
./gradlew :app:assembleRelease

# 版本与权限（$ANDROID_HOME 指向本机 Android SDK）
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump badging app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME/build-tools/35.0.0/aapt" dump permissions app/build/outputs/apk/release/app-release.apk

# 签名
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk

# SHA-256（Windows）
certutil -hashfile app/build/outputs/apk/release/app-release.apk SHA256
# macOS / Linux
shasum -a 256 app/build/outputs/apk/release/app-release.apk

# 安装到模拟器或真机
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/release/app-release.apk
```
