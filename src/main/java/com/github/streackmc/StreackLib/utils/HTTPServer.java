package com.github.streackmc.StreackLib.utils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.self.manager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import fi.iki.elonen.NanoHTTPD;

/**
 * 基于 NanoHTTPD 的简易转发服务器。
 * 其它插件可通过 registerHandler(String path, Handler h) 注册自己的子路由。
 * 
 * @author kdxiaoyi
 * @author KimiAI 亦有贡献
 * @since 0.1.0
 */
public class HTTPServer extends NanoHTTPD {

  private final Map<String, Handler> handlerMap = new ConcurrentHashMap<>();
  private String listenAddress;
  public int MAX_URI = 2048;
  public long MAX_FILE_SIZE = 20L/* MB */ * 1024 * 1024;

  public final Long INSTANCE_ID = StreackLib.getUniqueID();
  public final static class EVENTS {
    /**
     * HTTP服务器启动
     * 
     * @param address String | 该服务器的监听地址
     */
    public static final String STARTED = "streacklib.httpserver:started";
    /**
     * HTTP服务器停止
     * 
     * @param address String | 该服务器的监听地址
     */
    public static final String STOPPED = "streacklib.httpserver:stopped";
    /**
     * 接受到请求
     * <p>
     * 使用该方法无法对请求做出回应。
     * 
     * @param address String | 该服务器的监听地址
     * @param uri     String | 请求路径
     * @param origin  String | 请求来源，未经校验，可能因代理等误判
     * @param method  String | 请求方法
     * @see HTTPServer#registerHandler(String, Handler)
     */
    public static final String ON_REQUEST = "streacklib.httpserver:on_request";
  }

  /**
   * 初始化一个HTTPServer对象
   * 
   * @param hostname 监听地址
   * @param port     监听端口
   */
  public HTTPServer(String hostname, int port) {
    super(hostname, port);
    this.listenAddress = hostname + ":" + port;
    this.MAX_URI = StreackLib.ENV.conf.getInt("http-server.max-uri-length", 2048);
    this.MAX_FILE_SIZE = StreackLib.ENV.conf.getLong("http-server.max-file-size-kb", 20480L) * 1024;
  }

  /**
   * 启动当前HTTPServer.
   */
  public void startServer() {
    if (isAlive()) {
      return;
    }
    try {
      setAsyncRunner(new DefaultAsyncRunner() {
        private final ThreadPoolExecutor exec = new ThreadPoolExecutor(
            // default 8 | max 16 | keepalive 60s
            8, 16, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadFactoryBuilder()
                .setNameFormat("StreackLib.HTTPServer/Worker-%d")
                .setDaemon(true)
                .build(),
            new ThreadPoolExecutor.AbortPolicy());

        public void exec(Runnable code) {
          exec.execute(code);
        }

        public void close() {
          exec.shutdownNow();
        }
      });
      start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
      logger.info("已启动" + getServerFullName());
      SEventCentral.broadcastEvent(EVENTS.STARTED, INSTANCE_ID)
          .set("address", this.listenAddress)
          .broadcast();
    } catch (IOException e) {
      logger.severe("无法启动" + getServerFullName() + "：" + e.getLocalizedMessage());
      e.printStackTrace();
    }
  }

  /**
   * 停止当前HTTPServer.
   */
  public void stopServer() {
    if (isAlive()) {
      stop();
      logger.info("已停止" + getServerFullName() + ".\nfrom " + manager.getCaller(null).get(0));
      SEventCentral.broadcastEvent(EVENTS.STOPPED, INSTANCE_ID)
          .set("address", this.listenAddress)
          .broadcast();
    }
  }

  /**
   * 判断当前HTTPServer是否正在运行
   * 
   * @return 正在运行时返回True
   */
  public boolean isStarted() {
    return isAlive();
  }

  /**
   * 注册一个事件处理器，当指定的Path有请求传入时自动Call Handler
   * 
   * @param path    监听的路径
   * @param handler 处理器
   * @throws Exception 如果路径已被注册则抛出此错误
   */
  public void registerHandler(String path, Handler handler) throws Exception {
    if (handlerMap.containsKey(path)) {
      logger.warning(getServerFullName() + "无法注册在 " + path + "上的事件处理器：该路径已被占用\nfrom " + manager.getCaller(null).get(0));
      throw new Exception("在" + path + "上的事件处理器已被注册");
    } else {
      handlerMap.put(path, handler);
      logger.info(getServerFullName() + "注册在 " + path + "上的事件处理器.\nfrom " + manager.getCaller(null).get(0));
    }
  }

  /**
   * 移除一个事件处理器
   * 
   * @param path 目标Path
   */
  public void removeHandler(String path) {
    if (path == null) {
      logger.warning(getServerFullName() + "未能取消注册事件处理器，因为目标地址是 null .\nfrom " + manager.getCaller(null).get(0));
    }
    if (handlerMap.remove(path) != null) {
      logger.info(getServerFullName() + "取消注册在 " + path + "上的事件处理器.\nfrom " + manager.getCaller(null).get(0));
    } else {
      logger.warning(getServerFullName() + "未能取消注册在 " + path + "上的事件处理器：该路径未被注册.\nfrom " + manager.getCaller(null).get(0));
    }
  }

  /**
   * 获取服务器全名
   * @return 
   * @since 0.4.4
   */
  public String getServerFullName() {
    return " HTTPServer[" + listenAddress + "] ";
  }

  private static List<Map<String, Object>> banListCache;
  private static long banListLastModified;

  /**
   * 返回一个IP被封禁的原因。
   * 如果未被封禁返回 null 。
   * 
   * @param ip 目标IP，注意不会校验格式
   * @throws IllegalArgumentException 传入IP不合法
   * @return 封禁的原因
   * @deprecated 自 0.5.0 弃用，仅存档，已有新基于平台API的实现。
   */
  @Nullable
  @Deprecated
  public static String detailBannedIp(@NotNull String ip) throws IllegalArgumentException {
    Objects.requireNonNull(ip, "传入了一个 null");

    Path banIpList = StreackLib.ENV.dataPath.toPath().resolve("../../ban-ips.json");
    File file = banIpList.toFile();
    if (!file.exists()) {
      return null;
    }

    long lastMod = file.lastModified();/* 因为SConfig还不支持根数组，需要自行实现 */
    if (banListCache == null || lastMod != banListLastModified) {
      // 重新加载
      try (Reader reader = Files.newBufferedReader(banIpList, StandardCharsets.UTF_8)) {
        Type listType = new TypeToken<List<Map<String, Object>>>() {
        }.getType();
        List<Map<String, Object>> list = new Gson().fromJson(reader, listType);
        banListCache = list != null ? list : Collections.emptyList();
        banListLastModified = lastMod;
      } catch (IOException e) {
        // 读取失败，清空缓存并返回 null（或记录日志）
        banListCache = Collections.emptyList();
        banListLastModified = 0L;
        return null;
      }
    }

    for (Map<String, Object> record : banListCache) {
      Object cachedIp = record.get("ip");
      if (ip.equals(cachedIp)) {
        Object reason = record.get("reason");
        return reason != null ? reason.toString() : null;
      }
    }
    return null;
  }

  @Override
  public Response serve(IHTTPSession session) {
    // 准备请求信息
    String id = StreackLib.getUniqueID(/* 获取全局唯一ID */).toString();
    String ip = session.getRemoteIpAddress();
    String uri = session.getUri()
        .replaceAll("\\.\\./", "")
        .replaceAll("[\\p{Cntrl}&&[^\r\n]]+", "")
        .replaceAll("[\r\n]+", " ");
    NanoHTTPD.Method method = session.getMethod();

    // 处理IP封禁，共享游戏内封禁
    if (StreackLib.ENV.conf.getBoolean("http-server.banip.sync-game", true)) {//TODO: 独立黑名单处理，添加内网穿透真实IP获取，添加内联黑名单处理
      SConfig potentialBanEntry = manager.backend.checkBan(ip);// 应该不会有人执行 /ban 127.0.0.1
      long expireTime = potentialBanEntry.getLong("expire", 0L);
      if (potentialBanEntry.getBoolean("banned", false)
          && (expireTime >= System.currentTimeMillis() || expireTime < 0L)) {
        logger.info(
            getServerFullName() + String.format("拒绝了 %s 的连接：[ %s ]", ip, potentialBanEntry.getString("reason", "")));
        if (StreackLib.ENV.conf.getBoolean("http-server.use-404-as-403", false)) {
          // 用户要求使用404代替403
          return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
        } else {
          // 拼接返回值
          String reasonStr = MCColor.toHtml(potentialBanEntry.getString("reason", ""));
          reasonStr = (reasonStr.isBlank()) ? "Your" : "Because [" + reasonStr + "], your";

          String expireStr = ((expireTime < 0) ? "until forever."
              : "until " + StreackLib.formatTime(expireTime, "YYYY-MM-DD hh:mm:ss") + ".");
          return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_HTML,
              String.format(
                  "403 Forbidden: %s IP has been banned from this server since %s , %s",
                  reasonStr,
                  StreackLib.formatTime(potentialBanEntry.getLong("create", 0L), "YYYY-MM-DD hh:mm:ss"),
                  expireStr));
        }
      }
    }

    // 广播事件并日志
    logger.info(
        getServerFullName() + "收到请求#" + id + "\n"
            + " 来源 = [未校验]" + ip + "\n"
            + " 路径 = " + uri + "\n"
            + " 方法 = " + method.toString());
    SEventCentral.broadcastEvent(EVENTS.ON_REQUEST, INSTANCE_ID)
        .set("address", this.listenAddress)
        .set("uri", uri)
        .set("origin", ip)
        .set("method", method.toString())
        .broadcast();

    // 不处理过长uri
    if (uri.length() > MAX_URI) {
      logger.warn(getServerFullName() + "请求#" + id + " 的URI过长，已拒绝。");
      return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "414 Request-URI Too Long");
    }

    // 有请求处理器时
    Handler h = handlerMap.get(uri);
    if (h != null) {
      try {
        logger.debug(getServerFullName() + "请求#" + id + " 命中已注册的处理器。");
        return h.handle(session);
      } catch (Exception ex) {
        ex.printStackTrace();
        logger.severe(getServerFullName() + "请求#" + id + " 上的事件时发生异常：事件处理器抛出错误：" + ex.getLocalizedMessage());
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
            "500 Internal Server Error");
      }
    }

    // 没有请求处理器时
    if (!StreackLib.ENV.conf.getBoolean("http-server.allow-file-transport", false)) {
      // 文件传输未启用
      logger.debug(getServerFullName() + "请求#" + id + " 没有命中已注册的处理器，且文件传输已禁用。");
      return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
    }

    // 文件传递
    try {
      SFile.mkdir(StreackLib.ENV.dataPath, "HTTPServer");
      File root = new File(StreackLib.ENV.dataPath, "HTTPServer");
      File reach = new File(root, uri).getCanonicalFile();
      logger.debug(getServerFullName() + "请求#" + id + " 正在获取文件 " + reach.getAbsolutePath());

      // 防止路径穿越
      if (!reach.getPath().startsWith(root.getCanonicalPath())) {
        logger.warning(getServerFullName() + "请求#" + id + " 试图调用非法路径，已被拦截。");
        return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "403 Forbidden");
      }

      if (reach.exists() && reach.isFile()) {
        // 判断文件是否可访问
        int size = 0;
        String mime = "application/octet-stream";
        try {
          size = (int) reach.length();
          try {
            mime = SFile.getMIME(reach);
          } catch (Exception ignore) {
            mime = "application/octet-stream";
          }
          logger.debug(getServerFullName() + "请求#" + id + " 获取的文件信息：\n"
              + " 大小   = " + size + "Bytes\n"
              + " MIME  = " + mime);
        } catch (Exception e) {
          logger.severe(getServerFullName() + "请求#" + id + " 请求的文件无法获取：" + e.getLocalizedMessage());
          e.printStackTrace();
          return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
              "500 Internal Server Error");
        }

        // 文件体积限制
        if (size > MAX_FILE_SIZE) {
          logger.warning(
              getServerFullName() + "请求#" + id + " 请求的文件体积超出了限制：应小于等于 " + MAX_FILE_SIZE + " 字节，实为 " + size + " 字节。");
          return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT,
              "413 Payload Too Large");
        }

        // 返回文件
        FileChannel fileChannel = FileChannel.open(reach.toPath(), StandardOpenOption.READ);
        logger.debug(getServerFullName() + "请求#" + id + " 开始传输文件 @ " + fileChannel.toString());
        return newChunkedResponse(Response.Status.OK, mime, Channels.newInputStream(fileChannel));
        // return newFixedLengthResponse(Response.Status.OK, "application/octet-stream",
        // new FileInputStream(reach), reach.length());
      } else {
        // 文件不存在
        logger.debug(getServerFullName() + "请求#" + id + " 请求的文件不存在。");
        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
      }
    } catch (IOException e) {
      logger.severe(getServerFullName() + "请求#" + id + " 的文件传输发生异常：" + e.getLocalizedMessage());
      e.printStackTrace();
      return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
          "500 Internal Server Error");
    }
  }

  /** 函数式接口，方便 Lambda 注册 */
  @FunctionalInterface
  public interface Handler {
    Response handle(NanoHTTPD.IHTTPSession session) throws Exception;
  }
}
