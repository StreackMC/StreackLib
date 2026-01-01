package com.github.streackmc.StreackLib.utils;

import com.github.streackmc.StreackLib.libinit;
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
 */
public class HTTPServer extends NanoHTTPD {

  private final JavaPlugin plugin;
  private final Map<String, Handler> handlerMap = new ConcurrentHashMap<>();
  private String listenAddress;
  public int MAX_URI = 2048;
  public long MAX_FILE_SIZE = 20L * 1024 * 1024;

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
    this.MAX_URI = libinit.conf.getInt("http-server.max-uri-length", 2048);
    this.MAX_FILE_SIZE = libinit.conf.getLong("http-server.max-file-size-kb", 20480L) * 1024;
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
      plugin.getLogger().info("已启动" + getServerFullName());
    } catch (IOException e) {
      plugin.getLogger().severe("无法启动" + getServerFullName() + "：" + e.getMessage());
    }
  }

  /**
   * 停止当前HTTPServer.
   */
  public void stopServer() {
    if (isAlive()) {
      stop();
      plugin.getLogger().info("已停止" + getServerFullName() + ".\nfrom " + getCaller());
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
      plugin.getLogger().warning(getServerFullName() + "无法注册在 " + path + "上的事件处理器：该路径已被占用\nfrom " + getCaller());
      throw new Exception("在" + path + "上的事件处理器已被注册");
    } else {
      handlerMap.put(path, handler);
      plugin.getLogger().info(getServerFullName() + "注册在 " + path + "上的事件处理器.\nfrom " + getCaller());
    }
  }

  /**
   * 移除一个事件处理器
   * 
   * @param path 目标Path
   */
  public void removeHandler(String path) {
    if (path == null) {
      plugin.getLogger().warning(getServerFullName() + "未能取消注册事件处理器，因为目标地址是 null .\nfrom " + getCaller());
    }
    if (handlerMap.remove(path) != null) {
      plugin.getLogger().info(getServerFullName() + "取消注册在 " + path + "上的事件处理器.\nfrom " + getCaller());
    } else {
      plugin.getLogger().warning(getServerFullName() + "未能取消注册在 " + path + "上的事件处理器：该路径未被注册.\nfrom " + getCaller());
    }
  }

  private String getCaller() {
    StackTraceElement[] st = Thread.currentThread().getStackTrace();
    StackTraceElement caller;
    if (st.length >= 3) {
      caller = st[2];
      return caller.getFileName() + "//" + caller.getClassName() + ":" + caller.getMethodName() + "@"
          + caller.getLineNumber();
    }
    return "StreackLib-InnerCall";
  }

  private String getServerFullName() {
    return " HTTPServer[" + listenAddress + "] ";
  }

  @Override
  public Response serve(IHTTPSession session) {
    int id = new Random().nextInt(100000);
    String uri = session.getUri();
    plugin.getLogger().info(
      getServerFullName()+ "收到请求#" + id + "\n"
      + " origin = " + session.getRemoteIpAddress() + "\n"
      + " target = " + uri + "\n"
      + " method =" + session.getMethod()
    );
    // 不处理过长uri
    if (uri.length() > MAX_URI) {
      return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "414 Request-URI Too Long");
    }
    // 有请求处理器时
    Handler h = handlerMap.get(uri);
    if (h != null) {
      try {
        return h.handle(session);
      } catch (Exception ex) {
        plugin.getLogger().severe(getServerFullName() + "请求#" + id + " 上的事件时发生异常：事件处理器抛出错误：" + ex.getMessage());
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
          "500 Internal Server Error: " + ex.getMessage());
        }
      }
      // 没有请求处理器时
      if (libinit.conf.getBoolean("http-server.allow-file-transport", false)) {
        // 文件传输未启用
        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
      }
    // 文件传递
    try {
      SFile.mkdir(libinit.pluginDataPath, "HTTPServer");
      File root = new File(libinit.pluginDataPath, "HTTPServer");
      File reach = new File(root, uri).getCanonicalFile();
      // 防止路径穿越
      if (!reach.getPath().startsWith(root.getCanonicalPath())) {
        plugin.getLogger().warning(getServerFullName() + "请求#" + id + " 试图调用非法路径，已被拦截。");
        return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "403 Forbidden");
      }
      if (reach.exists() && reach.isFile()) {// 判断文件是否合法
        // 判断大小是否合法
        int size = 0;
        String mime = "application/octet-stream";
        try {
          size = (int) reach.length();
          String fileName = reach.getName();
          int dotPos = fileName.lastIndexOf('.');
          if (dotPos > 0 && dotPos < fileName.length() - 1) {
            String ext = fileName.substring(dotPos + 1).toLowerCase(Locale.ROOT);
            mime = Optional.ofNullable(MIME_TYPES.get(ext)).orElse("application/octet-stream");
          } else {
            mime = "application/octet-stream";
          }
        } catch (Exception e) {
          plugin.getLogger().severe(getServerFullName() + "请求#" + id + " 请求的文件无法获取：" + e.getMessage());
          return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
              "500 Internal Server Error: " + e.getMessage());
        }
        if (size > MAX_FILE_SIZE) {
          plugin.getLogger().warning(getServerFullName() + "请求#" + id + " 请求的文件体积超出了限制：应小于等于 " + MAX_FILE_SIZE + " 字节，实为 " + size + " 字节。");
          return newFixedLengthResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT,
              "413 Payload Too Large");
        }
        // 返回文件
        FileChannel fileChannel = FileChannel.open(reach.toPath(), StandardOpenOption.READ);
        return newChunkedResponse(Response.Status.OK, mime, Channels.newInputStream(fileChannel));
        //return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(reach), reach.length());
      } else {
        // 文件不存在
        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
      }
    } catch (IOException e) {
      plugin.getLogger().severe(getServerFullName() + "请求#" + id + " 的文件传输发生异常：" + e.getMessage());
      return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
          "500 Internal Server Error: " + e.getMessage());
    }
  }

  /** 函数式接口，方便 Lambda 注册 */
  @FunctionalInterface
  public interface Handler {
    Response handle(NanoHTTPD.IHTTPSession session) throws Exception;
  }
}
