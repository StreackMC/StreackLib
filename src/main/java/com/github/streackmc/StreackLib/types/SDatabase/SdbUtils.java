package com.github.streackmc.StreackLib.types.SDatabase;

/**
 * <h2>SdbUtils</h2>
 * SDatabase 模块内部共享的 SQL 转义工具。
 *
 * @since 0.6.0
 */
final class SdbUtils {

  private SdbUtils() {}

  /**
   * 反引号包裹 SQL 标识符并转义内部反引号。
   * <ul>
   *   <li>{@code "table"} → {@code "`table`"}</li>
   *   <li>{@code "weird`name"} → {@code "`weird``name`"}</li>
   * </ul>
   * <p>
   * 用于表名、列名、CTE 名等<b>标识符</b>。JDBC 不允许把标识符当作 {@code ?} 参数绑定，
   * 因此标识符只能靠反引号转义（防御保留字/畸形标识符破坏语句，并兜底拼接注入），
   * <b>这不等同于值的参数化保护</b>。若标识符来自不可信输入，调用方仍需白名单校验。
   */
  static String q(String identifier) {
    if (identifier == null || identifier.isEmpty()) return "";
    return "`" + identifier.replace("`", "``") + "`";
  }

  /**
   * SQL 字符串字面量（转义单引号和反斜杠）。
   * <p>
   * 仅用于<b>预览/调试</b>路径（{@code toString()} 系列）：把条件<b>值</b>拼成字符串 SQL 时的基础转义。
   * 该路径<b>非参数化</b>，其产物不应回灌执行器。
   * <b>真正执行的参数化路径（{@code toPrepared()}）不使用本方法</b>，值走 {@code ?} 占位符。
   *
   * @apiNote 本方法是字符串拼接的兜底转义，不能防御 SQL 注入；只服务于预览，不服务于执行。
   */
  static String literal(String val) {
    if (val == null) return "NULL";
    return "'" + val.replace("\\", "\\\\").replace("'", "''") + "'";
  }
}
