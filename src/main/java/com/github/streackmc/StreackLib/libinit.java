package com.github.streackmc.StreackLib;

import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.github.streackmc.StreackLib.utils.SConfig;

import java.io.*;
import java.nio.file.Files;

import org.bukkit.plugin.java.JavaPlugin;

public class libinit extends JavaPlugin {
  private final int CONFIG_VERSION = 1;
  public static HTTPServer httpServer;
  public static SConfig conf;
  public static File pluginDataPath;

  @Override
  public void onEnable() {
    getLogger().info(
      "\n" +
      "  ____     _                                     _        __  __     ____ \n" +
      " / ___|   | |_   _ __    ___      __ _    ___   | | __   |  \\/  |  / ___|\n" +
      " \\___ \\ | __| | '__|  / _ \\   / _` |  / __|  | |/ /   | |\\/| | | |    \n" +
      "  ___) |  | |_  | |    |  __/ | (_| | | (__  | <  | |    | |   | | ___ \n" +
      " |____/   \\__| |_|     \\___|  \\__,_|  \\___| |_|\\_\\ |_|   |_|  \\____|\n" +
      "                                                                   "
    );
    saveDefaultConfig();
    pluginDataPath = this.getDataFolder();
    conf = new SConfig(new File(pluginDataPath, "config.yml"), "YAML");
    CheckConfigUpdate();
    LoadConf();
    getLogger().info("初始化成功！正在启用组件。");
    EnableHTTPServer();
    getLogger().info("已启用StreackLib v" + getDescription().getVersion() + "");
  }
  @Override
  public void onDisable() {
    DisableHTTPServer();
  }

  /* 载入配置 */
  private void LoadConf() {
  }

  /* 检查配置文件更新 */
  private void CheckConfigUpdate() {
    if (conf.getInt("version", 0) < 1) {
      getLogger().warning("注意：你的配置文件版本过低。参阅config.new.yml修改你的配置文件。");
      try(
        InputStream is = this.getResource("config.yml");
        OutputStream os = Files.newOutputStream(new File(pluginDataPath, "config.new.yml").toPath());
      ) {
          byte[] buffer = new byte[1024];
          int length;
          while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
          }
          os.close();
      } catch (Exception e) {
        getLogger().severe("配置文件更新失败：" + e.getMessage());
      }
    }
  }

  /* HTTPServer */
  private void EnableHTTPServer() {
    boolean enabled = conf.getBoolean("http-server.enabled", false);
    String host = conf.getString("http-server.host", "0.0.0.0");
    int port = conf.getInt("http-server.port", 8080);
    getLogger().info("处理模块：HTTPServer");
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
    }
  }
}
