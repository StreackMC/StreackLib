package com.github.streackmc.StreackLib.self.backend;

import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.regex.Pattern;

import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.ApiStatus.Internal;

import com.github.streackmc.StreackLib.utils.SConfig;

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

  // ===================================================
  // BanList
  // ===================================================
  @Override
  public SConfig checkBan(String target) {
    SConfig data = getDefaultBanEntry(target);
    if (target == null || target.trim().isEmpty()) {
      return data;
    }
    // 1. 判断是否为 IP 地址 (IPv4 或 IPv6)
    if (isIpAddress(target)) {
      BanEntry banEntry = Bukkit.getServer().getBanList(BanList.Type.IP).getBanEntry(target);
      if (banEntry != null) {
        fillBanData(data, banEntry);
      }
      return data;
    }
    // 2. 处理玩家名或 UUID 传入
    String playerName = target;
    BanEntry banEntry = Bukkit.getServer().getBanList(BanList.Type.NAME).getBanEntry(playerName);
    if (banEntry != null) {
      fillBanData(data, banEntry);
    }
    return data;
  }

  /**
   * 将 BanEntry 中的信息填入 SConfig 对象
   */
  private void fillBanData(SConfig data, BanEntry banEntry) {
    data.putBoolean("banned", true);
    data.putString("reason", banEntry.getReason() == null ? "" : banEntry.getReason());
    data.putString("op", banEntry.getSource() == null ? "" : banEntry.getSource());

    Date created = banEntry.getCreated();
    data.putLong("create", created == null ? 0L : created.getTime());

    Date expires = banEntry.getExpiration();
    if (expires == null) {
      // 永久封禁：用 -1 表示
      data.putLong("expire", -1L);
    } else {
      data.putLong("expire", expires.getTime());
    }
  }

  /**
   * 判断字符串是否为有效的 IPv4 或 IPv6 地址
   * 使用正则快速匹配，避免阻塞式 DNS 查询
   */
  private boolean isIpAddress(String input) {
    return (Pattern.matches(ipv4Pattern, input) || Pattern.matches(ipv6Pattern, input));
  }
  static final String ipv4Pattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
  static final String ipv6Pattern = "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +
      "^([0-9a-fA-F]{1,4}:){1,7}:$|" +
      "^([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}$|" +
      "^([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}$|" +
      "^([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}$|" +
      "^([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}$|" +
      "^([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}$|" +
      "^[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})$|" +
      "^:((:[0-9a-fA-F]{1,4}){1,7}|:)$|" +
      "^fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}$|" +
      "^::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|"
      +
      "^([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
}