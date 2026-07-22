package com.github.streackmc.StreackLib.types.SDatabase;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SdbActionContext</h2>
 * 数据库操作上下文，用于链式构建并执行一次数据库操作。
 * <p>
 * 通过 {@link SdbDatabase#act(SdbEnums.ACTION_TYPE, java.util.function.Consumer)} 创建，然后链式设置参数：
 * <pre><code>
 * SdbDataEntry result = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
 *     ctx.table("users")
 *        .alias("u", "users")
 *        .filter(new SdbStatement().equal("name", "abc"))
 *        .limit(10));
 * </code></pre>
 * 
 * <h3>上下文</h3>
 * 一个（串）上下文是由一个数据库开始的，之后的每一个操作上下文都可以接下一个操作上下文，此时前者称作后者的上文，反过来后者是前者的下文。
 * <p>
 * 串联成一串上下文后，可以在任何一个上下文节点通过
 * {@link SdbDatabase#act(SdbEnums.ACTION_TYPE, java.util.function.Consumer)}
 * 或 {@link #toSqlString()} 来生成 SQL，这会从数据库开始，流式拼接直到该节点（含）途中全部操作上下文执行的命令。
 * <p>
 * 之所以称作「上下文」，是因为本链式上下文的全部操作都可以从最初的上下文（也就是发起操作的数据库）继承必要数据而无需重复声明。
 * <p>
 * 上下文的另外一个特点是<b>遮蔽</b>：如果中途某个上下文重新指定了某些数据，那么它的全部下文都会改为从它本身继承而不是从上文继承，直到下一次遮蔽。
 * <p>
 * 当前版本（0.6.0~），该算法的极端时间复杂度为<code>O(n)</code>。
 * 
 * @since 0.6.0
 * @author kdxiaoyi
 */
public class SdbActionContext extends StreackLibNewable {
  /** 操作类型 */
  public final SdbEnums.ACTION_TYPE type;
  /** 操作的数据库对象 */
  public final SdbDatabase database;
  /** 操作的主要表 */
  public String table;
  /** 表的别名映射：key=别名, value=真实表名 */
  public Map<String, String> alias;
  /** 操作参数 */
  public SConfig param;
  /** 操作的断言限制 */
  public SdbStatement filter;
  /** 操作的数量限制 */
  public int limit;
  /** WITH 命令数据来源（CTE 名 → 来源查询） */
  public LinkedHashMap<String, SdbActionContext> with;
  /** 上条命令 */
  public SdbActionContext parent;
  /** 下条命令 */
  public SdbActionContext child;

  /** 声明本操作被其它操作作为前置命令引用 */
  protected void setParent(SdbActionContext ctx) {
    this.parent = ctx;
  }

  /**
   * 将某个操作上下文作为本操作的下一句，构成操作链。
   * 
   * @since 0.6.0
   * @param ctx 下一句操作
   * @return ctx，支持链式调用
   * @throws IllegalStateException WITH 的下一条命令必须是 SELECT/UPDATE/DELETE/MERGE
   */
  public SdbActionContext next(SdbActionContext ctx) {
    if (this.type == SdbEnums.ACTION_TYPE.WITH) {
      switch (ctx.type) {
        case SELECT: case UPDATE: case DELETE: case MERGE: break;
        default:
          throw new IllegalStateException("WITH 的下一条命令必须是 SELECT/UPDATE/DELETE/MERGE，实际为 " + ctx.type);
      }
    }
    this.child = ctx;
    ctx.setParent(this);
    return ctx;
  }

  /** 由 {@link SdbDatabase#action(SdbEnums.ACTION_TYPE)} 创建 */
  SdbActionContext(SdbEnums.ACTION_TYPE type, SdbDatabase database) {
    this.type = type;
    this.database = database;
    this.alias = new HashMap<>();
    this.filter = new SdbStatement();
    this.param = new SConfig("", SConfig.TYPES.JSON, null);
  }

  /** 指定操作的主要表 */
  public SdbActionContext table(String table) {
    this.table = table;
    return this;
  }

  /** 添加单个别名映射 */
  public SdbActionContext alias(String key, String value) {
    this.alias.put(key, value);
    return this;
  }

  /** 批量设置别名映射 */
  public SdbActionContext alias(Map<String, String> alias) {
    if (alias != null) this.alias.putAll(alias);
    return this;
  }

  /** 设置操作参数 */
  public SdbActionContext param(SConfig param) {
    this.param = param;
    return this;
  }

  /** 设置操作的断言限制 */
  public SdbActionContext filter(SdbStatement filter) {
    this.filter = filter;
    return this;
  }

  /** 设置操作的数量限制 */
  public SdbActionContext limit(int limit) {
    this.limit = limit;
    return this;
  }

  /**
   * 设置临时表数据来源。临时表会被命名为 "tmp_" 后接 {@link SdbActionContext#INSTANCE_ID}。
   * <p>
   * <b>仅 {@code SELECT / UPDATE / DELETE / MERGE} 类型支持 WITH 子句。</b>
   * 
   * @since 需求 MySQL >= 8.0
   * @param query 提供临时表数据的 SELECT 操作
   * @throws IllegalArgumentException 数据来源不是 SELECT
   * @throws IllegalStateException    当前操作类型不支持 WITH
   * @apiNote 如果当前操作类型支持但不是 WITH，那么会自动将当前操作视作 WITH 的后续
   * @apiNote 原则上 WITH 操作里面不能嵌套 WITH 操作
   */
  public SdbActionContext with(SdbActionContext query) {
    requireWithCapable();
    if (!SdbEnums.ACTION_TYPE.SELECT.equals(query.type)) {
      throw new IllegalArgumentException("数据来源应使用 SELECT，但实际为 " + query.type);
    }
    if (this.with == null) this.with = new LinkedHashMap<>();
    this.with.put("tmp_" + query.INSTANCE_ID, query);
    return this;
  }

  /**
   * 设置临时表数据来源（指定 CTE 名）。
   * <p>
   * <b>仅 {@code SELECT / UPDATE / DELETE / MERGE} 类型支持 WITH 子句。</b>
   * 
   * @since 需求 MySQL >= 8.0
   * @param query 提供临时表数据的 SELECT 操作
   * @param name  CTE 别名，为 null 或空白时自动命名，自动命名参考 {@link #with(SdbActionContext)}
   * @throws IllegalArgumentException 数据来源不是 SELECT
   * @throws IllegalStateException    当前操作类型不支持 WITH
   * @apiNote 如果当前操作类型支持但不是 WITH，那么会自动将当前操作视作 WITH 的后续
   * @apiNote 原则上 WITH 操作里面不能嵌套 WITH 操作
   */
  public SdbActionContext with(SdbActionContext query, String name) {
    requireWithCapable();
    if (!SdbEnums.ACTION_TYPE.SELECT.equals(query.type)) {
      throw new IllegalArgumentException("数据来源应使用 SELECT，但实际为 " + query.type);
    }
    if (this.with == null) this.with = new LinkedHashMap<>();
    String cteName = (name != null && !name.isBlank()) ? name : "tmp_" + query.INSTANCE_ID;
    this.with.put(cteName, query);
    return this;
  }

  /** 检查当前操作类型是否支持 WITH 子句 */
  private void requireWithCapable() {
    switch (type) {
      case SELECT: case UPDATE: case DELETE: case MERGE: return;
      default:
        throw new IllegalStateException(type + " 操作不支持 WITH 子句，仅 SELECT/UPDATE/DELETE/MERGE 可用");
    }
  }

  /**
   * 判断当前操作是否没有下文。
   * 
   * @return true 时表示当前上下文是最后一个
   * @since 0.6.0
   */
  public boolean isLast() {
    return this.child == null;
  }

  /**
   * 获取下文
   * 
   * @since 0.6.0
   */
  @Nullable
  public SdbActionContext getNext() {
    return this.child;
  }

  /**
   * 获取上文
   * 
   * @since 0.6.0
   */
  @Nullable
  public SdbActionContext getPrevious() {
    return this.parent;
  }

  /**
   * 返回本操作的 SQL 片段（不含链中其它操作）。
   * 
   * @since 0.6.0
   */
  @Override
  public String toString() {
    return buildSQL(this);
  }

  /**
   * 将操作链（从头到当前节点）转为 SQL 命令列表。
   * <p>
   * 从链首开始正序遍历至当前节点，每条操作为一条独立的 SQL 命令。
   * WITH 节点会与它的下一节点合并为一条命令。
   * 
   * @apiNote 对 SQL 注入只有基本检测能力
   * @since 0.6.0
   */
  public java.util.List<String> toSqlString() {
    SdbActionContext head = this;
    while (head.parent != null) head = head.parent;

    java.util.ArrayList<String> sqlBatch = new java.util.ArrayList<>();
    for (SdbActionContext cur = head; cur != null; cur = cur.child) {
      if (cur.type == SdbEnums.ACTION_TYPE.WITH) {
        if (cur.child == null)
          throw new IllegalStateException("WITH 节点后缺少主查询");
        StringBuilder merged = new StringBuilder();
        merged.append(buildSQL(cur));
        merged.append(' ');
        merged.append(buildSQL(cur.child));
        sqlBatch.add(merged.toString());
        cur = cur.child;
        if (cur == this) break;
        continue;
      }
      sqlBatch.add(buildSQL(cur));
      if (cur == this) break;
    }
    return sqlBatch;
  }

  // ==================== 参数化构建 ====================

  /** 参数化 SQL 的构建结果 */
  public static record PreparedSQL(String sql, java.util.List<Object> params) {}

  /**
   * 参数化构建从链首到当前节点的完整 SQL（使用 ? 占位符）。
   * <p>
   * 与 {@link #toSqlString()} 逻辑相同，但值用 ? 替代，收集到 {@link PreparedSQL#params} 中。
   * 
   * @since 0.6.0
   */
  public PreparedSQL toPrepared() {
    SdbActionContext head = this;
    while (head.parent != null) head = head.parent;

    java.util.List<Object> allParams = new java.util.ArrayList<>();
    StringBuilder sb = new StringBuilder();

    for (SdbActionContext cur = head; cur != null; cur = cur.child) {
      if (cur.type == SdbEnums.ACTION_TYPE.WITH) {
        if (cur.child == null)
          throw new IllegalStateException("WITH 节点后缺少主查询");
        // WITH 子句
        PreparedSQL withSQL = buildPreparedSQL(cur);
        sb.append(withSQL.sql());
        allParams.addAll(withSQL.params());
        // 主查询
        PreparedSQL mainSQL = buildPreparedSQL(cur.child);
        sb.append(' ').append(mainSQL.sql());
        allParams.addAll(mainSQL.params());
        cur = cur.child;
        if (cur == this) break;
        continue;
      }
      PreparedSQL nodeSQL = buildPreparedSQL(cur);
      sb.append(nodeSQL.sql());
      allParams.addAll(nodeSQL.params());
      if (cur == this) break;
      sb.append("; ");
    }
    return new PreparedSQL(sb.toString(), allParams);
  }

  /** 构建单节点的参数化 SQL */
  private static PreparedSQL buildPreparedSQL(SdbActionContext ctx) {
    // === 校验 ===
    if (ctx.with != null && ctx.with.isEmpty())
      throw new IllegalStateException(ctx.type + " 的 WITH 子句未指定数据来源");

    String tbl = resolveTable(ctx);

    // WITH 节点：WITH alias AS (source)
    if (ctx.type == SdbEnums.ACTION_TYPE.WITH) {
      if (tbl == null) throw new IllegalStateException("WITH 节点未指定 CTE 别名");
      StringBuilder sb = new StringBuilder("WITH `").append(tbl).append("` AS (");
      java.util.List<Object> params = new java.util.ArrayList<>();
      if (ctx.with != null) {
        boolean f = true;
        for (SdbActionContext src : ctx.with.values()) {
          if (!f) sb.append(", "); f = false;
          PreparedSQL srcSQL = buildPreparedSQL(src);
          sb.append(srcSQL.sql()); params.addAll(srcSQL.params());
        }
      }
      sb.append(')');
      return new PreparedSQL(sb.toString(), params);
    }

    // 需要表名的操作
    switch (ctx.type) {
      case SELECT: case UPDATE: case DELETE:
      case CREATE: case ALTER: case DROP: case TRUNCATE:
      case MERGE:
        if (tbl == null) throw new IllegalStateException(ctx.type + " 操作未指定目标表");
        break;
      default: break;
    }

    StringBuilder sb = new StringBuilder();
    java.util.List<Object> params = new java.util.ArrayList<>();

    // WITH 子句
    if (ctx.with != null && !ctx.with.isEmpty()) {
      sb.append("WITH ");
      boolean f = true;
      for (Map.Entry<String, SdbActionContext> e : ctx.with.entrySet()) {
        if (!f) sb.append(", "); f = false;
        sb.append('`').append(e.getKey()).append("` AS (");
        PreparedSQL srcSQL = buildPreparedSQL(e.getValue());
        sb.append(srcSQL.sql()); params.addAll(srcSQL.params());
        sb.append(')');
      }
      sb.append(' ');
    }

    // 操作命令
    switch (ctx.type) {
      case SELECT:
        sb.append("SELECT ");
        String[] cols = ctx.filter.selectWhat();
        for (int i = 0; i < cols.length; i++) {
          if (i > 0) sb.append(", "); sb.append(cols[i]);
        }
        sb.append(" FROM "); if (tbl != null) sb.append(tbl);
        break;
      case UPDATE:
        sb.append("UPDATE "); if (tbl != null) sb.append(tbl);
        sb.append(" SET ?");
        break;
      case DELETE:
        sb.append("DELETE FROM "); if (tbl != null) sb.append(tbl);
        break;
      case CREATE:
        sb.append("CREATE TABLE "); if (tbl != null) sb.append(tbl);
        break;
      case ALTER:
        sb.append("ALTER TABLE "); if (tbl != null) sb.append(tbl);
        break;
      case DROP:
        sb.append("DROP TABLE "); if (tbl != null) sb.append(tbl);
        break;
      case TRUNCATE:
        sb.append("TRUNCATE TABLE "); if (tbl != null) sb.append(tbl);
        break;
      case MERGE:
        sb.append("MERGE INTO "); if (tbl != null) sb.append(tbl);
        break;
      default:
        sb.append(ctx.type).append(' '); if (tbl != null) sb.append(tbl);
        break;
    }

    // WHERE 条件（参数化）
    String where = ctx.filter.toString(ctx.alias);
    if (!"()".equals(where)) {
      // 使用参数化版本
      sb.append(" WHERE ");
      SdbActionContext.PreparedSQL wherePrep = ctx.filter.toPrepared(ctx.alias);
      sb.append(wherePrep.sql());
      params.addAll(wherePrep.params());
    }
    if (ctx.limit > 0) sb.append(" LIMIT ").append(ctx.limit);
    return new PreparedSQL(sb.toString(), params);
  }

  /** 根据操作类型构建单条 SQL */
  @SuppressWarnings("deprecation")
  private static String buildSQL(SdbActionContext ctx) {
    // === 校验 ===
    // WITH 子句声明了但未提供来源
    if (ctx.with != null && ctx.with.isEmpty()) {
      throw new IllegalStateException(ctx.type + " 的 WITH 子句未指定数据来源");
    }
    // 解析表名（向上继承）
    String tbl = resolveTable(ctx);

    // WITH 节点：构建 WITH alias AS (source)
    if (ctx.type == SdbEnums.ACTION_TYPE.WITH) {
      if (tbl == null)
        throw new IllegalStateException("WITH 节点未指定 CTE 别名（通过 table() 设置）");
      StringBuilder sb = new StringBuilder("WITH ");
      sb.append(SdbUtils.q(tbl)).append(" AS (");
      if (ctx.with != null && !ctx.with.isEmpty()) {
        boolean f = true;
        for (SdbActionContext src : ctx.with.values()) {
          if (!f) sb.append(", "); f = false;
          sb.append(buildSQL(src));
        }
      }
      sb.append(')');
      return sb.toString();
    }

    // 需要表名的操作
    switch (ctx.type) {
      case SELECT: case UPDATE: case DELETE:
      case CREATE: case ALTER: case DROP: case TRUNCATE:
      case MERGE:
        if (tbl == null)
          throw new IllegalStateException(ctx.type + " 操作未指定目标表（在当前或上文中设置）");
        break;
      default: break;
    }

    // === 构建 SQL ===
    StringBuilder sb = new StringBuilder();
    // WITH 子句（放在 SELECT/... 之前）
    if (ctx.with != null && !ctx.with.isEmpty()) {
      sb.append("WITH ");
      boolean f = true;
      for (Map.Entry<String, SdbActionContext> e : ctx.with.entrySet()) {
        if (!f) sb.append(", "); f = false;
        sb.append(SdbUtils.q(e.getKey())).append(" AS (").append(buildSQL(e.getValue())).append(')');
      }
      sb.append(' ');
    }
    // 操作命令
    switch (ctx.type) {
      case SELECT:
        sb.append("SELECT ");
        String[] cols = ctx.filter.selectWhat();
        for (int i = 0; i < cols.length; i++) {
          if (i > 0) sb.append(", ");
          if ("*".equals(cols[i])) {
            sb.append('*');
          } else {
            sb.append(SdbUtils.q(cols[i]));
          }
        }
        sb.append(" FROM ");
        if (tbl != null) sb.append(SdbUtils.q(tbl));
        break;
      case UPDATE:
        throw new UnsupportedOperationException(
            "UPDATE 的 SET 子句参数映射尚未实现。"
            + "请使用 db.act(\"UPDATE ... SET ... WHERE ...\") 原始 SQL 替代");
      case DELETE:
        sb.append("DELETE FROM ");
        if (tbl != null) sb.append(SdbUtils.q(tbl));
        break;
      case CREATE:
        sb.append("CREATE TABLE ");
        if (tbl != null) sb.append(SdbUtils.q(tbl));
        break;
      case ALTER:
        sb.append("ALTER TABLE ");
        if (tbl != null) sb.append(SdbUtils.q(tbl));
        break;
      case DROP:
        sb.append("DROP TABLE ");
        if (tbl != null) sb.append(SdbUtils.q(tbl));
        break;
      case TRUNCATE:
        sb.append("TRUNCATE TABLE ");
        if (tbl != null) sb.append(SdbUtils.q(tbl));
        break;
      case MERGE:
        sb.append("MERGE INTO ");
        if (tbl != null) sb.append(SdbUtils.q(tbl));
        break;
      case COMMIT:
      case ROLLBACK:
      case SAVEPOINT:
        throw new UnsupportedOperationException(
            ctx.type + " 不可通过 SdbActionContext 执行。"
            + "事务控制请使用 SdbAction.commit() / SdbAction.rollback()");
      default:
        sb.append(ctx.type).append(' ');
        if (tbl != null) sb.append(SdbUtils.q(tbl));
        break;
    }
    // WHERE 条件
    String where = ctx.filter.toString(ctx.alias);
    if (!"()".equals(where)) sb.append(" WHERE ").append(where);
    // LIMIT（上限 2^31-1 即无限制）
    if (ctx.limit > 0) {
      if (ctx.limit > Integer.MAX_VALUE - 1) ctx.limit = Integer.MAX_VALUE - 1;
      sb.append(" LIMIT ").append(ctx.limit);
    }
    return sb.toString();
  }

  /** 向上查找表名：优先用自身的，否则继承上文的 */
  private static String resolveTable(SdbActionContext ctx) {
    if (ctx.table != null && !ctx.table.isBlank()) return ctx.table;
    SdbActionContext p = ctx.parent;
    while (p != null) {
      if (p.table != null && !p.table.isBlank()) return p.table;
      p = p.parent;
    }
    return null;
  }
}
