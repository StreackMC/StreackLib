package com.github.streackmc.StreackLib.self.backend;

import java.util.ArrayDeque;
import java.util.Deque;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
public class StreackLibBukkitBackend extends StreackLibDefaultBackend {
  public StreackLibBukkitBackend() {
    super();
  }

  // 独有变量
  public BukkitRunnable UpdateCheckTask;
  public BukkitRunnable UpdateTpsTask;

  // ===================================================
  // TPS
  // ===================================================
  private final Deque<Long> tickTimes = new ArrayDeque<>();
  private volatile double liveTps = -1.0;

  public void onTickDoing() {
    long now = System.currentTimeMillis();
    tickTimes.addLast(now);
    // 移除 1 秒前的记录
    while (!tickTimes.isEmpty()
        // 处理1秒前的过期记录，含右边界不含左边界
        && now - tickTimes.peekFirst() >= 1000) {
      tickTimes.pollFirst();
    }
    liveTps = tickTimes.size();
  }

  @Override
  public double[] getLiveTPS() throws Exception {
    double[] tps = new double[5];
    tps[4] = System.currentTimeMillis();
    try {
      // 获取1m/5m/15m TPS
      double[] paperTps = (double[]) Bukkit.getTPS();
      tps[1] = Math.round(paperTps[0] * 100.0 / 100.0);
      tps[2] = Math.round(paperTps[1] * 100.0 / 100.0);
      tps[3] = Math.round(paperTps[2] * 100.0 / 100.0);
      // 获取1s TPS
      tps[0] = liveTps;
      return tps;
    } catch (Exception e) {
      throw new Exception("获取TPS时发生未知错误：" + e.getLocalizedMessage(), e);
    }
  }
}
