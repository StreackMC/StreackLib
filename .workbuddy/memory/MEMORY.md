# StreackLib 项目记忆

## 项目概述
- StreackLib 是 Minecraft 前置库，版本 0.6.0
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
- SDatabase（0.6.0 新增）：后端已完整实现（Backend接口、SqliteBackend/MysqlBackend、SdbManager引用计数连接池、SdbAction事务手柄）
- SConfig：66.4KB，项目最大源文件，支持 7+ 种配置格式
- HTTPServer：基于自定义 NanoHTTPd fork
- SMail：支持 SMTP 和 DKIM SELFSIGN 两种模式
- SLDB 已移除（设计与 SQL 架构不兼容）
- SdbManager 全静态化，不再实例化使用
