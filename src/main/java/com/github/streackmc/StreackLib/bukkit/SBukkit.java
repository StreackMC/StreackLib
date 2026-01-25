package com.github.streackmc.StreackLib.bukkit;

import org.bukkit.Bukkit;

import com.github.streackmc.StreackLib.StreackLib;

/**
 * 提供一些只能在Bukkit/Paper/Spigot平台上使用的方法。
 * 
 * @author kdxiaoyi
 * @author KimiAI
 * @since 0.4.3
 */
public class SBukkit {
  private SBukkit() {
  }
  
  /**
   * 获取当前服务器的TPS数值（Ticks Per Second，每秒刻数）
   * <p>
   * 通过反射调用 Paper服务端方法获取性能数据。
   * 
   * 你也可以自己使用StreackLib.currentTPS直接获取1s的TPS
   * 
   * 注TODO：此方法目前不支持Spigot
   * @return double[4] 数组，索引对应：
   *         [0] = 最近1秒的TPS
   *         [1] = 最近1分钟的平均TPS
   *         [2] = 最近5分钟的平均TPS
   *         [3] = 最近15分钟的平均TPS
   *         [4] = 时间戳
   *         如果发生非致命错误则会返回-1.0
   * @throws Exception 当服务器不支持TPS查询或反射调用失败时
   * @author KimiAI
   * @author kdxiaoyi 审计
   * @since 0.4.3
   */
  public static double[] getServerTPS() throws Exception {
    double[] tps = new double[5];
    tps[4] = System.currentTimeMillis();
    try {
      // 获取1m/5m/15m TPS
      double[] paperTps = (double[]) Bukkit.getTPS();
      tps[1] = paperTps[0];
      tps[2] = paperTps[1];
      tps[3] = paperTps[2];
      // 获取1s TPS
      tps[0] = StreackLib.currentTPS;
      return tps;
    } catch (Exception e) {
      throw new Exception("获取TPS时发生未知错误：" + e.getLocalizedMessage(), e);
    }
  }

}
