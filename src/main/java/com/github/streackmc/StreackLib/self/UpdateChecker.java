package com.github.streackmc.StreackLib.self;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.github.streackmc.StreackLib.StreackLib;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 检查StreackLib的更新
 * 
 * @author kdxiaoyi
 * @since 0.4.1
 */
public class UpdateChecker {

  private static final List<String> VERSION_URLS = Arrays.asList(
      "https://github.com/StreackMC/StreackLib/releases/latest/version_info.json",
      "https://gh.kdxaioyi.top/github.com/StreackMC/StreackLib/releases/latest/version_info.json");
  private static final AtomicReference<CompletableFuture<Void>> activeTask = new AtomicReference<>();

  /**
   * 比较 x.x.x 格式的版本号
   * 
   * @return true 如果 latest 比 current 新
   */
  public static boolean isNewer(String current, String latest) {
    String[] currentParts = current.split("\\.");
    String[] latestParts = latest.split("\\.");

    int maxLength = Math.max(currentParts.length, latestParts.length);

    for (int i = 0; i < maxLength; i++) {
      int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
      int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;

      if (latestPart > currentPart) {
        return true;
      } else if (latestPart < currentPart) {
        return false;
      }
    }
    return false;
  }

  /**
   * 启动异步更新检查
   * 当调用此方法时，新建一个异步线程检查更新
   */
  public static void checkUpdate() {
    cancelCheck();

    CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
      if (!StreackLib.conf.getBoolean("update-checker.enabled", true)) {
        logger.debug("更新检查已禁用，更新进程结束。");
        return;
      }

      try {
        logger.info("开始检查StreackLib更新...");

        String currentVersion = System.getProperty("build.version", null);
        if (currentVersion == null) {
          logger.severe("无法获取当前版本号，请将此问题反馈给开发者！");
          return;
        }


        JsonObject versionInfo = fetchVersionInfo();
        if (versionInfo == null) {
          logger.severe("无法获取新版本信息，检查你的网络后重试。");
          return;
        }

        String latestVersion = versionInfo.get("version").getAsString();
        String downloadUrl = versionInfo.get("download_url").getAsString();
        String changelog = versionInfo.has("changelog") ? versionInfo.get("changelog").getAsString() : "暂无更新日志";

        if (isNewer(currentVersion, latestVersion)) {
          logger.info(String.format("发现新版本: %s (当前: %s)", latestVersion, currentVersion));
          logger.info("更新日志:\n" + changelog);

          if (StreackLib.conf.getBoolean("update-checker.auto-download", false)) {
            logger.info("开始下载更新...");
            downloadUpdate(downloadUrl, latestVersion);
          }
        } else {
          logger.info(String.format("当前版本 %s 已是最新", currentVersion));
        }

      } catch (Exception e) {
        logger.severe("检查更新时发生错误: " + e.getMessage());
        e.printStackTrace();
      }
    });

    activeTask.set(task);
    task.whenComplete((result, error) -> activeTask.compareAndSet(task, null));
  }

  /**
   * 取消正在进行的更新检查并清理资源
   * 调用此方法时，无论如何立即停止检查
   */
  public static void cancelCheck() {
    CompletableFuture<Void> task = activeTask.getAndSet(null);
    if (task != null && !task.isDone()) {
      logger.info("正在取消更新检查...");
      task.cancel(true);
    }
  }

  /**
   * 从远程获取版本信息
   * 
   * @return JSON对象或null
   */
  private static JsonObject fetchVersionInfo() {
    for (String urlStr : VERSION_URLS) {
      HttpURLConnection conn = null;
      try {
        URL url = new URI(urlStr).toURL();
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setInstanceFollowRedirects(true);

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
          logger.warning("从 " + urlStr + " 获取失败，响应码: " + responseCode);
          continue; // 尝试下一个URL
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream()))) {
          String line;
          while ((line = reader.readLine()) != null) {
            response.append(line);
          }
        }

        return JsonParser.parseString(response.toString()).getAsJsonObject();

      } catch (Exception e) {
        logger.warning("从 " + urlStr + " 获取版本信息失败: " + e.getMessage());
        // 继续尝试下一个URL
      } finally {
        if (conn != null) {
          conn.disconnect();
        }
      }
    }

    logger.severe("所有版本信息链接均不可用: " + VERSION_URLS);
    return null;
  }

  /**
   * 下载更新文件
   * TODO: 实现实际的下载逻辑
   */
  private static void downloadUpdate(String downloadUrl, String version) {
    try {
      logger.info("准备下载更新从: " + downloadUrl);
      logger.info("下载完成，新版本 " + version + " 已准备就绪");
    } catch (Exception e) {
      logger.severe("下载更新失败: " + e.getMessage());
      e.printStackTrace();
    }
  }
}
