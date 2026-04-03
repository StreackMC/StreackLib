package com.github.streackmc.StreackLib;

import java.io.File;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus.Internal;

import com.github.streackmc.StreackLib.bukkit.SBukkit;
import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.github.streackmc.StreackLib.utils.SConfig;

/**
 * 杂项工具类，也作为其它工具类的跳板。
 * 作跳板用时和new Sxxx()并没有什么区别（（
 * 
 * @author kdxiaoyi
 * @since 0.4.3
 */
public final class StreackLib {

  public final static class EVENTS {
    /**
     * TPS被刷新
     * @param TPS double | 此刻的TPS
     * @see StreackLib#CURRENT_TPS
     * @see SBukkit#getServerTPS()
     * @deprecated 0.4.7 起删除该事件
     */
    @deprecated
    public static final String LIVE_TPS_REFRESHED = "streacklib.streacklib:tps.current.refreshed";
  }

  /** StreackLib内部持有的HTTP服务器 */
  public static HTTPServer httpServer;
  /** 当前TPS */
  public static double currentTPS = -1.0;

  public static class ENV {
    /** StreackLib的配置文件对象 */
    public static SConfig conf;
    /** StreackLib的默认配置文件对象 */
    public static SConfig defaultConf;
    /** StreackLib的构建信息对象 */
    public static SConfig buildConf;
    /** StreackLib的数据目录 */
    public static File dataPath;
    /** 服务器配置文件 */
    public static SConfig serverProperties;
  }

  // 私有
  private StreackLib() { // 禁止实例化
  }
  static final Deque<Long> tickTimes = new ArrayDeque<>();

  // ===================== Class Caller =====================

  /**
   * 获取内联HTTPServer对象
   * 该对象由StreackLib依据配置文件启动，可能受用户影响无效
   * @return 获取到的对象；若当前未启动服务器则为null
   */
  @Nullable
  public static HTTPServer getHttpServer() {
    return httpServer;
  }

  /**
   * 新建一个HTTPServer对象
   * @param hostname 监听地址
   * @param port 监听端口
   * @return 获取到的对象
   */
  public static HTTPServer newHttpServer(String hostname, int port) {
    return new HTTPServer(hostname, port, initBukkit.pluginSelf);
  }

  /**
   * 获取一个指向一个文件的配置文件对象。使用此对象方法可以更快捷地操作配置文件。建议使用前先使用Bukkit自带的释放配置文件以放出默认配置文件。
   * @param file 配置文件的对象
   * @param type 配置文件的类型
   * @return 一个配置文件对象
   */
  public static SConfig initConf(File file, String type) {
    return new SConfig(file, type);
  }

  // ===================== Other Utils =====================

  /**
   * 获取当前StreackLib的调试状态
   * 
   * @return
   * @since 0.4.3
   */
  public static boolean isDebugMode() {
    return ENV.conf.getBoolean("debug", false);
  }

  /**
   * 以系统时区格式化时间
   * 
   * @param time   目标时间戳
   *               <p>
   *               为 null 时默认为当前时间戳
   * @param format 格式
   *               <p>
   *               为 null 或为空时默认为 "yyyy-MM-dd HH:mm:ss.SSSS"
   * @see #StreackLib.formatTime(Long, String, ZoneId)
   * @return 处理好的时间
   * @throws IllegalArgumentException time超出范围 或 format无效
   * @since 0.4.4
   */
  public static String formatTime(@Nullable Long time, @Nullable String format) throws IllegalArgumentException {
    long t = (time == null)
        ? System.currentTimeMillis()
        : time.longValue();
    try {
      Instant.ofEpochMilli(t);// 超范围会抛异常
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("时间戳超出有效范围：" + t, e);
    }
    String f = (format == null || format.isEmpty())
        ? "yyyy-MM-dd HH:mm:ss.SSSS"
        : format;
    return java.time.LocalDateTime
        .ofInstant(java.time.Instant.ofEpochMilli(t), java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern(f));
  }

  /**
   * 以指定时区格式化时间
   * 
   * @param time     目标时间戳
   *                 <p>
   *                 为 null 时默认为当前时间戳
   * @param format   格式
   *                 <p>
   *                 为 null 或为空时默认为 "yyyy-MM-dd HH:mm:ss.SSSS"
   * @param timezone 时区，见于 {@link java.time.ZoneId}
   * @return 处理好的时间
   * @throws IllegalArgumentException time超出范围 或 format无效 或 timezone无效
   * @since 0.4.4
   * @see #StreackLib.formatTime(Long, String)
   */
  public static String formatTime(@Nullable Long time, @Nullable String format, ZoneId timezone) throws IllegalArgumentException {
    Objects.requireNonNull(timezone, "未设置时区");
    long t = (time == null)
        ? System.currentTimeMillis()
        : time.longValue();
    try {
      Instant.ofEpochMilli(t);// 超范围会抛异常
    } catch (DateTimeException e) {
      throw new IllegalArgumentException("时间戳超出有效范围：" + t, e);
    }
    String f = (format == null || format.isEmpty())
        ? "yyyy-MM-dd HH:mm:ss.SSSS"
        : format;
    return java.time.LocalDateTime
        .ofInstant(java.time.Instant.ofEpochMilli(t), java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern(f));
  }

  private static final AtomicLong uniqueIDCounter = new AtomicLong(0);
  @Internal
  /**
   * 获取一个全局唯一的ID
   * @return
   * @since 0.4.4
   */
  public static Long getUniqueID() {
    return uniqueIDCounter.getAndIncrement();
  }
}