package com.github.streackmc.StreackLib.self.backend;

import java.io.File;
import java.util.logging.Level;

import org.jetbrains.annotations.ApiStatus.Internal;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.self.logger.LoggerBackend;
import com.github.streackmc.StreackLib.types.HTTPServer;
import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/** 内部功能跨平台跳板，需要实现全部方法，否则视作未实现，返回默认值。 */
@Internal
public class StreackLibDefaultBackend extends StreackLibNewable {
  public class DefaultLogBackend implements logger.LoggerBackend {

  private volatile LoggerBackend detected;
  private volatile boolean resolved;

  private LoggerBackend resolve() {
    if (!resolved) {
      synchronized (this) {
        if (!resolved) {
          detected = detect();
          resolved = true;
        }
      }
    }
    return detected;
  }

  /** 按优先级探测可用日志实现 */
  private static LoggerBackend detect() {
    // 1. SLF4J — 检查是否有可用 Provider（排除 NOP）
    try {
      Class.forName("org.slf4j.LoggerFactory");
      org.slf4j.Logger slf4j = org.slf4j.LoggerFactory.getLogger(DefaultLogBackend.class);
      if (!slf4j.getClass().getName().equals("org.slf4j.helpers.NOPLogger")) {
        return new Slf4jBackend(slf4j);
      }
    } catch (Exception ignored) {
      // SLF4J 不可用
    }

    // 2. JUL 保底
    return new JulBackend();
  }

  @Override
  public void debug(String msg) {
    resolve().debug(msg);
  }

  @Override
  public void info(String msg) {
    resolve().info(msg);
  }

  @Override
  public void warn(String msg) {
    resolve().warn(msg);
  }

  @Override
  public void error(String msg, Throwable t) {
    resolve().error(msg, t);
  }

  // ==================== 内部实现 ====================

  /** SLF4J 后端 */
  private static final class Slf4jBackend implements LoggerBackend {
    private final org.slf4j.Logger log;

    Slf4jBackend(org.slf4j.Logger log) {
      this.log = log;
    }

    @Override
    public void debug(String msg) {
      log.info(msg);
    }

    @Override
    public void info(String msg) {
      log.info(msg);
    }

    @Override
    public void warn(String msg) {
      log.warn(msg);
    }

    @Override
    public void error(String msg, Throwable t) {
      log.error(msg, t);
    }
  }

  /** java.util.logging 保底后端 */
  private static final class JulBackend implements LoggerBackend {
    private static final java.util.logging.Logger LOG = java.util.logging.Logger.getLogger(DefaultLogBackend.class.getName());

    @Override
    public void debug(String msg) {
      LOG.info(msg);
    }

    @Override
    public void info(String msg) {
      LOG.info(msg);
    }

    @Override
    public void warn(String msg) {
      LOG.warning(msg);
    }

    @Override
    public void error(String msg, Throwable t) {
      LOG.log(Level.SEVERE, msg, t);
    }
  }
  }

  public final DefaultLogBackend logBackend = new DefaultLogBackend();

  /**
   * 获取日志后端，子类可重写以提供平台特定实现。
   * <p>
   * 默认返回 {@link #logBackend}（惰性探测 SLF4J → JUL）。
   */
  public LoggerBackend getLogBackend() {
    return logBackend;
  }

  /**
   * 不执行任何初始化。
   * <p>
   * 警告：不要在构造器内调用 {@link #logger}！因为平台加载器（如 Bukkit）会先 new 子类实例，
   * 然后才赋值给 {@code manager.backend}。如果在 {@code super()} 中调用 logger，
   * 那时 {@code manager.backend} 还指向旧的默认实例，导致日志跑到错误的后端去。
   * <p>
   * 所有初始化逻辑（HTTPServer 等）请放在 {@link #init()} 中，由加载器在设好
   * {@code manager.backend} 后显式调用。
   */
  public StreackLibDefaultBackend() {
  }

  /**
   * 初始化 HTTP 服务器等模块。
   * <p>
   * <b>必须</b>在 {@code manager.backend} 被设为本实例之后调用，这样才能确保
   * 内部的 {@code logger} 调用路由到正确的后端。
   */
  public void init() {
    if (StreackLib.ENV.conf == null) {
      logger.warn("StreackLib.ENV.conf 未初始化，跳过 HTTP 服务器启动");
      return;
    }
    String host = StreackLib.ENV.conf.getString("http-server.host", "0.0.0.0");
    int port = StreackLib.ENV.conf.getInt("http-server.port", 8080);
    logger.info("处理模块：HTTPServer");
    if (StreackLib.ENV.conf.getBoolean("http-server.enabled", false)) {
      this.httpServer = new HTTPServer(host, port);
      this.setHttpServer(httpServer);
      this.httpServer.startServer();
      logger.info("HTTP 服务器已启动于 " + host + ":" + port);
    } else {
      logger.info("HTTP 服务器未启用");
    }
  }

  /** 不进行任何初始化，仅供manager新建默认用 */
  public StreackLibDefaultBackend(String v) {
    // 嵌入/非插件模式下 ENV 未初始化 → 填充空白内存配置确保后续读取不 NPE
    // 注意：此处不可使用 new SConfig(String, String, String) 或 logger，因为 manager
    // 的静态初始化尚未完成，会产生循环依赖导致 NPE。
    if (StreackLib.ENV.conf == null) {
      StreackLib.ENV.conf = new SConfig(new java.util.HashMap<>(), "YAML", ".yml");
    }
    if (StreackLib.ENV.dataPath == null) {
      StreackLib.ENV.dataPath = new File(System.getProperty("user.dir", "."));
    }
  }

  /** StreackLib内部持有的HTTP服务器 */
  public volatile HTTPServer httpServer = null;
  public void setHttpServer(HTTPServer hs) { httpServer=hs; };

  /** @return {@type double[5]} TPS数据 
   * @throws Exception */
  public double[] getLiveTPS() throws Exception {
    double[] defaultData = { -1.0, -1.0, -1.0, -1.0, System.currentTimeMillis() };
    return defaultData;
  }

  /**
   * @return {@type SConfig} 具有键：
   *         <p>
   *         {@type String} 目标target  原因reason  执行者op
   *         <p>
   *         {@type boolean} 状态banned
   *         <p>
   *         {@type long} 生效时间戳create  过期时间戳expire  (永久封禁则 expire = -1L )
   *         <p>
   */
  public SConfig checkBan(String target) {
    return getDefaultBanEntry(target);
  }

  protected SConfig getDefaultBanEntry(String target) {
    SConfig data = new SConfig("", SConfig.TYPES.JSON, "");
    data.putString("target", target);
    data.putBoolean("banned", false);
    data.putString("reason", "");
    data.putString("op", "");
    data.putLong("create", 0L);
    data.putLong("expire", 0L);
    return data;
  }
}
