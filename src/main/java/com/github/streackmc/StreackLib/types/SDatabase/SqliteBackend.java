package com.github.streackmc.StreackLib.types.SDatabase;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * <h2>SqliteBackend</h2>
 *
 * SQLite 单连接后端。由于 SQLite 设计上不支持并发写入，此处使用
 * 单连接 + {@code synchronized} 串行化所有借用。
 *
 * <p>借出的连接是一个代理对象，其{@code close()} 方法被拦截为 no-op，
 * 以支持事务语义下的"归还"操作。真正的关闭只发生在{@link #close()}。
 *
 * @since 0.6.0
 */
class SqliteBackend implements Backend {

  private final File dbFile;
  private final Connection realConn;
  private final Connection sharedConn;

  SqliteBackend(File dbFile) throws Exception {
    this.dbFile = dbFile;
    File parent = dbFile.getParentFile();
    if (parent != null && !parent.exists()) parent.mkdirs();

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
    try { realConn.close(); } catch (Exception ignored) {}
  }

  // ==========================================
  // Internal
  // ==========================================

  /** 将真实连接的 close() 拦截为 no-op */
  private static Connection wrapConnection(Connection real) {
    return (Connection) Proxy.newProxyInstance(
        Connection.class.getClassLoader(),
        new Class<?>[]{Connection.class},
        new CloseInterceptor(real));
  }

  private record CloseInterceptor(Connection delegate) implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable {
      if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
        return null; // 拦截 close()
      }
      return method.invoke(delegate, args);
    }
  }
}
