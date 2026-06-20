package com.github.streackmc.StreackLib.self;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

import org.apache.logging.log4j.util.InternalApi;
import org.jetbrains.annotations.NotNull;

import com.github.streackmc.StreackLib.StreackLib;

/**
 * 全局静态日志工具，自动根据运行环境选择日志后端。
 * <p>
 * 使用方式：
 * 
 * <pre>{@code
 * // 在主类 onEnable 里初始化一次
 * logger.init(this);
 *
 * // 任意位置
 * logger.info("玩家 %s 加入了游戏", player.getName());
 * }</pre>
 * <p>
 * 后端选择策略（通过 {@link manager#getBackend()}.{@link
 * com.github.streackmc.StreackLib.self.backend.StreackLibDefaultBackend#getLogBackend() getLogBackend()} 获取）：
 * <ul>
 *   <li>{@link com.github.streackmc.StreackLib.self.backend.StreackLibBukkitBackend} — Bukkit 环境，返回 {@code BukkitLogBackend} 使用 {@code plugin.getLogger()}</li>
 *   <li>{@link com.github.streackmc.StreackLib.self.backend.StreackLibDefaultBackend} — 默认/嵌入环境，返回 {@code DefaultLogBackend} 惰性探测 SLF4J → java.util.logging</li>
 * </ul>
 * <p>
 * 未来支持 Fabric 时，只需新增一个 {@link LoggerBackend} 实现即可，无需改动业务代码。
 *
 * @author KimiAI 编写
 * @author GitHub Copilot 编写
 * @author kdxiaoyi 审计
 * @since 0.4.0
 */
@InternalApi
public final class logger {

  /* ===================== 对外 API ===================== */

  /**
   * 输出调试信息
   * 这只在启用相应配置项后生效
   * 
   * @param args 任意数量、任意类型的参数。若第一个参数为 String，则视为格式化模板（其余参数用于 format）。
   *             若最后一个参数为 Throwable，则对 severe/error 系列方法会将其作为异常输出；其它级别会将堆栈附加到消息。
   */
  public static void debug(@NotNull Object... args) {
    try {
      if (!StreackLib.isDebugMode()) {
        return;
      }
    } catch (Exception ignored) {
      return; // 读取配置时发生异常，安全起见不输出调试信息
    }
    Payload p = extract(args);
    if (p.t != null) {
      backend().debug(p.msg + "\n" + throwableToString(p.t));
    } else {
      backend().debug(p.msg);
    }
  }

  /**
   * 输出一般信息
   * 
   * @param args 任意数量、任意类型的参数。若第一个参数为 String，则视为格式化模板（其余参数用于 format）。
   *             若最后一个参数为 Throwable，则对 severe/error 系列方法会将其作为异常输出；其它级别会将堆栈附加到消息。
   */
  public static void info(@NotNull Object... args) {
    Payload p = extract(args);
    if (p.t != null) {
      backend().info(p.msg + "\n" + throwableToString(p.t));
    } else {
      backend().info(p.msg);
    }
  }

  /**
   * 输出警告信息
   * 
   * @param args 任意数量、任意类型的参数。若第一个参数为 String，则视为格式化模板（其余参数用于 format）。
   *             若最后一个参数为 Throwable，则对 severe/error 系列方法会将其作为异常输出；其它级别会将堆栈附加到消息。
   */
  public static void warning(@NotNull Object... args) {
    Payload p = extract(args);
    if (p.t != null) {
      backend().warn(p.msg + "\n" + throwableToString(p.t));
    } else {
      backend().warn(p.msg);
    }
  }

  /**
   * 输出警告信息，别名
   * 
   * @param args 任意数量、任意类型的参数。若第一个参数为 String，则视为格式化模板（其余参数用于 format）。
   *             若最后一个参数为 Throwable，则对 severe/error 系列方法会将其作为异常输出；其它级别会将堆栈附加到消息。
   */
  public static void warn(@NotNull Object... args) {
    warning(args);
  }

  /**
   * 输出错误信息（可携带 Throwable）
   * 
   * @param args 任意数量、任意类型的参数。若第一个参数为 String，则视为格式化模板（其余参数用于 format）。
   *             若最后一个参数为 Throwable，则对 severe/error 系列方法会将其作为异常输出；其它级别会将堆栈附加到消息。
   */
  public static void severe(@NotNull Object... args) {
    Payload p = extract(args);
    backend().error(p.msg, p.t);
  }

  /**
   * 输出错误信息（可携带 Throwable）
   * 
   * @param args 任意数量、任意类型的参数。若第一个参数为 String，则视为格式化模板（其余参数用于 format）。
   *             若最后一个参数为 Throwable，则对 severe/error 系列方法会将其作为异常输出；其它级别会将堆栈附加到消息。
   */
  public static void error(@NotNull Object... args) {
    severe(args);
  }

  /**
   * 输出错误信息（可携带 Throwable）
   * 
   * @param args 任意数量、任意类型的参数。若第一个参数为 String，则视为格式化模板（其余参数用于 format）。
   *             若最后一个参数为 Throwable，则对 severe/error 系列方法会将其作为异常输出；其它级别会将堆栈附加到消息。
   */
  public static void err(@NotNull Object... args) {
    severe(args);
  }

  /* ===================== 内部实现 ===================== */

  public static interface LoggerBackend {
    /** 输出调试信息 */
    void debug(String msg);
    /** 输出一般信息 */
    void info(String msg);
    /** 输出警告信息 */
    void warn(String msg);
    /** 输出错误信息（可携带异常） */
    void error(String msg, Throwable t);
  }

  private static LoggerBackend backend() {
    return manager.getBackend().getLogBackend();
  }

  /* -------------------- 工具方法 -------------------- */

  /** 简单格式化：用 String.format，兼容 %s 等占位符 */
  private static String format(String msg, Object... arg) {
    return arg.length == 0 ? msg : String.format((msg == null) ? "" : msg, arg);
  }

  /** 解析传入参数，返回最终消息与可选 Throwable */
  private static final class Payload {
    final String msg;
    final Throwable t;

    Payload(String msg, Throwable t) {
      this.msg = msg;
      this.t = t;
    }
  }

  private static Payload extract(Object... args) {
    if (args == null || args.length == 0)
      return new Payload("", null);
    // 如果只有一个参数
    if (args.length == 1) {
      Object o = args[0];
      if (o instanceof Throwable) {
        return new Payload(((Throwable) o).toString(), (Throwable) o);
      }
      return new Payload(String.valueOf(o), null);
    }
    // 检查最后一个是否为 Throwable
    Throwable lastAsThrowable = null;
    int len = args.length;
    if (args[len - 1] instanceof Throwable) {
      lastAsThrowable = (Throwable) args[len - 1];
      len -= 1; // 剩余用于消息构造
    }
    Object first = args[0];
    if (first instanceof String) {
      Object[] fmtArgs = len <= 1 ? new Object[0] : Arrays.copyOfRange(args, 1, len);
      String msg = fmtArgs.length == 0 ? (String) first : format((String) first, fmtArgs);
      return new Payload(msg, lastAsThrowable);
    } else {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < len; i++) {
        if (i > 0)
          sb.append(' ');
        sb.append(String.valueOf(args[i]));
      }
      return new Payload(sb.toString(), lastAsThrowable);
    }
  }

  private static String throwableToString(Throwable t) {
    if (t == null)
      return "";
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }

  private logger() {
  } // 禁止实例化
}
