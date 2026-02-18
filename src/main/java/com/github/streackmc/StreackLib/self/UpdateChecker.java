package com.github.streackmc.StreackLib.self;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.util.InternalApi;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.utils.SFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 检查StreackLib的更新
 * 
 * @author kdxiaoyi
 * @since 0.4.1
 */
@InternalApi
public class UpdateChecker {

  private static final List<String> VERSION_URLS = Arrays.asList(//TODO:目前还没有稳定的API方法
      "https://raw.githubusercontent.com/StreackMC/StreackLib/refs/heads/version/version_info.json",
      "https://gh.kdxiaoyi.top/raw.githubusercontent.com/StreackMC/StreackLib/refs/heads/version/version_info.json");
  private static final AtomicReference<CompletableFuture<Void>> activeTask = new AtomicReference<>();
  private static final String USER_AGENT = "StreackLib-UpdateChecker/0.4.1";

  /**
   * 比较 x.x.x…… 格式的版本号
   *
   * @return true 当且仅当 latest 比 current 新（严格大于）
   * @throws IllegalArgumentException 如果任一字符串为空、null 或含有非数字段
   */
  public static boolean isNewer(String current, String latest) {
    if (current == null || latest == null) {
      throw new IllegalArgumentException("版本号不能为 null");
    }
    if (current.isEmpty() || latest.isEmpty()) {
      throw new IllegalArgumentException("版本号不能为空串");
    }

    String[] currParts = current.split("\\.");
    String[] lateParts = latest.split("\\.");

    int len = Math.max(currParts.length, lateParts.length);
    for (int i = 0; i < len; i++) {
      int c = i < currParts.length ? parseSegment(currParts[i], true) : 0;
      int l = i < lateParts.length ? parseSegment(lateParts[i], true) : 0;

      if (l > c)
        return true;
      if (l < c)
        return false;
    }
    return false; // 完全相等
  }

  /**
   * 将目标文本转为整数
   * 
   * @param seg            原文本
   * @param allowFrontZero 是否允许前导零
   * @throws IllegalArgumentException
   * @return
   */
  private static int parseSegment(String seg, boolean allowFrontZero) {
    if (!seg.matches("\\d+")) {
      throw new IllegalArgumentException("非法字符段: " + seg);
    }
    // 禁止前导零的“超长”段（如 00123456789）防止歧义
    if (seg.length() > 1 && seg.startsWith("0") && !allowFrontZero) {
      throw new IllegalArgumentException("不允许前导零: " + seg);
    }
    return Integer.parseInt(seg);
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

        String currentVersion = manager.getBuildVersion();

        if (currentVersion == null) {
          throw new Exception("无法获取当前正在运行的版本");
        }

        JsonObject versionInfo = fetchVersionInfo();
        if (versionInfo == null) {
          throw new Exception("无法获取新版本信息，检查你的网络后重试。");
        }

        String latestVersion = versionInfo.get("version").getAsString();
        JsonArray downloadUrl = versionInfo.get("download_url").getAsJsonArray();
        String changelog = versionInfo.has("changelog") ? versionInfo.get("changelog").getAsString() : "暂无更新日志";

        if (isNewer(currentVersion, latestVersion)) {
          logger.info(String.format("发现新版本: %s (当前: %s)", latestVersion, currentVersion));
          logger.info("更新日志:\n" + changelog);

          if (StreackLib.conf.getBoolean("update-checker.auto-download", false)) {
            downloadUpdate(downloadUrl, latestVersion);
          }
        } else {
          logger.info(String.format("当前版本 %s 已是最新", currentVersion));
        }

      } catch (Exception e) {
        logger.severe("检查更新时发生错误: " + e.getLocalizedMessage());
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
        conn.setRequestProperty("User-Agent", USER_AGENT);

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
        logger.warning("从 " + urlStr + " 获取版本信息失败: " + e.getLocalizedMessage());
        e.printStackTrace();
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
   */
  private static void downloadUpdate(JsonArray dlUrls, String version) {

    try {
      logger.info("开始下载更新...");
      logger.debug("更新链接：" + dlUrls.toString());

      // 准备目标文件夹
      Path dataPath = StreackLib.dataPath.toPath(); // mcserver/plugins/StreackLib/
      Path pluginsFolder = dataPath.getParent(); // mcserver/plugins/
      Path updateFolder = pluginsFolder.resolve("update"); // mcserver/plugins/update/

      // 确保 update 文件夹存在（会创建所有不存在的父目录）
      Files.createDirectories(updateFolder);

      // 逐个尝试
      final AtomicBoolean isDone = new AtomicBoolean(false);
      dlUrls.forEach((dlUrlOrigin) -> {
        if (isDone.get()) return;
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        OutputStream outputStream = null;
        String downloadUrl = "";
        try {
          if (dlUrlOrigin.isJsonNull()) {
            throw new IllegalArgumentException(String.format("无效的JSON描述信息： %s", dlUrlOrigin.toString()));
          }
          downloadUrl = dlUrlOrigin.getAsString();
          File targetFile = SFile.wget(downloadUrl, updateFolder);
          logger.info("下载完成，新版本 " + version + " 已准备就绪。文件已保存到:" + targetFile.toPath().toString());
        } catch (Exception e) {
          logger.severe("从" + downloadUrl +"下载更新失败: " + e.getLocalizedMessage());
          e.printStackTrace();
        } finally {// 确保资源被关闭
          try {
            if (inputStream != null)
              inputStream.close();
            if (outputStream != null)
              outputStream.close();
            if (conn != null)
              conn.disconnect();
          } catch (IOException e) {
            logger.warning("关闭资源时出错: " + e.getMessage());
            e.printStackTrace();
          }
        }
      });
    } catch (Exception e) {
      logger.severe("下载更新失败: " + e.getLocalizedMessage());
      e.printStackTrace();
    }
  }
}