package com.github.streackmc.StreackLib.types.SDatabase;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.errors.raw.StreackLibNewableRuntimeException;
import com.github.streackmc.StreackLib.types.SConfig;

/**
 * <h2>SdbManager</h2>
 *
 * 全局静态连接池管理器。对每个数据库 Profile 维护一个{@link Backend}，
 * 并采用引用计数决定何时销毁。
 *
 * <h3>线程安全</h3>
 * 对{@link #POOLS} 和{@link #REFS} 的所有复合操作由内部的静态锁保证原子性。
 *
 * @since 0.6.0
 */
class SdbManager {

  // ==========================================
  // 全局注册表
  // ==========================================

  /** profileId → Backend */
  private static final ConcurrentHashMap<String, Backend> POOLS = new ConcurrentHashMap<>();
  /** profileId → 活跃引用数 */
  private static final ConcurrentHashMap<String, Integer> REFS  = new ConcurrentHashMap<>();
  /** 保护 POOLS + REFS 复合操作 */
  private static final Object POOL_LOCK = new Object();

  // ==========================================
  // acquire / release
  // ==========================================

  /**
   * 获取或创建一个 Backend，并将引用计数 +1。
   *
   * @param profileConf 数据库 Profile 配置节
   * @param profileId   唯一标识（通常为 Profile 名）
   * @return 目标 Backend
   * @throws Exception 创建连接池失败时抛出
   */
  static Backend acquire(SConfig profileConf, String profileId) throws Exception {
    synchronized (POOL_LOCK) {
      try {
        Backend backend = POOLS.computeIfAbsent(profileId, id -> {
          try {
            return createBackend(profileConf);
          } catch (Exception e) {
            throw new BackendCreationException(e);
          }
        });
        REFS.merge(profileId, 1, (a, b) -> a + b);
        return backend;
      } catch (BackendCreationException e) {
        throw e.getCause() instanceof Exception ? (Exception) e.getCause() : new Exception(e.getCause());
      }
    }
  }

  /** 内部异常，用于在 computeIfAbsent 的 lambda 中安全传递受检异常 */
  private static class BackendCreationException extends StreackLibNewableRuntimeException {
    BackendCreationException(Throwable cause) { super(cause); }
  }

  /**
   * 释放一个引用。当引用计数降至 0 时销毁 Backend。
   *
   * @param profileId 唯一标识
   */
  static void release(String profileId) {
    synchronized (POOL_LOCK) {
      Integer count = REFS.get(profileId);
      if (count == null) return;

      int newCount = count - 1;
      if (newCount <= 0) {
        REFS.remove(profileId);
        Backend backend = POOLS.remove(profileId);
        if (backend != null) backend.close();
      } else {
        REFS.put(profileId, newCount);
      }
    }
  }

  // ==========================================
  // 工厂
  // ==========================================

  static private Backend createBackend(SConfig profileConf) throws Exception {
    String mode = profileConf.getString("mode", "").toLowerCase();
    switch (mode) {
      case "sqlite":
      case "sldb": {
        String filePath = profileConf.getString("file",
            StreackLib.ENV.dataPath + File.separator + "database.db");
        return new SqliteBackend(new File(filePath));
      }
      case "mysql": {
        String host     = profileConf.getString("host", "localhost");
        int    port     = profileConf.getInt("port", 3306);
        String database = profileConf.getString("database", "");
        String user     = profileConf.getString("user", "");
        String password = profileConf.getString("password", "");
        if (user.isEmpty() || "root".equalsIgnoreCase(user)) {
          throw new IllegalArgumentException(
              "MySQL 不允许使用 root 登录。请创建一个专用数据库用户并在 config.yml 中配置");
        }
        return new MysqlBackend(host, port, database, user, password);
      }
      default:
        throw new IllegalArgumentException("Unsupported database mode: " + mode);
    }
  }
}
