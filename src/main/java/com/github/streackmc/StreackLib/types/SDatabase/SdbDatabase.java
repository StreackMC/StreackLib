package com.github.streackmc.StreackLib.types.SDatabase;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.errors.ConfigNotFoundException;
import com.github.streackmc.StreackLib.errors.InvaildConfigException;
import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

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
