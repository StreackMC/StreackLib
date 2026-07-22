package com.github.streackmc.StreackLib.types.SDatabase;

import java.sql.Connection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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
