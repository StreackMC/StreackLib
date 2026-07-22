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
   */
  static String q(String identifier) {
    if (identifier == null || identifier.isEmpty()) return "";
    return "`" + identifier.replace("`", "``") + "`";
  }

  /**
   * SQL 字符串字面量（转义单引号和反斜杠）。
   *
   * @deprecated 当前版本使用字符串拼接生成 SQL，仅做基础转义，
   *             不能完全防御 SQL 注入。计划在 0.7.0 迁移至 PreparedStatement。
   */
  @Deprecated
  static String literal(String val) {
    if (val == null) return "NULL";
    return "'" + val.replace("\\", "\\\\").replace("'", "''") + "'";
  }
}
