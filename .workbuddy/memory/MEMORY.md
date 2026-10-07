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
- SConfig：66.4KB，项目最大源文件，支持 7+ 种配置格式
- 🔴 SConfig 已知缺陷（2026-10-07 实测复现，均有最小复现用例）：
  ① **顶层出现 null 值即加载失败（JSON / YAML / 全格式）** —— `new SConfig("{\"b\":null}", JSON, null)`
     与 `new SConfig("b:\n", YAML, null)` 都抛 `RuntimeException: 无法加载配置文件：null`，
     根因 `cache = new ConcurrentHashMap<>(loaded)` 不接受 null value（`SConfig.java` 约 409 / 1495 行）。
     **边界：只有最外层那一层会炸**——`{"a":{"b":null}}` 与项目自己的 `config.yml`（null 都在嵌套层）
     都能正常加载，因为 ConcurrentHashMap 只包了顶层，嵌套 Map 仍是允许 null 的 HashMap。
  ② **`save()` 对 JSON / TOML 写出 0 字节文件（数据丢失）** —— `getWriter(OutputStream)` 返回的
     `OutputStreamWriter` 从未 flush，而 Gson 的 `toJson(obj, Writer)` 也不会 flush；
     YAML / PROPERTIES 正常，INI 会丢新增键（疑似设计限制）
  ③ **解析失败静默返回空 Map** —— `BackendJSONc.load` 里 `catch (Exception ignore) {} return new HashMap<>()`；
     `BackendJSON` 对非对象/非数组根也返回空 Map。配置文件场景是「fail-soft」，但调用方无法区分
     「内容确实为空」与「格式坏了」
  ④ 代码注释称 JSONC「支持注释、尾随逗号」，但 Gson 2.10.1 的 `setLenient()` 并不接受尾随逗号
     （实测 `{"a":1,}` → 空 Map）
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
  该 Map 构造器绕过 ConcurrentHashMap 因而容忍顶层 null，但 `getRawData()`（`Map.copyOf`）仍会 NPE。
