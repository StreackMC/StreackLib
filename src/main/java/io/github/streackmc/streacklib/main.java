package io.github.streackmc.StreackLib;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.streackmc.StreackLib.utils.HTTPServer;

public class main extends JavaPlugin {

  private HTTPServer httpServer;
  private FileConfiguration conf;

  @Override
  public void onEnable() {
    getLogger().info(
      "  ____     _                                     _        __  __     ____ \n" +
      " / ___|   | |_   _ __    ___      __ _    ___   | | __   |  \\/  |  / ___|\n" +
      " \\___ \\ | __| | '__|  / _ \\   / _` |  / __|  | |/ /   | |\\/| | | |    \n" +
      "  ___) |  | |_  | |    |  __/ | (_| | | (__  | <  | |    | |   | | ___ \n" +
      " |____/   \\__| |_|     \\___|  \\__,_|  \\___| |_|\\_\\ |_|   |_|  \\____|\n" +
      "                                                                   "
    );
    saveDefaultConfig();
    conf = getConfig();
    getLogger().info("初始化成功！正在启用组件。");
    EnableHTTPServer();
    getLogger().info("已启用StreackLib v" + getDescription().getVersion() + "");
  }
  @Override
  public void onDisable() {
    DisableHTTPServer();
  }

  /* HTTPServer */
  private void EnableHTTPServer() {
    boolean enabled = conf.getBoolean("http-server.enabled", false);
    String host = conf.getString("http-server.host", "0.0.0.0");
    int port = conf.getInt("http-server.port", 8080);
    
    getLogger().info("模块：HTTPServer " + host + ":" + port);
    if (enabled) {
      httpServer = new HTTPServer(host, port, this);
      httpServer.startServer();
      getLogger().info("HTTP 服务器已启动于 " + host + ":" + port);
    } else {
      httpServer = null;
      getLogger().info("HTTP 服务器未启用");
    }
  }
  private void DisableHTTPServer() {
    if (httpServer != null) {
      httpServer.stopServer();
      getLogger().info("HTTP 服务器已关闭");
    }
  }
  public HTTPServer getHttpServer() {
    return httpServer;
  }
}