package com.github.streackmc.StreackLib.types.SDatabase;

import java.sql.Connection;
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
  private final SdbManager manager;
  private final String     profileId;

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
    this.manager     = new SdbManager();

    String mode = profileConf.getString("mode", "").toLowerCase();
    if (mode.isEmpty()) {
      throw new ConfigNotFoundException("Database profile '" + name + "' is missing or has no mode");
    }
    // 预热连接池（将来在首次 act() 时才真正借用）
  }

  // ==========================================
  // act() —— 事务手柄
  // ==========================================

  /**
   * 打开一个事务手柄。调用者<b>必须</b>使用 try-with-resources 或在 finally 中关闭。
   *
   * <pre><code>
   * try (SdbAction action = db.act()) {
   *     action.apply(ctx);
   *     action.commit();
   * }
   * </code></pre>
   *
   * @return 事务手柄
   * @throws Exception 无法获取数据库连接
   */
  public SdbAction act() throws Exception {
    return new SdbAction(
        manager.acquire(profileConf, profileId).borrowConnection(),
        manager,
        profileId);
  }

  /**
   * 语法糖：创建上下文、执行、自动关闭事务（auto-commit）。
   *
   * <pre><code>
   * SdbDataEntry r = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
   *     ctx.table("users").filter(stmt).limit(10));
   * </code></pre>
   *
   * @param type    操作类型
   * @param builder 用于填充上下文的 Consumer
   * @return 操作结果
   * @throws Exception 数据库错误
   */
  public SdbDataEntry act(SdbEnums.ACTION_TYPE type,
                          Consumer<SdbActionContext> builder) throws Exception {
    try (SdbAction action = act()) {
      SdbActionContext ctx = new SdbActionContext(type, this);
      builder.accept(ctx);
      SdbDataEntry result = action.apply(ctx);
      action.commit();
      return result;
    }
  }

  /**
   * 直接执行原始 SQL。
   *
   * <pre><code>
   * SdbDataEntry r = db.act("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY)");
   * </code></pre>
   *
   * @param rawSql 原始 SQL 命令（调用者负责防范注入）
   * @return 操作结果
   * @throws Exception 数据库错误
   */
  public SdbDataEntry act(String rawSql) throws Exception {
    try (SdbAction action = act()) {
      SdbDataEntry result = action.apply(rawSql);
      action.commit();
      return result;
    }
  }

  // ==========================================
  // 表操作
  // ==========================================

  /**
   * 移动或删除一个表。
   *
   * @param existed 已存在的表
   * @param newname 新表名。为空或 Null 则删除
   */
  public SdbDatabase moveTable(@Nonnull String existed, @Nullable String newname) {
    // TODO: implement
    return this;
  }

  /**
   * 新建一个表。
   *
   * @param name 新表名
   */
  public SdbDatabase moveTable(@Nonnull String name) {
    // TODO: implement
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
