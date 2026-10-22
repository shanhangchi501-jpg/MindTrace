# 第三方组件与内容来源

## 量表

心迹内置的量表仅用于自评筛查、趋势追踪和就诊沟通，**不构成诊断**。分数与分级来自各量表的公开常用评分标准；部分量表在应用内使用了简化展示或简化计分，已在该量表的说明中标注。

### 可免费用于临床与研究的量表

- **PHQ-9 / PHQ-2 / PHQ-15** — Kroenke, Spitzer, Williams 等。Pfizer 教育资助项目；无需许可即可复制、翻译、展示或分发。
- **GAD-7 / GAD-2** — Spitzer, Kroenke, Williams, Löwe。无需许可即可复制、翻译、展示或分发。
- **PCL-5 / PC-PTSD-5** — U.S. Department of Veterans Affairs, National Center for PTSD。公共领域。
- **WHO-5 / WHODAS 2.0** — World Health Organization。可免费用于非商业目的，请保留来源说明。
- **AUDIT-C** — Bush 等 / World Health Organization。非商业使用请保留来源说明。
- **ASRS-5** — World Health Organization / Ustun 等。非商业使用请保留来源说明。
- **SCOFF** — Morgan, Reid & Lacey。临床与研究常用，请保留来源说明。

### 需要确认电子版/再分发条款的量表

以下量表在临床与研究中使用广泛，但可能受版权保护，电子版再分发或商业使用可能需要作者/出版方许可。公开发布或商业使用前请逐项确认：

- **MDQ** — Hirschfeld 等（2000）
- **ASRM** — Altman 等（1997）
- **OCI-R** — Foa 等（2002）
- **ISI** — Morin 等
- **EPDS** — Cox, Holden & Sagovsky（1987）
- **DAST-10** — Skinner（1982）/ NIDA 常用版本
- **DASS-21** — Lovibond & Lovibond（1995）
- **PSQI** — Buysse 等（1989）
- **PSS-4** — Cohen, Kamarck & Mermelstein（1983）
- **Rosenberg Self-Esteem Scale** — Morris Rosenberg（1965）
- **AQ-10** — Allison, Auyeung & Baron-Cohen（2012）
- **ITQ** — Cloitre 等 / International Trauma Consortium

### 简化版本说明

- **PSQI**：应用内为 19 项简化自评版，未计算完整睡眠效率。
- **WHODAS 2.0（12 题）**：使用简化总分，未使用官方复杂加权。
- **ITQ**：结果展示 PTSD / DSO / 功能受损三个分值，总分为简化展示，不能替代 ICD-11 复杂性 PTSD 的专业评估。
- **DASS-21**：结果同时显示抑郁、焦虑、压力三个分量表。

中文与英文题目采用常用公开表述或为适配应用而作的简化表述。如果你发现表述与授权版本不一致，请以原作者发布的正式版本为准。

## 科普内容（0.2.1 起）

「科普」页共 **252 条**内容，只保留疾病、症状与药物三类，使用两个开放知识库：

### 1. 多意识体系统 Wiki（150 条）

疾病（127 条）与症状（23 条）内容取自 **Multiple Personality System Wiki（多意识体系统 Wiki）**，由该项目的贡献者编写。

- 仓库：https://github.com/mps-team-cn/Multiple_personality_system_wiki
- 在线站点：https://wiki.mpsteam.cn/
- 许可：**Creative Commons Attribution-ShareAlike 4.0 International（CC BY-SA 4.0）**
- 内容范围：疾病诊断、临床症状、创伤与疗愈等
- 应用内每一条内容都显示来源条目名称与来源链接；完整条目列表与来源网址见 `app/src/main/assets/education.json`
- 正文在构建时解析为结构化块，Wiki 中的表格会渲染为原生表格
- 部分条目为控制篇幅做了节选，并在详情页标注
- 如单个词条内另有来源、版权或许可声明，以该词条内声明为准；公开发布前请逐项核对

### 2. 维基百科（102 条药物）

药物内容取自中文与英文维基百科对应条目，由维基百科贡献者编写。

- 来源：中文维基百科、英文维基百科
- 许可：**CC BY-SA 4.0**
- 覆盖范围：SSRIs、SNRIs、其他抗抑郁药、三环类、MAOIs、典型与非典型抗精神病药、心境稳定剂、苯二氮䓬类、Z 类药物、抗焦虑药、ADHD 用药、痴呆用药、成瘾治疗药等主要精神科药物
- 应用内每条药物都显示中英文来源链接
- 药物内容有中英文两版，并附分类别的安全提醒

### 授权边界

- 科普文字采用 **CC BY-SA 4.0**，与采用 MIT 许可的应用源代码**分开授权**
- 如果你再分发或修改这些科普文字，需要保留署名、注明许可，并以相同方式共享
- 多意识体系统 Wiki 与维基百科均不对本应用作出背书
- 许可全文：https://creativecommons.org/licenses/by-sa/4.0/

### 医疗边界

多意识体系统 Wiki 是社群协作知识库，维基百科也不是临床指南。科普内容仅供健康教育，不构成诊断、处方或用药建议；具体诊断、药物剂量、相互作用与停药请遵医嘱。

## 开源组件

- Kotlin — Apache License 2.0
- Jetpack Compose / AndroidX — Apache License 2.0
- Room — Apache License 2.0
- SQLCipher for Android (Zetetic) — Community Edition, BSD-style license
- Material Symbols / Material Icons — Apache License 2.0
- kotlinx.coroutines / kotlinx.serialization — Apache License 2.0

## 危机资源

- 全国心理援助热线：12356（国家卫生健康委员会统一号码，接听时间以当地为准）
- 急救：120
- 报警：110

危机资源为公共信息，应用内可自行编辑或添加联系人。热线可用性与接听时间可能随地区变化，紧急情况请直接联系急救服务。
