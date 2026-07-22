package com.github.streackmc.StreackLib.types.SDatabase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SdbAction</h2>
 *
 * 一次数据库操作的会话。持有 1 个{@link Connection} 并控制事务生命周期。
 *
 * <h3>事务语义</h3>
 * <ul>
 *   <li>构造时自动 {@code setAutoCommit(false)}——手动事务模式</li>
 *   <li>{@link #apply(SdbActionContext)} / {@link #apply(String)}：在同一事务内执行</li>
 *   <li>{@link #commit()}：提交事务</li>
 *   <li>{@link #rollback()}：回滚事务</li>
 *   <li>{@link #close()}：未提交/回滚 → 自动回滚；归还连接至池</li>
 * </ul>
 *
 * <h3>典型用法</h3>
 * <pre><code>
 * try (SdbAction action = db.act()) {
 *     action.apply(ctx1);
 *     action.apply(ctx2);
 *     action.commit();
 * }
 * </code></pre>
 *
 * @since 0.6.0
 */
public class SdbAction extends StreackLibNewable implements AutoCloseable {

  private final Connection connection;
  private final SdbManager manager;
  private final String    profileId;
  private boolean committed  = false;
  private boolean rolledBack = false;

  SdbAction(Connection conn, SdbManager manager, String profileId) throws SQLException {
    this.connection = conn;
    this.manager    = manager;
    this.profileId  = profileId;
    conn.setAutoCommit(false);
  }

  // ==========================================
  // apply
  // ==========================================

  /**
   * 执行一个操作上下文。将其构建的 SQL 批次在<b>当前事务</b>内执行。
   *
   * @param ctx 操作上下文（链式构建后的最终节点）
   * @return 最后一条 SQL 的执行结果
   * @throws SQLException SQL 执行错误
   */
  public SdbDataEntry apply(SdbActionContext ctx) throws SQLException {
    List<String> sqlBatch = ctx.toSqlString();
    int      totalAffected = 0;
    List<SConfig> lastRows = List.of();

    for (String sql : sqlBatch) {
      try (Statement stmt = connection.createStatement()) {
        boolean hasResultSet = stmt.execute(sql);
        if (hasResultSet) {
          try (ResultSet rs = stmt.getResultSet()) {
            lastRows = readResultSet(rs);
            totalAffected += lastRows.size();
          }
        } else {
          int affected = stmt.getUpdateCount();
          if (affected >= 0) totalAffected += affected;
        }
      }
    }
    return new SdbDataEntry(totalAffected, lastRows);
  }

  /**
   * 执行原始 SQL 字符串（由调用者负责防范注入）。
   *
   * @param rawSql 原始 SQL 命令
   * @return 执行结果
   * @throws SQLException SQL 执行错误
   */
  public SdbDataEntry apply(String rawSql) throws SQLException {
    try (Statement stmt = connection.createStatement()) {
      boolean hasResultSet = stmt.execute(rawSql);
      if (hasResultSet) {
        try (ResultSet rs = stmt.getResultSet()) {
          List<SConfig> rows = readResultSet(rs);
          return new SdbDataEntry(rows.size(), rows);
        }
      }
      return new SdbDataEntry(stmt.getUpdateCount());
    }
  }

  // ==========================================
  // 事务控制
  // ==========================================

  /** 提交事务，持久化所有已执行的修改 */
  public void commit() throws SQLException {
    connection.commit();
    committed = true;
  }

  /** 回滚事务，撤销所有未提交的修改 */
  public void rollback() throws SQLException {
    connection.rollback();
    rolledBack = true;
  }

  // ==========================================
  // AutoCloseable
  // ==========================================

  /**
   * 归还连接。<br>
   * 如果既未{@link #commit()} 也未{@link #rollback()}，则自动回滚。
   */
  @Override
  public void close() throws Exception {
    try {
      if (!committed && !rolledBack) {
        try { connection.rollback(); } catch (SQLException ignored) {}
      }
      try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
    } finally {
      try { connection.close(); } catch (SQLException ignored) {}
      manager.release(profileId);
    }
  }

  // ==========================================
  // Internal
  // ==========================================

  /** 将 ResultSet 转为 {@code List<SConfig>} — 每个 SConfig 为一行，key=列名 */
  private static List<SConfig> readResultSet(ResultSet rs) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    int colCount = meta.getColumnCount();
    List<SConfig> rows = new ArrayList<>();
    while (rs.next()) {
      SConfig row = new SConfig("", SConfig.TYPES.JSON, null);
      for (int i = 1; i <= colCount; i++) {
        String colName = meta.getColumnName(i);
        Object value   = rs.getObject(i);
        row.put(colName, value != null ? value : "");
      }
      rows.add(row);
    }
    return rows;
  }
}
