package com.github.streackmc.StreackLib.types.SDatabase;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.errors.ConfigNotFoundException;
import com.github.streackmc.StreackLib.errors.InvaildConfigException;
import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * <h2>SdbDatabase</h2>
 * 对 SQL 数据库的封装，使得数据库像 Excel 一样简单。
 *
 * <h3>操作模型</h3>
 * <pre><code>
 * // 完整手动控制（同一事务多条 SQL）
 * try (SdbAction action = db.act()) {
 *     action.apply(ctx1);
 *     action.apply(ctx2);
 *     action.commit();
 * }
 *
 * // 语法糖一键执行
 * SdbDataEntry r = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
 *     ctx.table("users").filter(stmt));
 *
 * // 直接执行 SQL
 * try (SdbAction action = db.act()) {
 *     action.apply(rawSql);
 *     action.commit();
 * }
 * </code></pre>
 *
 * @since 0.6.0
 * @author kdxiaoyi
 */
public class SdbDatabase extends StreackLibNewable {

  // ==========================================
  // Fields
  // ==========================================

  private final SConfig    profileConf;
  final String     profileId;

  /** 获取表前缀 */
  public String tablePrefix() {
    return profileConf.getString("table_prefix", "");
  }

  /** 获取数据库模式（sqlite / mysql），包级权限供 SdbActionContext 使用 */
  String getMode() {
    return profileConf.getString("mode", "sqlite").toLowerCase();
  }

  // ==========================================
  // 构造
  // ==========================================

  /**
   * @param profile 要使用的 Profile 名，为空或 Null 时视作 {@code "default"}
   * @throws Exception            读写/连接错误
   * @throws InvaildConfigException Profile 无效
   * @throws ConfigNotFoundException Profile 不存在
   */
  public SdbDatabase(@Nullable String profile) throws Exception {
    String name = (profile == null || profile.isBlank()) ? "default" : profile;
    this.profileId   = name;
    this.profileConf = StreackLib.ENV.conf.getSection(
        "databases." + name, SConfig.TYPES.JSON, null);

    String mode = profileConf.getString("mode", "").toLowerCase();
    if (mode.isEmpty()) {
      throw new ConfigNotFoundException("Database profile '" + name + "' is missing or has no mode");
    }
    // 预热连接池（将来在首次 act() 时才真正借用）
  }

  // ==========================================
  // act() —— 事务会话
  // ==========================================

  /**
   * 打开一个事务会话。调用者<b>必须</b>使用 try-with-resources 或在 finally 中关闭。
   *
   * <pre><code>
   * try (SdbAction action = db.act()) {
   *     action.apply(ctx);
   *     action.commit();
   * }
   * </code></pre>
   *
   * @return 事务会话
   * @throws SQLException            无法获取数据库连接
   * @throws IllegalArgumentException Profile 配置错误（如 MySQL 使用 root）
   */
  public SdbAction act() throws SQLException {
    try {
      return new SdbAction(
          SdbManager.acquire(profileConf, profileId).borrowConnection(),
          profileId);
    } catch (SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to acquire database connection for profile '" + profileId + "'", e);
    }
  }

  /**
   * 语法糖：创建上下文、执行、自动提交并关闭事务。适用于单条语句场景。
   *
   * <pre><code>
   * SdbDataEntry r = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
   *     ctx.table("users").filter(stmt).limit(10));
   * </code></pre>
   *
   * @param type    操作类型
   * @param builder 用于填充上下文的 Consumer
   * @return 操作结果
   * @throws SQLException       SQL 执行错误
   * @throws IllegalStateException 操作上下文校验失败
   */
  public SdbDataEntry act(SdbEnums.ACTION_TYPE type,
                          Consumer<SdbActionContext> builder) throws SQLException {
    try (SdbAction action = act()) {
      SdbActionContext ctx = new SdbActionContext(type, this);
      builder.accept(ctx);
      SdbDataEntry result = action.apply(ctx);
      action.commit();
      return result;
    } catch (SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Unexpected error during database operation", e);
    }
  }

  /**
   * 使用已构建的操作上下文执行。
   *
   * @param type 操作类型
   * @param ctx  操作上下文
   * @return 操作结果
   * @throws SQLException          SQL 执行错误
   * @throws IllegalStateException 传入操作上下文的数据库不是本数据库，或上下文配置非法
   */
  public SdbDataEntry act(SdbEnums.ACTION_TYPE type, SdbActionContext ctx) throws SQLException {
    try (SdbAction action = act()) {
      if (ctx.database != this)
        throw new IllegalStateException("传入操作上下文的数据库不是本数据库");
      SdbDataEntry result = action.apply(ctx);
      action.commit();
      return result;
    } catch (SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Unexpected error during database operation", e);
    }
  }

  /**
   * 构建一个操作上下文。
   *
   * @param type 操作类型
   * @return 操作上下文
   */
  public SdbActionContext act(SdbEnums.ACTION_TYPE type) throws SQLException {
    return new SdbActionContext(type, this);
  }

  /**
   * 直接执行原始 SQL 并自动提交。
   *
   * <pre><code>
   * SdbDataEntry r = db.act("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY)");
   * </code></pre>
   *
   * <p><b>防注入责任在调用者</b>：本方法底层直接 {@code Statement.execute()}，既不转义也不参数化。
   * 仅适用于固定写死的 SQL（如建表 DDL）。任何含外部输入的值都必须改用
   * {@link #act(SdbEnums.ACTION_TYPE, java.util.function.Consumer)} + 断言树（值走 {@code ?} 占位符）。
   *
   * @param rawSql 原始 SQL 命令（调用者全权负责防注入）
   * @return 操作结果
   * @throws SQLException SQL 执行错误
   */
  public SdbDataEntry act(String rawSql) throws SQLException {
    try (SdbAction action = act()) {
      SdbDataEntry result = action.apply(rawSql);
      action.commit();
      return result;
    } catch (SQLException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Unexpected error during database operation", e);
    }
  }

  // ==========================================
  // 表操作
  // ==========================================

  /**
   * 重命名或删除一个表。
   * <p>
   * 当 {@code newname} 非空时调用 {@code ALTER TABLE ... RENAME TO}（MySQL / SQLite 通用）；
   * 当 {@code newname} 为空或 Null 时调用 {@code DROP TABLE IF EXISTS}。
   *
   * @param existed 已存在的表名
   * @param newname 新表名，为空或 Null 则<b>删除</b>该表（不可恢复）
   * @throws RuntimeException 当数据库操作失败时包装抛出
   */
  public SdbDatabase moveTable(@Nonnull String existed, @Nullable String newname) {
    try {
      if (newname == null || newname.isBlank()) {
        act("DROP TABLE IF EXISTS " + SdbUtils.q(existed));
      } else {
        act("ALTER TABLE " + SdbUtils.q(existed) + " RENAME TO " + SdbUtils.q(newname));
      }
    } catch (SQLException e) {
      throw new RuntimeException("moveTable failed for '" + existed + "' → '" + newname + "'", e);
    }
    return this;
  }

  /**
   * 创建一张新表（含自增主键 {@code id}）。
   * <p>
   * 根据 Profile 配置的数据库类型生成不同的建表语句：
   * <ul>
   *   <li><b>SQLite</b>：{@code id INTEGER PRIMARY KEY AUTOINCREMENT}</li>
   *   <li><b>MySQL</b>：{@code id INT AUTO_INCREMENT PRIMARY KEY}</li>
   * </ul>
   * 如需自定义列定义，使用 {@link #act(String)} 直接执行 DDL。
   *
   * @param name 新表名
   * @throws RuntimeException 当数据库操作失败时包装抛出
   */
  public SdbDatabase newTable(@Nonnull String name) {
    String mode  = profileConf.getString("mode", "").toLowerCase();
    String idCol = mode.equals("mysql")
        ? "id INT AUTO_INCREMENT PRIMARY KEY"
        : "id INTEGER PRIMARY KEY AUTOINCREMENT";
    try {
      act("CREATE TABLE IF NOT EXISTS " + SdbUtils.q(name) + " (" + idCol + ")");
    } catch (SQLException e) {
      throw new RuntimeException("newTable failed for '" + name + "'", e);
    }
    return this;
  }
}

/**
 * <h2>Backend</h2>
 * 数据库后端接口，封装连接池/single-connection的借还操作。
 *
 * @since 0.6.0
 */
interface Backend {

  /**
   * 从池中借出一个连接。
   *
   * @return 一个可用的{@link Connection}。调用者的{@code close()}行为：
   *         <ul>
   *         <li><b>SqliteBackend</b>：被拦截，不真正关闭</li>
   *         <li><b>MysqlBackend</b>：归还给 HikariCP 池</li>
   *         </ul>
   */
  Connection borrowConnection() throws Exception;

  /** 销毁整个连接池/关闭底层连接 */
  void close();
}

/**
 * <h2>MysqlBackend</h2>
 *
 * MySQL HikariCP 连接池后端。构造时创建 DataSource，
 * {@code borrowConnection()} 返回池中空闲连接，调用者的
 * {@code close()} 自动归还连接给池。
 *
 * @since 0.6.0
 */
class MysqlBackend implements Backend {

  private final HikariDataSource ds;

  MysqlBackend(String host, int port, String database, String user, String password) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
    config.setUsername(user);
    config.setPassword(password);

    config.setMaximumPoolSize(10);
    config.setMinimumIdle(2);
    config.setConnectionTimeout(30000);
    config.setIdleTimeout(600000);
    config.setMaxLifetime(1800000);

    config.addDataSourceProperty("cachePrepStmts", "true");
    config.addDataSourceProperty("prepStmtCacheSize", "250");
    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
    config.addDataSourceProperty("useServerPrepStmts", "true");

    this.ds = new HikariDataSource(config);
  }

  @Override
  public Connection borrowConnection() throws Exception {
    return ds.getConnection();
  }

  @Override
  public void close() {
    ds.close();
  }
}

/**
 * <h2>SqliteBackend</h2>
 *
 * SQLite 单连接后端。由于 SQLite 设计上不支持并发写入，此处使用
 * 单连接 + {@code synchronized} 串行化所有借用。
 *
 * <p>
 * 借出的连接是一个代理对象，其{@code close()} 方法被拦截为 no-op，
 * 以支持事务语义下的"归还"操作。真正的关闭只发生在{@link #close()}。
 *
 * @since 0.6.0
 */
class SqliteBackend implements Backend {

  public final File dbFile;
  private final Connection realConn;
  private final Connection sharedConn;

  SqliteBackend(File dbFile) throws Exception {
    this.dbFile = dbFile;
    File parent = dbFile.getParentFile();
    if (parent != null && !parent.exists())
      parent.mkdirs();

    Class.forName("org.sqlite.JDBC");
    this.realConn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
    this.sharedConn = wrapConnection(realConn);
  }

  // ==========================================
  // Backend API
  // ==========================================

  @Override
  public synchronized Connection borrowConnection() {
    return sharedConn;
  }

  @Override
  public synchronized void close() {
    try {
      realConn.close();
    } catch (Exception ignored) {
    }
  }

  // ==========================================
  // Internal
  // ==========================================

  /** 将真实连接的 close() 拦截为 no-op */
  private static Connection wrapConnection(Connection real) {
    return (Connection) Proxy.newProxyInstance(
        Connection.class.getClassLoader(),
        new Class<?>[] { Connection.class },
        new CloseInterceptor(real));
  }

  private record CloseInterceptor(Connection delegate) implements InvocationHandler {
    @Override
    public synchronized Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
      if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
        return null; // 拦截 close()
      }
      return method.invoke(delegate, args);
    }
  }
}
