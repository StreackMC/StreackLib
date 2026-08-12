# StreackLib 项目记忆

## 项目概述
- StreackLib 是 Minecraft 前置库，版本 0.6.1（pom.xml 已确认；此前误记 0.6.0）
- 目标：让 Java 像 JavaScript 一样易用
- 构建：Java 21 + Maven，目标 Paper 1.21.8 / Spigot 1.21.5
- groupId: `com.github.streackmc`

## 架构层次（自上而下）
1. **入口层**：`forBukkit`（JavaPlugin）→ `StreackLib`（ENV 内部类 + manager）
2. **后端抽象层**：`StreackLibDefaultBackend`（抽象）← `StreackLibBukkitBackend`（Bukkit 实现）
3. **功能模块层**：HTTPServer、SConfig、SMail、SDatabase
4. **工具层**：SEventCentral、SFile、MCColor、nbtHandler
5. **基础设施**：logger、updateChecker、StreackLibNewable

## Git 规范
- 提交时 username: `Neonai`，email: `neonai+coding@streack.top`
- 这两个参数只能在命令行携带，不能写入配置文件
- 所有修改必须用 Git 跟踪

## 模块状态备注
- SDatabase（0.6.0 新增，0.6.1 审查）：后端已完整实现（Backend接口、SqliteBackend/MysqlBackend、SdbManager引用计数连接池、SdbAction事务会话）
- ⚠️ SDatabase 已知缺陷（2026-08-12 审查）：① UPDATE/MERGE 操作上下文不可用（buildSQL 抛 UnsupportedOperationException / toPrepared 拼出非法 `SET ?`，须用原始 SQL）；② 操作链 `.next()` 无法经 `apply()` 执行（toPrepared 拼多语句，JDBC prepareStatement 不支持）；③ SELECT 投影列在参数化路径未加反引号 —— **2026-08-12 已修复（同步转义表名/CTE 名/SELECT 列）**；④ `SdbActionContext` 构造器 package-private，文档「上下文链/WITH」示例 `new SdbActionContext(...)` 外部不可编译；⑤ 空 filter 恒为 `WHERE 1=1`（无害）；⑥ 文档 MySQL 示例误用 root（代码拒绝 root）
- ✅ SQL 注入防御（2026-08-12 复核 + 修复）：过滤条件的「值」已参数化（安全）；**执行路径 `buildPreparedSQL` 现已对全部标识符做反引号转义**——表名（SELECT/UPDATE/MERGE/DELETE/CREATE/ALTER/DROP/TRUNCATE + 默认分支）、WITH 的 CTE 名、SELECT 投影列，与预览路径 `buildSQL` 一致，标识符注入面已闭合。唯一残留风险：`act(String)` 原始 SQL 无任何防护（调用方自担）。文档「全部使用 PreparedStatement 防注入」表述仍略过度——标识符是转义而非参数化。
- SConfig：66.4KB，项目最大源文件，支持 7+ 种配置格式
- HTTPServer：基于自定义 NanoHTTPd fork
- SMail：支持 SMTP 和 DKIM SELFSIGN 两种模式
- SLDB 已移除（设计与 SQL 架构不兼容）
- SdbManager 全静态化，不再实例化使用
