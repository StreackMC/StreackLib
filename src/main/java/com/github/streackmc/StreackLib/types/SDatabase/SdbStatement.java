package com.github.streackmc.StreackLib.types.SDatabase;

import java.lang.ref.WeakReference;
import java.sql.PreparedStatement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.github.streackmc.StreackLib.errors.CircularReferenceException;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SdbStatement</h2>
 * 对数据库操作进行断言。断言可以作为条件限制数据库操作。
 * <p>
 * 除了如 {@link #or()} 这类少数的，如果同一个断言被多次指定逻辑，那么实际上这些逻辑会自动被视作使用 {@link #and()} 并列的隐式断言。
 * <p>
 * 对于 {@link #or()} ，它实际上会将全部非 OR 断言作为一个断言，与其他 OR 断言合并判断，也就是 OR 的优先级高于其他的；因此为了语义清晰，建议不要混用 OR 和其他条件。
 * <p>
 * 空断言始终为 true。断言可以作为其他断言的条件，但如果尝试重复(循环)断言，则会抛出 {@link CircularReferenceException}。
 * 
 * <h3>不可回滚性</h3>
 * 为了保证 API 简单度，<b>条件/其他断言被加入断言后不可撤销或修改</b>。这意味着如果断言包含的条件发生变更，你应始终新建一个断言而非继续使用原断言，除了以下这些情况：
 * <ul>
 * <li>在 {@link #and()} 或 {@link #or()} 方法中重新指定：会切换该断言的需求状态</li>
 * <li>新增了其它条件</li>
 * </ul>
 * 尽管如此，由于线程安全性原因，<b>极其不推荐在一个断言被使用时修改这个断言</b>。推荐使用 {@link #copy()} 或 {@link #copyAll()} 复制一个副本。
 * 
 * <h3>「从表查找」</h3>
 * 在部分断言中，「从表查找」指的是将输入参数作为表中列名，并将行的该列的内容作为参数进行断言。大部分断言中 v1 都会从表查找，而 v2 v3 等则不会，这是因为大部分情况下使用 SQL 比较定值不太理想（除非是<code>OR '1' = '1'</code>这种注入攻击）。
 * <p>
 * 默认情况下，查找的表是使用本断言的操作上下文所指定的表，但是可以使用 <code>table.column</code> 指定完整表名，或者使用 <code>alias.column</code> 指定操作上下文中定义的表别名。
 * 
 * @since 0.6.0
 * @author kdxiaoyi
 * @author Deepseek
 */
public class SdbStatement extends StreackLibNewable {

  /** 开始一个断言 */
  public SdbStatement() {
  }

  // ===--- 工具 ---===

  /**
   * 判断断言是否已被包含在另外一个断言中，这可以避免循环断言。
   * 
   * @since 0.6.0
   * @param parent 父断言：已经存在的
   * @param child  子断言：要加入父断言作为子断言的
   * @apiNote 使用 BFS 遍历，时间复杂度为<code>O(断言树中断言数量 + 父引用边数)</code>，空间复杂度为<code>O(断言树中断言数量)</code>——在轻量断言树中的性能影响可忽略不计。
   */
  public static boolean detectCircular(SdbStatement parent, SdbStatement child) {
    // 先判断输入是否合法
    if (child == null || parent == null)
      return false;
    if (parent == child)
      return true;
    // BFS 遍历父级链
    ArrayDeque<SdbStatement> queue = new ArrayDeque<>();
    HashSet<SdbStatement> visited = new HashSet<>();
    queue.addLast(parent);
    visited.add(parent);
    while (!queue.isEmpty()) {
      SdbStatement current = queue.removeFirst();
      // 清理已 GC 的弱引用
      current.parentStatements.removeIf(i -> i.get() == null);
      for (WeakReference<SdbStatement> ref : current.parentStatements) {
        SdbStatement ancestor = ref.get();
        if (ancestor == null)
          continue;
        if (ancestor == child)
          return true;
        if (visited.add(ancestor)/* 返回 true 说明尚未遍历，加入队列 */) {
          queue.addLast(ancestor);
        }
      }
    }
    // 检查通过
    return false;
  }

  /**
   * 获取此断言的镜像，有助于线程安全。
   * @since 0.6.0
   * @see {@link #copyAll()} 深拷贝版本
   */
  public SdbStatement copy() {
    SdbStatement newer = new SdbStatement();
    newer.reverted = this.reverted;
    newer.conditions.addAll(this.conditions);
    newer.andStatements.putAll(this.andStatements);
    newer.orStatements.putAll(this.orStatements);
    // 新断言不会有任何父级，解除引用
    return newer;
  }

  /**
   * 获取此断言的镜像，且包含的全部子断言的镜像，有助于线程安全。
   * @since 0.6.0
   * @see {@link #copy()} 浅拷贝版本
   */
  public SdbStatement copyAll() {
    SdbStatement newer = new SdbStatement();
    newer.reverted = this.reverted;
    for (Condition c : this.conditions)
      newer.conditions.add(c.clone());
    for (Map.Entry<SdbStatement, Boolean> e : this.andStatements.entrySet())
      newer.andStatements.put(e.getKey().copyAll(), e.getValue());
    for (Map.Entry<SdbStatement, Boolean> e : this.orStatements.entrySet())
      newer.orStatements.put(e.getKey().copyAll(), e.getValue());
    return newer;
  }

  // ===--- 断言接口 ---===

  /**
   * 将断言树转为{@link java.sql.PreparedStatement}。
   *
   * @throws UnsupportedOperationException 本方法未实现。参数化查询已由 {@link #toPrepared(Map)} 提供，
   *         其内部使用 {@code ?} 占位符 + 值绑定，彻底防止<b>值</b>注入；标识符（列名）则经
   *         {@link #lookupCol} 反引号转义。请勿再使用本方法，也勿把 {@link #toString()} 的字符串结果回灌执行器。
   * @since 0.6.0
   */
  public PreparedStatement toSql(SdbActionContext ctx) {
    throw new UnsupportedOperationException(
        "toSql 未实现，请改用 toPrepared(Map) 进行参数化构建，或使用 toString() 仅作预览");
  }

  /**
   * 将断言转为 SQL 指令；无上下文时裸列名输出 <code>?</code>。
   * <p>
   * 仅用于<b>预览/调试</b>：条件<b>值</b>经 {@link SdbUtils#literal} 做基础转义（字符串拼接，<b>非参数化</b>），
   * 列名经 {@link #lookupCol} 反引号转义。此字符串<b>不应回灌给执行器</b>。
   * 真正执行请使用 {@link #toPrepared(Map)}（值走 {@code ?} 占位符）。
   * 
   * @apiNote 本方法不是防注入的安全边界：值只是基础转义，调用者仍不可把不可信值经此路径导出后执行。
   * @since 0.6.0
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    appendSQL(sb, null);
    return sb.length() == 0 ? "()" : sb.toString();
  }

  /**
   * 将断言转为 SQL 指令；支持透传别名映射至子断言。
   * <p>
   * <code>table.column</code> 输出 <code>`table`.`column`</code>（表或别名由外层 FROM 定义）<br>
   * 裸列名输出 <code>`column`</code>
   * <p>
   * 仅用于<b>预览/调试</b>：条件<b>值</b>经 {@link SdbUtils#literal} 基础转义（非参数化），列名经 {@link #lookupCol} 反引号转义。
   * 真正执行请使用 {@link #toPrepared(Map)}。
   * 
   * @apiNote 本方法不是防注入的安全边界：值仅基础转义，不可把不可信值经此路径导出后执行。
   * @param alias 别名映射，当前仅透传至子断言，由 {@link #toSql(SdbActionContext)} 使用
   * @since 0.6.0
   */
  public String toString(Map<String, String> alias) {
    StringBuilder sb = new StringBuilder();
    appendSQL(sb, alias);
    return sb.length() == 0 ? "()" : sb.toString();
  }

  /**
   * 将断言转为 SQL 指令；从操作上下文中提取别名映射。
   * 
   * @param ctx 数据库操作上下文
   * @since 0.6.0
   */
  public String toString(SdbActionContext ctx) {
    return toString(ctx == null ? null : ctx.alias);
  }

  /** 递归生成 SQL */
  private void appendSQL(StringBuilder sb, Map<String, String> alias) {
    boolean hasCond = !conditions.isEmpty();
    boolean hasAnd  = !andStatements.isEmpty();
    boolean hasOr   = !orStatements.isEmpty();

    if (!hasCond && !hasAnd && !hasOr) { sb.append("1=1"); return; }

    // 叶条件转 SQL
    for (int i = 0; i < conditions.size(); i++) {
      if (i > 0) sb.append(" AND ");
      appendConditionSQL(sb, conditions.get(i), alias);
    }

    // AND 子断言
    if (hasAnd) {
      if (hasCond) sb.append(" AND ");
      boolean first = true;
      for (Map.Entry<SdbStatement, Boolean> e : andStatements.entrySet()) {
        if (!first) sb.append(" AND ");
        first = false;
        if (!e.getValue()) sb.append("NOT ");
        sb.append('(');
        e.getKey().appendSQL(sb, alias);
        sb.append(')');
      }
    }

    // OR 子断言
    if (hasOr) {
      if (hasCond || hasAnd) sb.append(" AND ");
      sb.append('(');
      boolean first = true;
      for (Map.Entry<SdbStatement, Boolean> e : orStatements.entrySet()) {
        if (!first) sb.append(" OR ");
        first = false;
        if (!e.getValue()) sb.append("NOT ");
        sb.append('(');
        e.getKey().appendSQL(sb, alias);
        sb.append(')');
      }
      sb.append(')');
    }

    if (reverted) {
      String expr = sb.toString();
      sb.setLength(0);
      sb.append("NOT (").append(expr).append(')');
    }
  }

  /** 单条条件转 SQL */
  private void appendConditionSQL(StringBuilder sb, Condition c, Map<String, String> alias) {
    String col = lookupCol(c.v1, alias);
    switch (c.type) {
      case IS_NULL:      sb.append(col).append(" IS NULL"); break;
      case IS_NOT_NULL:  sb.append(col).append(" IS NOT NULL"); break;
      case EQUAL:        sb.append(col).append(" = ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case UNEQUAL:      sb.append(col).append(" <> ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case LARGER:       sb.append(col).append(" > ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case SMALLER:      sb.append(col).append(" < ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case LARGER_OR_EQUAL: sb.append(col).append(" >= ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case SMALLER_OR_EQUAL: sb.append(col).append(" <= ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case LIKE:         sb.append(col).append(" LIKE ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case NOT_LIKE:     sb.append(col).append(" NOT LIKE ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case REGEX:        sb.append(col).append(" REGEXP ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case NOT_REGEX:    sb.append(col).append(" NOT REGEXP ").append(lookupVal(c.v2, c.v2Lookup, alias)); break;
      case BETWEEN:
        sb.append(col).append(" BETWEEN ")
          .append(lookupVal(c.v2, c.v2Lookup, alias)).append(" AND ").append(SdbUtils.literal(c.v3)); break;
      case NOT_BETWEEN:
        sb.append(col).append(" NOT BETWEEN ")
          .append(lookupVal(c.v2, c.v2Lookup, alias)).append(" AND ").append(SdbUtils.literal(c.v3)); break;
      case IN: case NOT_IN:
        sb.append(col).append(c.type == CondType.IN ? " IN (" : " NOT IN (");
        if (c.inValues != null) {
          boolean f = true;
          for (Map.Entry<String, Boolean> iv : c.inValues.entrySet()) {
            if (!f) sb.append(", "); f = false;
            sb.append(lookupVal(iv.getKey(), iv.getValue(), alias));
          }
        }
        sb.append(')');
        break;
    }
  }

  // ==================== 参数化构建（PreparedStatement） ====================

  /** 参数化构建 WHERE（? 占位符 + 参数收集），递归调用自身和子断言 */
  private void buildPreparedSQL(StringBuilder sb, List<Object> params, Map<String, String> alias) {
    boolean hasCond = !conditions.isEmpty();
    boolean hasAnd  = !andStatements.isEmpty();
    boolean hasOr   = !orStatements.isEmpty();
    if (!hasCond && !hasAnd && !hasOr) { sb.append("1=1"); return; }

    for (int i = 0; i < conditions.size(); i++) {
      if (i > 0) sb.append(" AND ");
      buildPreparedConditionSQL(sb, params, conditions.get(i), alias);
    }
    if (hasAnd) {
      if (hasCond) sb.append(" AND ");
      boolean first = true;
      for (Map.Entry<SdbStatement, Boolean> e : andStatements.entrySet()) {
        if (!first) sb.append(" AND "); first = false;
        if (!e.getValue()) sb.append("NOT ");
        sb.append('('); e.getKey().buildPreparedSQL(sb, params, alias); sb.append(')');
      }
    }
    if (hasOr) {
      if (hasCond || hasAnd) sb.append(" AND ");
      sb.append('(');
      boolean first = true;
      for (Map.Entry<SdbStatement, Boolean> e : orStatements.entrySet()) {
        if (!first) sb.append(" OR "); first = false;
        if (!e.getValue()) sb.append("NOT ");
        sb.append('('); e.getKey().buildPreparedSQL(sb, params, alias); sb.append(')');
      }
      sb.append(')');
    }
    if (reverted) {
      String expr = sb.toString(); sb.setLength(0);
      sb.append("NOT (").append(expr).append(')');
    }
  }

  /** 单条条件参数化构建（值用 ? 占位符，值收集到 params） */
  private void buildPreparedConditionSQL(StringBuilder sb, List<Object> params, Condition c, Map<String, String> alias) {
    String col = lookupCol(c.v1, alias);
    switch (c.type) {
      case IS_NULL:      sb.append(col).append(" IS NULL"); break;
      case IS_NOT_NULL:  sb.append(col).append(" IS NOT NULL"); break;
      case EQUAL:        paramOp(sb, params, col, " = ",    c.v2, c.v2Lookup, alias); break;
      case UNEQUAL:      paramOp(sb, params, col, " <> ",   c.v2, c.v2Lookup, alias); break;
      case LARGER:       paramOp(sb, params, col, " > ",    c.v2, c.v2Lookup, alias); break;
      case SMALLER:      paramOp(sb, params, col, " < ",    c.v2, c.v2Lookup, alias); break;
      case LARGER_OR_EQUAL: paramOp(sb, params, col, " >= ", c.v2, c.v2Lookup, alias); break;
      case SMALLER_OR_EQUAL: paramOp(sb, params, col, " <= ", c.v2, c.v2Lookup, alias); break;
      case LIKE:         paramOp(sb, params, col, " LIKE ",       c.v2, c.v2Lookup, alias); break;
      case NOT_LIKE:     paramOp(sb, params, col, " NOT LIKE ",   c.v2, c.v2Lookup, alias); break;
      case REGEX:        paramOp(sb, params, col, " REGEXP ",     c.v2, c.v2Lookup, alias); break;
      case NOT_REGEX:    paramOp(sb, params, col, " NOT REGEXP ", c.v2, c.v2Lookup, alias); break;
      case BETWEEN:
        sb.append(col).append(" BETWEEN ");
        if (c.v2Lookup) { sb.append(lookupCol(c.v2, alias)); }
        else { sb.append("?"); params.add(c.v2); }
        sb.append(" AND ?"); params.add(c.v3);
        break;
      case NOT_BETWEEN:
        sb.append(col).append(" NOT BETWEEN ");
        if (c.v2Lookup) { sb.append(lookupCol(c.v2, alias)); }
        else { sb.append("?"); params.add(c.v2); }
        sb.append(" AND ?"); params.add(c.v3);
        break;
      case IN: case NOT_IN:
        sb.append(col).append(c.type == CondType.IN ? " IN (" : " NOT IN (");
        if (c.inValues != null) {
          boolean f = true;
          for (Map.Entry<String, Boolean> iv : c.inValues.entrySet()) {
            if (!f) sb.append(", "); f = false;
            if (iv.getValue()) { sb.append(lookupCol(iv.getKey(), alias)); }
            else { sb.append("?"); params.add(iv.getKey()); }
          }
        }
        sb.append(')');
        break;
    }
  }

  /** 辅助：从表查找用 lookupCol，否则用 ? 占位符并收集值 */
  private static void paramOp(StringBuilder sb, List<Object> params, String col, String op, String val, boolean fromTable, Map<String, String> alias) {
    sb.append(col).append(op);
    if (fromTable) { sb.append(lookupCol(val, alias)); }
    else { sb.append("?"); params.add(val); }
  }

  /**
   * 参数化构建 WHERE 子句（使用 ? 占位符，避免 SQL 注入）。
   * <p>
   * <b>值</b>（{@code equal/like/larger/...} 的定值参数、{@code between} 的端点、{@code in} 的非列查值）一律替换为
   * {@code ?} 并收集到 {@link SdbActionContext.PreparedSQL#params}，由执行器经 {@code setObject} 绑定——
   * 值注入已彻底防住。<b>列名</b>经 {@link #lookupCol} 反引号转义（标识符无法参数化，故为转义）。
   * <p>
   * 本方法对应的是<b>实际执行路径</b>。但如需把表名/列名动态来源于外部输入，调用者仍需自行白名单校验
   * （转义只是兜底保留字/畸形标识符，不能把不可信标识符变安全）。
   * 
   * @apiNote 仅处理 WHERE 子树；表名、CTE 名、SELECT 投影列由上层 {@link SdbActionContext} 负责转义。
   * @since 0.6.0
   */
  public SdbActionContext.PreparedSQL toPrepared(Map<String, String> alias) {
    List<Object> params = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    buildPreparedSQL(sb, params, alias);
    return new SdbActionContext.PreparedSQL(sb.toString(), params);
  }

  /**
   * 将列名格式化为 SQL 标识符（反引号包裹）：
   * <ul>
   *   <li><code>column</code> → <code>`column`</code>（裸列名，表由外层 FROM 定义）</li>
   *   <li><code>table.column</code> → <code>`table`.`column`</code>（表或别名均由 SQL 引擎从 FROM 解析）</li>
   * </ul>
   * @param alias 别名映射，当前仅透传至子断言供 {@link #toSql(SdbActionContext)} 使用
   */
  private static String lookupCol(String col, Map<String, String> alias) {
    if (col == null || col.isEmpty()) return "";
    int dot = col.indexOf('.');
    if (dot > 0) {
      return SdbUtils.q(col.substring(0, dot)) + "." + SdbUtils.q(col.substring(dot + 1));
    }
    return SdbUtils.q(col);
  }

  /** 将值格式化为 SQL 字面量或列引用 */
  private static String lookupVal(String val, boolean fromTable, Map<String, String> alias) {
    return fromTable ? lookupCol(val, alias) : SdbUtils.literal(val);
  }

  // ===--- 逻辑控制 ---===

  /** AND 断言列表；value 为 false 时需求 key 为假 */
  private HashMap<SdbStatement, Boolean> andStatements = new HashMap<SdbStatement, Boolean>();
  /** 父断言列表，仅用作防止循环引用 */
  private ArrayList<WeakReference<SdbStatement>> parentStatements = new ArrayList<WeakReference<SdbStatement>>();
  /** OR 断言列表；value 为 false 时需求 key 为假 */
  private HashMap<SdbStatement, Boolean> orStatements = new HashMap<SdbStatement, Boolean>();
  /** 为 true 时本断言的结果反转 */
  public volatile boolean reverted = false;

  /** 条件类型枚举 */
  private enum CondType {
    LIKE, NOT_LIKE, REGEX, NOT_REGEX,
    BETWEEN, NOT_BETWEEN, IN, NOT_IN,
    IS_NULL, IS_NOT_NULL,
    EQUAL, UNEQUAL,
    LARGER, SMALLER, LARGER_OR_EQUAL, SMALLER_OR_EQUAL
  }

  /** 单条条件 */
  private static class Condition implements Cloneable {
    final CondType type;
    final String v1, v2, v3;
    final boolean v2Lookup;
    final Map<String, Boolean> inValues;

    Condition(CondType type, String v1, String v2, String v3, boolean v2Lookup, Map<String, Boolean> inValues) {
      this.type = type; this.v1 = v1; this.v2 = v2; this.v3 = v3;
      this.v2Lookup = v2Lookup;
      this.inValues = inValues == null ? null : new HashMap<>(inValues);
    }

    @Override
    public Condition clone() {
      return new Condition(type, v1, v2, v3, v2Lookup, inValues);
    }
  }

  /** 本断言的条件列表，这些条件都是 AND 关系 */
  private ArrayList<Condition> conditions = new ArrayList<>();

  /** 声明本断言已被作为其它断言的子断言 */
  protected void setParent(SdbStatement p) {
    parentStatements.add(new WeakReference<SdbStatement>(p));
  }

  /**
   * ……并且参数需为真
   * 
   * @since 0.6.0
   * @throws NullPointerException       参数为 Null
   * @throws CircularReferenceException 检测到循环引用
   */
  public SdbStatement and(SdbStatement another) {
    Objects.requireNonNull(another, "请求的断言为 Null");
    if (detectCircular(this, another)) throw new CircularReferenceException("请求的断言已被父级引用");
    andStatements.put(another, true);
    another.setParent(this);
    return this;
  }

  /**
   * ……并且参数需为假
   * 
   * @since 0.6.0
   * @throws NullPointerException       参数为 Null
   * @throws CircularReferenceException 检测到循环引用
   */
  public SdbStatement andNot(SdbStatement another) {
    Objects.requireNonNull(another, "请求的断言为 Null");
    if (detectCircular(this, another)) throw new CircularReferenceException("请求的断言已被父级引用");
    andStatements.put(another, false);
    another.setParent(this);
    return this;
  }

  /**
   * ……或者参数为真
   * 
   * @since 0.6.0
   * @throws NullPointerException       参数为 Null
   * @throws CircularReferenceException 检测到循环引用
   */
  public SdbStatement or(SdbStatement another) {
    Objects.requireNonNull(another, "请求的断言为 Null");
    if (detectCircular(this, another)) throw new CircularReferenceException("请求的断言已被父级引用");
    orStatements.put(another, true);
    another.setParent(this);
    return this;
  }

  /**
   * ……或者参数为假
   * 
   * @since 0.6.0
   * @throws NullPointerException       参数为 Null
   * @throws CircularReferenceException 检测到循环引用
   */
  public SdbStatement orNot(SdbStatement another) {
    Objects.requireNonNull(another, "请求的断言为 Null");
    if (detectCircular(this, another)) throw new CircularReferenceException("请求的断言已被父级引用");
    orStatements.put(another, false);
    another.setParent(this);
    return this;
  }

  /**
   * 设置是否要「反转本断言的结果」
   * 
   * @since 0.6.0
   * @param reverted 为 true 反转
   * @see {@link #reverted}
   */
  public SdbStatement revert(boolean reverted) {
    this.reverted = reverted;
    return this;
  }

  /**
   * 反转「反转本断言的结果」的状态
   * 
   * @since 0.6.0
   * @see {@link #reverted}
   */
  public SdbStatement revert() {
    this.reverted = !this.reverted;
    return this;
  }

  // ===--- 匹配与范围 ---===

  /**
   * 要求 v1 匹配 v2 这个简易模糊匹配器。
   * 
   * @see {@link #regex()} 正则表达式匹配
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2：使用 <code>_</code> 匹配任意<b>单个</b>字符；使用 <code>%</code>
   *           匹配<b>任意数量</b>字符。
   */
  public SdbStatement like(String v1, String v2) {
    conditions.add(new Condition(CondType.LIKE, v1, v2, null, false, null));
    return this;
  }

  /**
   * 要求 v1 <b>不</b>匹配 v2 这个简易模糊匹配器。
   * 
   * @see {@link #regex()} 正则表达式匹配
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2：使用 <code>_</code> 匹配任意<b>单个</b>字符；使用 <code>%</code>
   *           匹配<b>任意数量</b>字符。
   */
  public SdbStatement notLike(String v1, String v2) {
    conditions.add(new Condition(CondType.NOT_LIKE, v1, v2, null, false, null));
    return this;
  }

  /**
   * 要求 v1 匹配 v2 这个正则表达式。
   * 
   * @see {@link #like()} 简单模糊匹配
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2：一个正则表达式。
   */
  public SdbStatement regex(String v1, String v2) {
    conditions.add(new Condition(CondType.REGEX, v1, v2, null, false, null));
    return this;
  }

  /**
   * 要求 v1 <b>不</b>匹配 v2 这个正则表达式。
   * 
   * @see {@link #like()} 简单模糊匹配
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2：一个正则表达式。
   */
  public SdbStatement notRegex(String v1, String v2) {
    conditions.add(new Condition(CondType.NOT_REGEX, v1, v2, null, false, null));
    return this;
  }

  /**
   * 要求 v1 在 v2 到 v3 指定的范围里面，可以是数值或者时间。
   * 
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2
   * @param v3 参数3
   */
  public SdbStatement between(String v1, String v2, String v3) {
    conditions.add(new Condition(CondType.BETWEEN, v1, v2, v3, false, null));
    return this;
  }

  /**
   * 要求 v1 <b>不</b>在 v2 到 v3 指定的范围里面，可以是数值或者时间。
   * 
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2
   * @param v3 参数3
   */
  public SdbStatement notBetween(String v1, String v2, String v3) {
    conditions.add(new Condition(CondType.NOT_BETWEEN, v1, v2, v3, false, null));
    return this;
  }

  /**
   * 要求 v1 在 v2 中可以找到
   * 
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2：key表示要匹配的值，value表示是否要从表查找，默认 false。
   */
  public SdbStatement in(String v1, Map<String, Boolean> v2) {
    conditions.add(new Condition(CondType.IN, v1, null, null, false, v2));
    return this;
  }

  /**
   * 要求 v1 在 v2 中无法找到
   * 
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2：key表示要匹配的值，value表示是否要从表查找，默认 false。
   */
  public SdbStatement notIn(String v1, Map<String, Boolean> v2) {
    conditions.add(new Condition(CondType.NOT_IN, v1, null, null, false, v2));
    return this;
  }

  // ===--- 比较运算 ---===

  /**
   * 要求 v1 是 Null。
   * 
   * @since 0.6.0
   * @param v1 参数1
   */
  public SdbStatement isNull(String v1) {
    conditions.add(new Condition(CondType.IS_NULL, v1, null, null, false, null));
    return this;
  }

  /**
   * 要求 v1 不是 Null。
   * 
   * @since 0.6.0
   * @param v1 参数1
   */
  public SdbStatement isNotNull(String v1) {
    conditions.add(new Condition(CondType.IS_NOT_NULL, v1, null, null, false, null));
    return this;
  }

  /**
   * 要求两个参数相等。
   * 
   * @see {@link #isNull()} {@link #isNotNull()} <b> Null 不能使用本方法匹配，请改用这些。</b>
   * @since 0.6.0
   * @param v1   参数1
   * @param v2lp 参数2是否要从表查找，默认 false
   * @param v2   参数2
   */
  public SdbStatement equal(String v1, String v2, boolean v2lp) {
    conditions.add(new Condition(CondType.EQUAL, v1, v2, null, v2lp, null));
    return this;
  }

  /**
   * 要求两个参数相等。
   * 
   * @see {@link #isNull()} {@link #isNotNull()} <b> Null 不能使用本方法匹配，请改用这些。</b>
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2，不会从表中查找。
   */
  public SdbStatement equal(String v1, String v2) {
    return equal(v1, v2, false);
  }

  /**
   * 要求两个参数不相等。
   * 
   * @see {@link #isNull()} {@link #isNonNull()} <b> Null 不能使用本方法匹配，请改用这些。</b>
   * @since 0.6.0
   * @param v1   参数1
   * @param v2lp 参数2是否要从表查找，默认 false
   * @param v2   参数2
   */
  public SdbStatement unequal(String v1, String v2, boolean v2lp) {
    conditions.add(new Condition(CondType.UNEQUAL, v1, v2, null, v2lp, null));
    return this;
  }

  /**
   * 要求两个参数不相等。
   * 
   * @since 0.6.0
   * @see {@link #isNull()} {@link #isNonNull()} <b> Null 不能使用本方法匹配，请改用这些。</b>
   * @param v1 参数1
   * @param v2 参数2，不会从表中查找。
   */
  public SdbStatement unequal(String v1, String v2) {
    return unequal(v1, v2, false);
  }

  /**
   * 要求 v1 大于 v2。
   * 
   * @since 0.6.0
   * @param v1   参数1
   * @param v2lp 参数2是否要从表查找，默认 false
   * @param v2   参数2
   */
  public SdbStatement larger(String v1, String v2, boolean v2lp) {
    conditions.add(new Condition(CondType.LARGER, v1, v2, null, v2lp, null));
    return this;
  }

  /**
   * 要求 v1 大于 v2。
   * 
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2，不会从表中查找。
   */
  public SdbStatement larger(String v1, String v2) {
    return larger(v1, v2, false);
  }

  /**
   * 要求v1 小于 v2。
   * 
   * @since 0.6.0
   * @param v1   参数1
   * @param v2lp 参数2是否要从表查找，默认 false
   * @param v2   参数2
   */
  public SdbStatement smaller(String v1, String v2, boolean v2lp) {
    conditions.add(new Condition(CondType.SMALLER, v1, v2, null, v2lp, null));
    return this;
  }

  /**
   * 要求v1 小于 v2。
   * 
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2，不会从表中查找。
   */
  public SdbStatement smaller(String v1, String v2) {
    return smaller(v1, v2, false);
  }

  /**
   * 要求 v1 大于等于 v2。
   * 
   * @since 0.6.0
   * @param v1   参数1
   * @param v2lp 参数2是否要从表查找，默认 false
   * @param v2   参数2
   */
  public SdbStatement largerOrEqual(String v1, String v2, boolean v2lp) {
    conditions.add(new Condition(CondType.LARGER_OR_EQUAL, v1, v2, null, v2lp, null));
    return this;
  }

  /**
   * 要求 v1 大于等于 v2。
   * 
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2，不会从表中查找。
   */
  public SdbStatement largerOrEqual(String v1, String v2) {
    return largerOrEqual(v1, v2, false);
  }

  /**
   * 要求v1 小于等于 v2。
   * 
   * @since 0.6.0
   * @param v1   参数1
   * @param v2lp 参数2是否要从表查找，默认 false
   * @param v2   参数2
   */
  public SdbStatement smallerOrEqual(String v1, String v2, boolean v2lp) {
    conditions.add(new Condition(CondType.SMALLER_OR_EQUAL, v1, v2, null, v2lp, null));
    return this;
  }

  /**
   * 要求v1 小于等于 v2。
   * 
   * @since 0.6.0
   * @param v1 参数1
   * @param v2 参数2，不会从表中查找。
   */
  public SdbStatement smallerOrEqual(String v1, String v2) {
    return smallerOrEqual(v1, v2, false);
  }

  // ===--- 其它 ---===

  private volatile List<String> selectColumns;

  /**
   * 设置 SELECT 能选择的列。如果 SELECT 操作上下文没有本限制，它会选中 * 。
   * <p>
   * <b>要想生效，该条件必须在操作上下文的直接断言内。</b>
   * 
   * @apiNote 本条件不参与其他断言的判断
   * @apiNote 仅对 {@link SdbEnums.ACTION_TYPE#SELECT} 的操作上下文有效。通常情况下
   *          {@link #toString()} 等方法不会导出本设置。
   * @param columnHead 要选择的列名
   * @throws NullPointerException 参数为 Null
   * @since 0.6.0
   */
  public SdbStatement selectThat(String columnHead) {
    if (this.selectColumns == null)
      this.selectColumns = new ArrayList<>();
    this.selectColumns.add(Objects.requireNonNull(columnHead, "要选择的列不能是 Null"));
    return this;
  }

  /**
   * @return 获取 SELECT 能选择的列，以列表形式传递
   * @since 0.6.0
   */
  public String[] selectWhat() {
    return (selectColumns == null || selectColumns.isEmpty()) ? new String[] { "*" }
        : selectColumns.toArray(new String[0]);
  }
}