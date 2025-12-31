package com.github.streackmc.StreackLib.utils;

import com.github.streackmc.StreackLib.libinit;
import fi.iki.elonen.NanoHTTPD;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 基于 NanoHTTPD 的简易转发服务器。
 * 其它插件可通过 registerHandler(String path, Handler h) 注册自己的子路由。
 * @author kdxiaoyi
 * @author KimiAI 亦有贡献
 */
public class HTTPServer extends NanoHTTPD {

  private final JavaPlugin plugin;
  private final Map<String, Handler> handlerMap = new ConcurrentHashMap<>();
  private String uri;
  public int MAX_URI = 2048;
  public long MAX_FILE_SIZE = 20L*1024*1024;

  /**
   * 初始化一个HTTPServer对象
   * @param hostname 监听地址
   * @param port 监听端口
   * @param plugin 发起的插件对象
   */
  public HTTPServer(String hostname, int port, JavaPlugin plugin) {
    super(hostname, port);
    this.uri = hostname + ":" + port;
    this.plugin = plugin;
    this.MAX_URI = libinit.conf.getInt("http-server.max-uri-length", 2048);
    this.MAX_FILE_SIZE = libinit.conf.getLong("http-server.max-file-size-kb", 20480L) * 1024;
  }

  /**
   * 启动当前HTTPServer.
   */
  public void startServer() {
    try {
      setAsyncRunner(new DefaultAsyncRunner() {
        private final ThreadPoolExecutor exec = new ThreadPoolExecutor(
            // default 8 | max 16 | keepalive 60s 
            8, 16, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
              Thread t = new Thread(r, "StreackLib.HTTPServer/NanoHTTPD-" + r.hashCode());
              t.setDaemon(true);
              t.setContextClassLoader(HTTPServer.class.getClassLoader());
              return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

        @Override
        public void exec(Runnable code) {
          exec.execute(code);
        }

        @Override
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
      return caller.getFileName() + "//" + caller.getClassName() + ":" + caller.getMethodName() + "@" + caller.getLineNumber();
    }
    return "StreackLib-InnerCall";
  }
  private String getServerFullName() {
    return " HTTPServer[" + uri + "] ";
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();
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
        plugin.getLogger().warning(getServerFullName() + "在处理 " + uri + " 上的事件时发生异常：事件处理器抛出错误：" + ex.getMessage());
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
            "500 Internal Server Error: " + ex.getMessage());
      }
    }
    // 没有请求处理器时
    if (libinit.conf.getBoolean("http-server.allow-file-transport", false)) {
      // 文件传递
      try {
        SFile.mkdir(libinit.pluginDataPath, "HTTPServer");
        File root = new File(libinit.pluginDataPath, "HTTPServer");
        File reach = new File(root, uri).getCanonicalFile();
        // 防止路径穿越
        if (!reach.getPath().startsWith(root.getCanonicalPath())) {
          return newFixedLengthResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "403 Forbidden");
        }
        if (reach.exists() && reach.isFile()) {
          // 判断文件是否合法
          return newFixedLengthResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(reach), reach.length());
        } else {
          // 文件不存在
          return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
        }
      } catch (IOException e) {
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "500 Internal Server Error: File is unreachable.");
      }
    } else { // 文件传输未启用
      return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
    }
  }

  /** 函数式接口，方便 Lambda 注册 */
  @FunctionalInterface
  public interface Handler {
    Response handle(NanoHTTPD.IHTTPSession session) throws Exception;
  }
}
