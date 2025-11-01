package io.github.streackmc.StreackLib.utils;

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

  public HTTPServer(String hostname, int port, JavaPlugin plugin) {
    super(hostname, port);
    this.plugin = plugin;
  }

  public void startServer() {
    try {
      start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
    } catch (IOException e) {
      plugin.getLogger().severe("无法启动 HTTP 服务器: " + e.getMessage());
    }
  }

  public void stopServer() {
    if (isAlive()) {
      stop();
    }
  }

  /** 供外部插件注册子路由 */
  public void registerHandler(String path, Handler handler) {
    handlerMap.put(path, handler);
    plugin.getLogger().info("已注册 HTTP 处理器: " + path);
  }

  @Override
  public Response serve(IHTTPSession session) {
    String uri = session.getUri();   // 例如 /api/demo
    Handler h = handlerMap.get(uri);
    if (h != null) {
      try {
        return h.handle(session);
      } catch (Exception ex) {
        plugin.getLogger().warning("处理器异常 (" + uri + "): " + ex.getMessage());
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Internal Server Error");
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
