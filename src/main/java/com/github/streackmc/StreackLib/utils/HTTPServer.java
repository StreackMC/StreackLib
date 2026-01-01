package com.github.streackmc.StreackLib.utils;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.libinit;
import com.github.streackmc.StreackLib.self.logger;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import fi.iki.elonen.NanoHTTPD;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.Channels;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;

/**
 * 基于 NanoHTTPD 的简易转发服务器。
 * 其它插件可通过 registerHandler(String path, Handler h) 注册自己的子路由。
 * 
 * @author kdxiaoyi
 * @author KimiAI 亦有贡献
 * @since 0.1.0
 */
public class HTTPServer extends NanoHTTPD {

  private final JavaPlugin plugin;
  private final Map<String, Handler> handlerMap = new ConcurrentHashMap<>();
  private String listenAddress;
  public int MAX_URI = 2048;
  public long MAX_FILE_SIZE = 20L/* MB */ * 1024 * 1024;

  /**
   * 初始化一个HTTPServer对象
   * 
   * @param hostname 监听地址
   * @param port     监听端口
   * @param plugin   发起的插件对象
   */
  public HTTPServer(String hostname, int port, JavaPlugin plugin) {
    super(hostname, port);
    this.listenAddress = hostname + ":" + port;
    this.plugin = plugin;
    this.MAX_URI = StreackLib.conf.getInt("http-server.max-uri-length", 2048);
    this.MAX_FILE_SIZE = StreackLib.conf.getLong("http-server.max-file-size-kb", 20480L) * 1024;
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
    } catch (IOException e) {
      logger.severe("无法启动" + getServerFullName() + "：" + e.getMessage());
    }
  }

  /**
   * 停止当前HTTPServer.
   */
  public void stopServer() {
    if (isAlive()) {
      stop();
      logger.info("已停止" + getServerFullName() + ".\nfrom " + getCaller());
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
      logger.warning(getServerFullName() + "无法注册在 " + path + "上的事件处理器：该路径已被占用\nfrom " + getCaller());
      throw new Exception("在" + path + "上的事件处理器已被注册");
    } else {
      handlerMap.put(path, handler);
      logger.info(getServerFullName() + "注册在 " + path + "上的事件处理器.\nfrom " + getCaller());
    }
  }

  /**
   * 移除一个事件处理器
   * 
   * @param path 目标Path
   */
  public void removeHandler(String path) {
    if (path == null) {
      logger.warning(getServerFullName() + "未能取消注册事件处理器，因为目标地址是 null .\nfrom " + getCaller());
    }
    if (handlerMap.remove(path) != null) {
      logger.info(getServerFullName() + "取消注册在 " + path + "上的事件处理器.\nfrom " + getCaller());
    } else {
      logger.warning(getServerFullName() + "未能取消注册在 " + path + "上的事件处理器：该路径未被注册.\nfrom " + getCaller());
    }
  }

  private static final Set<String> SKIP_CLASS = Set.of(
      HTTPServer.class.getName(),
      "java.lang.reflect.Method",
      "jdk.internal.reflect.NativeMethodAccessorImpl",
      "jdk.internal.reflect.DelegatingMethodAccessorImpl");

  /**
   * 返回第一个非 HTTPServer 且非反射的调用者
   * 格式：SimpleClassName:method@line
   */
  private String getCaller() {
    StackTraceElement[] st = Thread.currentThread().getStackTrace();
    for (int i = 2; i < st.length; i++) { // 0=getStackTrace, 1=getCaller
      String cls = st[i].getClassName();
      if (!SKIP_CLASS.contains(cls)) {
        return st[i].getClassName()
            + ":" + st[i].getMethodName()
            + "@" + st[i].getLineNumber();
      }
    }
    return "StreackLib-Self_Call";
  }

  private String getServerFullName() {
    return " HTTPServer[" + listenAddress + "] ";
  }

  @Override
  public Response serve(IHTTPSession session) {
    String id = System.currentTimeMillis() + "-" + new Random().nextInt(100000);
    String uri = session.getUri();
    uri = uri.replaceAll("\\.\\./", "")
        .replaceAll("[\\p{Cntrl}&&[^\r\n]]+", "")
        .replaceAll("[\r\n]+", " "); // 清洗URL
    logger.info(
        getServerFullName() + "收到请求#" + id + "\n"
            + " 来源 = [未校验]" + session.getRemoteIpAddress() + "\n"
            + " 路径 = " + uri + "\n"
            + " 方法 = " + session.getMethod());
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
        logger.severe(getServerFullName() + "请求#" + id + " 上的事件时发生异常：事件处理器抛出错误：" + ex.getMessage());
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "500 Internal Server Error");
      }
    }
    // 没有请求处理器时
    if (!StreackLib.conf.getBoolean("http-server.allow-file-transport", false)) {
      // 文件传输未启用
      logger.debug(getServerFullName() + "请求#" + id + " 没有命中已注册的处理器，且文件传输已禁用。");
      return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
    }
    // 文件传递
    try {
      SFile.mkdir(libinit.pluginDataPath, "HTTPServer");
      File root = new File(libinit.pluginDataPath, "HTTPServer");
      File reach = new File(root, uri).getCanonicalFile();
      logger.debug(getServerFullName() + "请求#" + id + " 正在获取文件 " + reach.getAbsolutePath());
      // 防止路径穿越
      if (!reach.getPath().startsWith(root.getCanonicalPath())) {
        logger.warning(getServerFullName() + "请求#" + id + " 试图调用非法路径，已被拦截。");
        return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "403 Forbidden");
      }
      if (reach.exists() && reach.isFile()) {// 判断文件是否合法
        // 判断大小是否合法
        int size = 0;
        String mime = "application/octet-stream";
        try {
          size = (int) reach.length();
          try {
            mime = SFile.getMIME(reach);
          } catch (Exception ignored) {
            mime = "application/octet-stream";
          }
          logger.debug(getServerFullName() + "请求#" + id + " 获取的文件信息：\n"
          + " 大小   = " + size + "Bytes\n"
          + " MIME  = " + mime);
        } catch (Exception e) {
          logger.severe(getServerFullName() + "请求#" + id + " 请求的文件无法获取：" + e.getMessage());
          return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "500 Internal Server Error");
        }
        if (size > MAX_FILE_SIZE) {
          logger.warning(getServerFullName() + "请求#" + id + " 请求的文件体积超出了限制：应小于等于 " + MAX_FILE_SIZE + " 字节，实为 " + size + " 字节。");
          return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT,
            "413 Payload Too Large");
          }
          // 返回文件
          FileChannel fileChannel = FileChannel.open(reach.toPath(), StandardOpenOption.READ);
          logger.debug(getServerFullName() + "请求#" + id + " 开始传输文件 @ " + fileChannel.toString());
          return newChunkedResponse(Response.Status.OK, mime, Channels.newInputStream(fileChannel));
        //return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(reach), reach.length());
      } else {
        // 文件不存在
        logger.debug(getServerFullName() + "请求#" + id + " 请求的文件不存在。");
        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
      }
    } catch (IOException e) {
      logger.severe(getServerFullName() + "请求#" + id + " 的文件传输发生异常：" + e.getMessage());
      return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "500 Internal Server Error");
    }
  }

  /** 函数式接口，方便 Lambda 注册 */
  @FunctionalInterface
  public interface Handler {
    Response handle(NanoHTTPD.IHTTPSession session) throws Exception;
  }
}
