package com.github.streackmc.StreackLib.utils;

import fi.iki.elonen.NanoHTTPD;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 NanoHTTPD 的简易转发服务器。
 * 其它插件可通过 registerHandler(String path, Handler h) 注册自己的子路由。
 */
public class HTTPServer extends NanoHTTPD {

  private final JavaPlugin plugin;
  private final Map<String, Handler> handlerMap = new ConcurrentHashMap<>();
  private String uri;

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
  }

  /**
   * 启动当前HTTPServer.
   */
  public void startServer() {
    try {
      start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    } catch (IOException e) {
      plugin.getLogger().severe("无法启动HTTPServer [" + uri + "]: " + e.getMessage());
    }
  }

  /**
   * 停止当前HTTPServer.
   */
  public void stopServer() {
    if (isAlive()) {
      stop();
      plugin.getLogger().info("已停止HTTP Server [" + uri + "].");
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
   */
  public void registerHandler(String path, Handler handler) {
    handlerMap.put(path, handler);
    plugin.getLogger().info("向HTTP Server [" + uri + "]注册HTTP事件于 " + path);
  }

  /**
   * 移除一个事件处理器
   * @param path 目标Path
   */
  public void removeHandler(String path) {
    handlerMap.remove(path);
    plugin.getLogger().info("向取消注册HTTP事件于 " + path);
  }

  private String getCaller() {
    StackTraceElement[] st = Thread.currentThread().getStackTrace();
    StackTraceElement caller;
    if (st.length >= 3) {
      caller = st[2];
      return caller.getFileName() + "//" + caller.getClassName() + ":" + caller.getMethodName() + "@" + caller.getLineNumber();
    }
    return "";
  }
  private String getServerFullName() {
    return "HTTPServer [" + uri + "]";
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();
    Handler h = handlerMap.get(uri);
    if (h != null) {
      try {
        return h.handle(session);
      } catch (Exception ex) {
        plugin.getLogger().warning("HTTP Server [" + uri + "]的处理器异常 (" + uri + "): " + ex.getMessage());
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
            "Internal Server Error: " + ex.getMessage());
      }
    }
    // 默认 404
    return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
  }

  /** 函数式接口，方便 Lambda 注册 */
  @FunctionalInterface
  public interface Handler {
    Response handle(NanoHTTPD.IHTTPSession session) throws Exception;
  }
}
