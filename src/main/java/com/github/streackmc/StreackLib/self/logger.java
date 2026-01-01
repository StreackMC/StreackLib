package com.github.streackmc.StreackLib.self;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 全局静态日志工具，自动根据运行环境选择日志后端。
 * <p>
 * 使用方式：
 * <pre>{@code
 * // 在主类 onEnable 里初始化一次
 * logger.init(this);
 *
 * // 任意位置
 * logger.info("玩家 %s 加入了游戏", player.getName());
 * }</pre>
 * <p>
 * 优先级（运行时一次性探测）：
 * <ol>
 *   <li>Bukkit 插件 Logger（通过 {@link #init(JavaPlugin)} 注入）</li>
 *   <li>SLF4J（如果存在）</li>
 *   <li>java.util.logging（保底）</li>
 * </ol>
 * <p>
 * 未来支持 Fabric 时，只需新增一个 {@link Backend} 实现即可，无需改动业务代码。
 *
 * @author KimiAI 编写
 * @author kdxiaoyi 审计
 * @since 0.0.0
 */
public final class logger {

  /* ===================== 对外 API ===================== */

  /**
   * 输出调试信息
   * @param msg 信息
   * @param arg 其它内容
   */
  public static void debug(@NotNull String msg, Object... arg) { backend().debug(format(msg, arg)); }
  /**
   * 输出一般信息
   * @param msg 信息
   * @param arg 其它内容
   */
  public static void info(@NotNull String msg, Object... arg)  { backend().info(format(msg, arg)); }
  /**
   * 输出警告信息
   * @param msg 信息
   * @param arg 其它内容
   */
  public static void warning(@NotNull String msg, Object... arg) { backend().warn(format(msg, arg)); }
  /**
   * 输出警告信息
   * @param msg 信息
   * @param arg 其它内容
   */
  public static void warn(@NotNull String msg, Object... arg) { warning(msg, arg); }
  /**
   * 输出错误信息
   * @param msg 信息
   * @param arg 其它内容
   */
  public static void severe(@NotNull String msg, @Nullable Throwable t) { backend().error(msg, t); }
  /**
   * 输出错误信息
   * @param msg 信息
   * @param arg 其它内容
   */
  public static void error(@NotNull String msg, @Nullable Throwable t) { severe(msg, t); }
  /**
   * 输出错误信息
   * @param msg 信息
   * @param arg 其它内容
   */
  public static void err(@NotNull String msg, @Nullable Throwable t) { severe(msg, t); }

  /* ===================== 内部实现 ===================== */

  /** 供外部探测的插件实例，null 表示未接入 Bukkit */
  public static JavaPlugin plugin = null;

  /** 日志后端接口，隔离具体实现 */
  public interface Backend {
    void debug(String msg);
    void info(String msg);
    void warn(String msg);
    void error(String msg, Throwable t);
  }

  /** 后端实例，惰性初始化且只初始化一次 */
  private static volatile Backend BACKEND;

  private static Backend backend() {
    if (BACKEND == null) {
      synchronized (logger.class) {
        if (BACKEND == null) {
          BACKEND = detectBackend();
        }
      }
    }
    return BACKEND;
  }

  /** 按优先级探测并实例化 Backend */
  private static Backend detectBackend() {
    // 1. Bukkit
    if (plugin != null) return new BukkitBackend();
    // 2. SLF4J
    try {
      Class.forName("org.slf4j.LoggerFactory");
      return new Slf4jBackend();
    } catch (ClassNotFoundException ignore) { /* 不存在 */ }
    // 3. JUL 保底
    return new JulBackend();
  }

  /* -------------------- 后端实现 -------------------- */

  /** Bukkit 插件日志 */
  private static final class BukkitBackend implements Backend {
    private Logger log() { return plugin.getLogger(); }

    public void debug(String msg) { log().fine(msg); }
    public void info(String msg)  { log().info(msg); }
    public void warn(String msg)  { log().warning(msg); }
    public void error(String msg, Throwable t) { log().log(Level.SEVERE, msg, t); }
  }

  /** SLF4J 日志（无插件时） */
  private static final class Slf4jBackend implements Backend {
    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(logger.class);

    public void debug(String msg) { LOG.debug(msg); }
    public void info(String msg)  { LOG.info(msg); }
    public void warn(String msg)  { LOG.warn(msg); }
    public void error(String msg, Throwable t) { LOG.error(msg, t); }
  }

  /** java.util.logging 保底 */
  private static final class JulBackend implements Backend {
    private static final Logger LOG = Logger.getLogger(logger.class.getName());

    public void debug(String msg) { LOG.fine(msg); }
    public void info(String msg)  { LOG.info(msg); }
    public void warn(String msg)  { LOG.warning(msg); }
    public void error(String msg, Throwable t) { LOG.log(Level.SEVERE, msg, t); }
  }

  /* -------------------- 工具方法 -------------------- */

  /** 简单格式化：用 String.format，兼容 %s 等占位符 */
  private static String format(String msg, Object... arg) {
    return arg.length == 0 ? msg : String.format(msg, arg);
  }

  private logger() {} // 禁止实例化
}
