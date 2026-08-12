# SDatabase

自 0.6.0 版本加入的`SdbDatabase` 提供了一套面向 SQL 数据库的链式操作 API。通过断言树和操作上下文，将 SQL 的"增删改查"抽象为一种简洁的调用方式。

> 本文由 AI 生成，可能不准确。

## 前置：配置档案

使用本模块前，需要在 StreackLib 配置（`config.yml`）中定义一个数据库档案：

```yaml
databases:
  default:
    mode: sqlite
    file: "plugins/StreackLib/data.db"
```

MySQL 档案示例：

```yaml
databases:
  mydb:
    mode: mysql
    host: "localhost"
    port: 3306
    database: "mydb"
    user: "root"
    password: ""
```

### 档案配置项

| 键 | SQLite | MySQL | 默认值 |
|:--|:------:|:-----:|:------|
| `mode` | `sqlite` | `mysql` | （必填） |
| `file` | 文件路径 | — | `{dataPath}/database.db` |
| `host` | — | 数据库主机 | `localhost` |
| `port` | — | 数据库端口 | `3306` |
| `database` | — | 数据库名 | `""` |
| `user` | — | 用户名（**不能使用 `root`**） | `""` |
| `password` | — | 密码 | `""` |

## 连接并操作

先引入：

```java
import com.github.streackmc.StreackLib.types.SDatabase.*;
```

### 语法糖一键执行

```java
SdbDatabase db = new SdbDatabase("default");

// ❶ 查询
SdbDataEntry rows = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
    ctx.table("users")
       .filter(new SdbStatement().equal("status", "active"))
       .limit(10));

// ❷ 修改
db.act(SdbEnums.ACTION_TYPE.UPDATE, ctx ->
    ctx.table("users")
       .filter(new SdbStatement().equal("id", "42"))
       .param(/* ... */));

// ❸ 删除
db.act(SdbEnums.ACTION_TYPE.DELETE, ctx ->
    ctx.table("logs")
       .filter(new SdbStatement().smaller("created_at", "2024-01-01")));
```

> 语法糖内部自动使用 try-with-resources，会在执行后提交事务。**适用于单条语句场景**。

### 事务控制（多条 SQL 在同一事务内）

```java
try (SdbAction action = db.act()) {
    action.apply(ctx1);   // 事务内 SQL①
    action.apply(ctx2);   // 事务内 SQL②
    action.commit();      // 提交——两条的修改同时生效或回滚
}

// close() 时若未 commit / rollback，自动执行 rollback
```

### 直接执行原始 SQL

```java
SdbDataEntry result = db.act(
    "CREATE TABLE IF NOT EXISTS players (id INT PRIMARY KEY, name TEXT)"
);
```

> **注意**：原始 SQL 由调用者负责防范注入。对于用户输入的场景，请使用语法糖或断言树构建查询。

## 过滤（断言树）—— `SdbStatement`

断言树是 SDatabase 的核心。它替代了手写 `WHERE` 子句，通过链式组合条件来构建查询过滤。

### 基本条件

```java
SdbStatement where = new SdbStatement()
    .equal("status", "active")       // status = 'active'
    .larger("score", "100")          // AND score > 100
    .isNotNull("email");             // AND email is not null
```

### 可用条件

| 条件 | 调用 | SQL 等价 |
|:--|:--|:--|
| 等于 | `equal(v1, v2)` | `v1 = 'v2'` |
| 不等于 | `unequal(v1, v2)` | `v1 <> 'v2'` |
| 大于 | `larger(v1, v2)` | `v1 > 'v2'` |
| 小于 | `smaller(v1, v2)` | `v1 < 'v2'` |
| 模糊匹配 | `like(v1, pat)` | `v1 LIKE 'pat'` |
| 正则 | `regex(v1, re)` | `v1 REGEXP 're'` |
| 范围 | `between(v1, low, high)` | `v1 BETWEEN low AND high` |
| 集合 | `in(v1, Map)` | `v1 IN (...)` |
| 空值 | `isNull(v1)` / `isNotNull(v1)` | `v1 IS NULL` |

所有 `v2` 参数默认作为字面值；也可指定**从表中查找**：

```java
stmt.equal("player_id", "id", true)
// → `player_id` = `id` （列-列比较，而非定值）
```

### 逻辑组合

```java
SdbStatement activeUsers = new SdbStatement()
    .equal("status", "active");

SdbStatement premium = new SdbStatement()
    .equal("tier", "premium");

// AND
activeUsers.and(premium);

// OR
activeUsers.or(premium);

// NOT
activeUsers.revert();
```

> **注意**：断言一旦创建，条件不可移除或修改。对于需要变式的场景，请使用 `copy()` 或 `copyAll()` 复制副本。

### 限定查询列

```java
SdbStatement stmt = new SdbStatement()
    .selectThat("name")
    .selectThat("email")
    .equal("status", "active");
```

不设置时默认 `SELECT *`。

## 读取结果`SdbDataEntry`

操作返回 `SdbDataEntry` 对象，包含两个字段：

| 字段 | 类型 | 说明 |
|:--|:--|:--|
| `InfluencedLines` | `long` | 受影响的行数（含查询命中的行数） |
| `ResultLines` | `List<SConfig>` | 每条记录对应一个 SConfig，key=列名 |

### 读取行数据

```java
SdbDataEntry entry = db.act(/* ... */);

for (SConfig row : entry.ResultLines) {
    String name  = row.getString("name");     // VARCHAR → String
    int    age   = row.getInt("age");         // INT → int
    double sal   = row.getDouble("salary");   // DECIMAL → double
    boolean vip  = row.getBoolean("vip");     // BIT → boolean
}
```

### JDBC 类型映射

| 数据库列类型 | JDBC 返回 | SConfig 存储 | 推荐读取方法 |
|:--|:--|:--|:--|
| INT / SMALLINT | `Integer` | `putInt()` | `getInt()` |
| BIGINT | `Long` | `putLong()` | `getLong()` 或 `getInt()` |
| FLOAT / DOUBLE | `Double` | `putDouble()` | `getDouble()` |
| DECIMAL | `BigDecimal` | `putBigDecimal()` | `getDouble()` |
| VARCHAR / TEXT | `String` | `putString()` | `getString()` |
| BOOLEAN / BIT | `Boolean` | `putBoolean()` | `getBoolean()` |
| DATETIME / TIMESTAMP | `Timestamp` | `putLocalDateTime()` | `getString()` |
| DATE | `Date` | `putLocalDate()` | `getString()` |
| BLOB | `byte[]` | Base64 → `putString()` | `getString()` |
| NULL | — | 跳过（不写入） | getter 返回默认值 |

> `NULL` 列不会被写入 SConfig，因此 `getString()` 返回 `""`、`getInt()` 返回 `0`、`getBoolean()` 返回 `false`，与 JavaScript `||` 惯用法行为一致。

## 上下文链（高级用法）

多条操作可以串联成链——后续操作自动继承前文的表名等配置，直到被覆盖（遮蔽机制）：

```java
SdbActionContext head = db.act(SELECT)
    .table("users");

SdbActionContext tail = db.act(SELECT)
    .filter(new SdbStatement().larger("score", "100"))
    .limit(5);

head.next(tail); // tail 继承 users 表名，无需重复指定
```

### WITH 子句（CTE）

仅 MySQL 可用（需 >= 8.0 版本），对 `SELECT`/`UPDATE`/`DELETE`/`MERGE` 操作可使用 `WITH` 创建临时表：

```java
// WITH 子表数据来源
SdbActionContext source = db.act(SELECT)
    .table("users")
    .filter(new SdbStatement().equal("active", "1"));

db.act(SELECT, ctx ->
    ctx.with(source, "active_users") // 创建 CTE: active_users
       .table(/* 省略，会从链条中继承 */)
       .filter(new SdbStatement().larger("login_count", "50")));
```

## 内部架构

```
用户代码 ──► SdbDatabase.act() ──► SdbAction (事务会话，AutoCloseable)
                                        │ apply(ctx)
                                        ▼
              SdbManager (全局静态)   SdbActionContext (纯数据，生成 SQL)
                 │ acquire/release      │ .filter()
                 ▼                      ▼
              Backend (接口)         SdbStatement (断言树 → WHERE)
            ┌────┴────┐
     SqliteBackend  MysqlBackend
    (单连接串行)   (HikariCP 连接池)
```

### 连接池管理

- `SdbManager` 全局管理所有 Profile 的连接池（`ConcurrentHashMap` + 静态锁）
- 同一个 Profile 复用同一个 `Backend`，通过**引用计数**决定何时销毁
- SQLite 使用单连接并串行化所有操作（Proxy 拦截 `close()` 实现复用）
- MySQL 使用 HikariCP（最多 10 连接、最少 2 空闲）
- `SdbAction.close()` 自动归还连接并递减引用计数

## 设计限制与注意

> **关于 SQL 注入**：通过本库的完整工具链（Profile 连接数据库—操作上下文—断言）执行的 SQL 操作全部使用 PreparedStatement 防止 SQL 注入攻击。<br>
> 执行原始 SQL 、`toString()` 导出 SQL 命令等方法都没有完善的 SQL 注入检测

> **异常类型**：所有 `SdbDatabase.act()` 方法签名声明 `throws SQLException`——SQL 执行错误直接透传，调用者可精确 catch。连接池创建等底层异常被包装为 `RuntimeException`（这些属于配置错误，应在启动时解决）。

| 异常 | 来源 | 场景 |
|:--|:--|:--|
| `SQLException` | `SdbAction.apply()` | SQL 语法错误、约束冲突、连接断开 |
| `IllegalStateException` | `SdbActionContext.buildSQL()` | 操作上下文配置非法（缺少表名、WITH 链错误等） |
| `IllegalArgumentException` | `SdbManager.createBackend()` | MySQL 使用 root 登录 |
| `RuntimeException` | `SdbDatabase.act()` | 连接池创建失败等底层异常 |
| `NullPointerException` | 各断言方法 | 断言参数为 Null |
