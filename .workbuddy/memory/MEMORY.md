# StreackLib 项目记忆

## 项目概述
- StreackLib 是 Minecraft 前置库，版本 0.6.2（pom.xml 已确认）
- 目标：让 Java 像 JavaScript 一样易用
- 构建：Java 21 + Maven，目标 Paper 1.21.8 / Spigot 1.21.5
- groupId: `com.github.streackmc`

## 架构层次（自上而下）
1. **入口层**：`forBukkit`（JavaPlugin）→ `StreackLib`（ENV 内部类 + manager）
2. **后端抽象层**：`StreackLibDefaultBackend`（抽象）← `StreackLibBukkitBackend`（Bukkit 实现）
3. **功能模块层**：HTTPServer、SConfig、SMail、SDatabase、SUDS
4. **工具层**：SEventCentral、SFile、MCColor、nbtHandler
5. **基础设施**：logger、updateChecker、StreackLibNewable

## Git 规范
- 提交时 username: `Neonai`，email: `neonai+coding@kdxiaoyi.top`
- 这两个参数只能在命令行携带，不能写入配置文件
- 所有修改必须用 Git 跟踪

## 模块状态备注
- SDatabase（0.6.0 新增，0.6.1 审查）：后端已完整实现（Backend接口、SqliteBackend/MysqlBackend、SdbManager引用计数连接池、SdbAction事务会话）
- ⚠️ SDatabase 已知缺陷（2026-08-12 审查）：① UPDATE/MERGE 操作上下文不可用（buildSQL 抛 UnsupportedOperationException / toPrepared 拼出非法 `SET ?`，须用原始 SQL）；② 操作链 `.next()` 无法经 `apply()` 执行（toPrepared 拼多语句，JDBC prepareStatement 不支持）；③ SELECT 投影列在参数化路径未加反引号 —— **2026-08-12 已修复（同步转义表名/CTE 名/SELECT 列）**；④ `SdbActionContext` 构造器 package-private，文档「上下文链/WITH」示例 `new SdbActionContext(...)` 外部不可编译；⑤ 空 filter 恒为 `WHERE 1=1`（无害）；⑥ 文档 MySQL 示例误用 root（代码拒绝 root）
- ✅ SQL 注入防御（2026-08-12 复核 + 修复）：过滤条件的「值」已参数化（安全）；**执行路径 `buildPreparedSQL` 现已对全部标识符做反引号转义**——表名（SELECT/UPDATE/MERGE/DELETE/CREATE/ALTER/DROP/TRUNCATE + 默认分支）、WITH 的 CTE 名、SELECT 投影列，与预览路径 `buildSQL` 一致，标识符注入面已闭合。唯一残留风险：`act(String)` 原始 SQL 无任何防护（调用方自担）。2026-08-12 已重写 `docs/types/SDatabase.md`「SQL 注入防御与责任划分」及全部相关 JavaDoc，明确三类职责（✅自动防护：值=PreparedStatement 占位符、标识符=`SdbUtils.q` 反引号转义且注明「转义≠参数化」；⚠️调用者负责：原始 SQL、动态标识符、toString 预览路径）。
- SConfig：约 97KB，项目最大源文件，支持 JSON/JSONC/YAML/YAMLc/YAMLi/TOML/INI/PROPERTIES/NBT/NBTle/SNBT
- ✅ SConfig 缺陷修复（2026-10-07，三项已复现缺陷全部修复，110 项自检全通过）：
  ① **顶层 null 导致加载失败** → 根因 `new ConcurrentHashMap<>(loaded)` 拒绝 null value。
     缓存改由 `newCache()` / `newCache(loaded)` 创建：`Collections.synchronizedMap(new LinkedHashMap<>())`
     （该 Map 允许 null；本类所有读写都已被 `lock` 保护，且未用 ConcurrentHashMap 的复合原子操作）。
     `getRawData()` 同时补上读锁 + 改用 `copyCache()`（原 `Map.copyOf` 同样拒绝 null）。
  ② **`save()` 对 JSON/JSONC/TOML 写出 0 字节** → 全部文本后端统一走
     `writeText(OutputStream, TextWriter)`：写出后强制 `flush()`（只 flush 不 close，流生命周期仍归
     `atomicWrite`/`toString` 管理）。顺带修掉三个同源问题：Gson 加 `serializeNulls()`
     （否则显式 null 字段会被静默丢弃，与 YAML 行为不一致）；INI 顶层标量改为显式抛
     `IllegalArgumentException`（原来静默丢成 0 字节）；`atomicWrite` 失败时清理临时文件。
  ③ **JSONC「尾随逗号」承诺落空 + 失败静默变空 Map** → 新增 `stripTrailingCommas` /
     `skipWhitespaceAndComments` 做字符串感知的预处理（Gson 2.10.1 的 lenient 确实不收尾随逗号）；
     移除 `catch (Exception ignore)`，畸形输入现在抛错并正常触发 `WRONG_FORMAT` 与 `onLoadFailure`。
  修复过程中另外发现并修掉三个「必然失败」的既有缺陷（均已自检覆盖）：
  ④ YAMLc/YAMLi 的 `save()` 100% 抛 NPE「Scalar style must be provided」——
     SnakeYAML 的 `new ScalarNode(tag, value, mark, mark, style)` 既不允许 style=null，
     也不允许 value=null（空值原来直接传了 null），已统一改为 `ScalarStyle.PLAIN` 且空值传 `"null"`；
  ⑤ 全新 NBT 文件永远无法创建 —— 文件不存在时 `reload()` 提前返回，`BackendNBT.rootName`
     未被 `setRootName` 初始化仍为 null，`flush` 直接 `rootName.isEmpty()` 即 NPE，改用 `getRootName()`；
  ⑥ 空 SNBT 文件加载抛 `StringIndexOutOfBoundsException` → 空内容视作空配置，与其它格式对齐。
- ⚠️ SConfig 剩余格式边界（非缺陷，已写入 JavaDoc 与 `docs/types/SConfig.md`）：
  · TOML 语言无 null 字面量 → null 项保存时被省略（整节皆 null 则整节省略）；
  · INI 必须把项写在节里（键形如 `section.key`），顶层标量保存时抛异常；
  · Properties 的 null 退化为空串；JSON/JSONC 写入会丢弃注释；
  · `BackendJSON` 对非对象/非数组根仍返回空 Map（标量根无法用 Map 表示），这是有意的 fail-soft。
- HTTPServer：基于自定义 NanoHTTPd fork
- SMail：支持 SMTP 和 DKIM SELFSIGN 两种模式
- SLDB 已移除（设计与 SQL 架构不兼容）
- SdbManager 全静态化，不再实例化使用
- SUDS（0.6.2 补完，2026-10-07）：UDS 进程间通讯。`SUDSAbsLink`（抽象基类）← `SUDSServerLink` / `SUDSClientLink`，
  连接实体 `SUDSPeer`，报文载体 `SUDSPayload`，协议枚举 `SUDSProtocol{JSON_LINES, RAW}`。
  设计定位：**对齐 JS 的 `Response`/`MessageEvent.data`**（载荷只有 body，没有 headers/status，也**刻意不抄**
  `bodyUsed` 一次性消费——可重复读、不需要 `clone()`）。0.6.2 未发布时按用户决定**去掉了 `<T>`** 以降低理解成本。
  要点：① 客户端必须 `SocketChannel.open(UNIX)`，`ServerSocketChannel` 无 `connect()`；
  ② LF 分帧符由链路在 `send` 时追加，不放进 Payload 字节（保证「收到再转发」可用）；
  ③ 两侧各自为同一条连接生成 `SUDSPeer`，ID 本地唯一、不可跨进程比较；
  ④ 服务端 bind 前探测僵尸 socket（连不上才删），close 时删自己创建的文件；
  ⑤ 载荷-协议错配（RAW 链路发 JSON 载荷）直接抛 `IllegalArgumentException`；
  ⑥ 客户端**不自动重连**，无心跳（后续可加）。文档见 `docs/types/SUDS.md`。
- `SUDSPayload` API（非泛型，构造/消费两侧同名对称）：
  构造 `json(Object)` / `text(String)` / `bytes(byte[])` / `bytes(byte[],off,len)` / `of(byte[],raw)`（最底层，不校验）；
  解析 `parse(byte[] line)`（严格：语法 + 要求对象根，链路接收路径用它，校验一次并缓存）；
  消费 `json()`→`Map`（缓存同一实例、保持键序、可变）/ `config()`→`SConfig` / `text()` / `bytes()` / `as(Class|Type)`。
  RAW 载荷上 `json()/config()/as()` 抛 `IllegalStateException`。
  ⚠️ `config()` 必须传顶层拷贝（SConfig 的 `this.cache = rD` 直接引用不拷贝，否则会污染 `json()` 缓存）；
  该 Map 构造器绕过 ConcurrentHashMap 因而容忍顶层 null；`getRawData()` 自 2026-10-07 修复后
  也不再拒绝 null（改为读锁 + 允许 null 的不可变副本）。
