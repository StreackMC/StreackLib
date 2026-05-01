package com.github.streackmc.StreackLib;

import java.io.File;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.apache.logging.log4j.util.InternalApi;
import org.jetbrains.annotations.ApiStatus.Internal;

import com.github.streackmc.StreackLib.self.manager;
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

  /** StreackLib的环境信息 */
  @InternalApi
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

  // =====================    私有量    =====================

  private StreackLib() { // 禁止实例化
  }
  /** 唯一ID生成器 */
  private static final AtomicLong uniqueIDCounter = new AtomicLong(596478L);

  // ===================== Class Caller =====================

  /**
   * 获取内联HTTPServer对象
   * 该对象由StreackLib依据配置文件启动，可能受用户影响无效
   * @return 获取到的对象；若当前未启动服务器则为null
   */
  @Nullable
  public static HTTPServer getHttpServer() {
    return manager.backend.httpServer;
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
    return formatTime(time, format, java.time.ZoneId.systemDefault());
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
        .ofInstant(java.time.Instant.ofEpochMilli(t), timezone)
        .format(java.time.format.DateTimeFormatter.ofPattern(f));
  }

  /**
   * 获取当前服务器的TPS数值，精确到2位小数。
   * 
   * @return double[5] 数组，索引对应：
   *         [0] = 最近1秒的TPS，这个不可能有小数部分
   *         [1] = 最近1分钟的平均TPS
   *         [2] = 最近5分钟的平均TPS
   *         [3] = 最近15分钟的平均TPS
   *         [4] = 时间戳
   *         如果发生非致命错误则会返回-1.0
   * @author kdxiaoyi
   * @since 0.5.0
   */
  public static double[] getServerTPS() throws Exception {
    return manager.backend.getLiveTPS();
  }

  /**
   * 获取一个全局唯一的ID
   * @return
   * @since 0.4.4
  */
 @Internal
 public static Long getUniqueID() {
    return uniqueIDCounter.getAndIncrement();
  }
}