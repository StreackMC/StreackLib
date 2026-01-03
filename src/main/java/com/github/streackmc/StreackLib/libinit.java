package com.github.streackmc.StreackLib;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.github.streackmc.StreackLib.self.UpdateChecker;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.self.manager;
import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.github.streackmc.StreackLib.utils.SConfig;

public class libinit extends JavaPlugin {
  private Long CONFIG_VERSION = 0L;

  // 共享变量
  public static JavaPlugin pluginSelf;
  public static BukkitRunnable UpdateCheckTask;

  // 模块代表变量
  public static HTTPServer httpServer;

  @Override
  public void onEnable() {
    // 展示启动信息
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
    // 填充共享变量
    pluginSelf = this;
    StreackLib.dataPath = this.getDataFolder();
    StreackLib.conf = new SConfig(new File(StreackLib.dataPath, "config.yml"), "YAML");
    logger.plugin = this;
    // 读取构建信息
    try {
      StreackLib.buildConf = new SConfig(manager.getResourceAsFile("/plugin.yml"), "yml");
      StreackLib.defaultConf = new SConfig(manager.getResourceAsFile("/config.yml"), "yml");
      CONFIG_VERSION = StreackLib.defaultConf.getLong("version", -1L);
    } catch (Exception e) {
      logger.severe("未能获取构建信息：" + e.getLocalizedMessage());
      e.printStackTrace();
      this.getPluginLoader().disablePlugin(this);
    } finally {
      logger.debug(String.format("构建信息：\nversion = %s \nbuild.type = %s \nconf.CONFIG_VERISON = %s", StreackLib.buildConf.getString("version"), System.getProperty("build.type"), CONFIG_VERSION));
    }
    if (manager.isPreviewBuild()) {
      getLogger().warning("当前StreackLib为预览版构建，可能存在意料之外的错误。如有发现请及时提出Issue以便我们改进！→ https://github.com/StreackMC/StreackLib/issues/new ");
    }
    // 配置文件初始化
    CheckConfigUpdate();
    LoadConf();
    // 启用组件
    logger.info("初始化成功！正在启用组件。");
    EnableHTTPServer();
    // 计划自动更新
    UpdateCheckTask = new BukkitRunnable() {
      @Override
      public void run() {
        UpdateChecker.checkUpdate();
      }
    };
    UpdateCheckTask.runTaskTimerAsynchronously(pluginSelf, 100L, 86400L);
    // 完成
    logger.info("已启用StreackLib v" + getDescription().getVersion() + "");
  }
  @Override
  public void onDisable() {
    DisableHTTPServer();
  }

  /* 载入配置 */
  private void LoadConf() {
    StreackLib.conf.startAutoReload();
    // debug mode
    if (StreackLib.conf.getBoolean("debug", false)) {
      logger.warn("调试模式已启用，你会因此收到更多消息");
      logger.debug("当前环境信息：\n" + manager.generateDebugInfo());
    }
  }

  /* 检查配置文件更新 */
  private void CheckConfigUpdate() {
    logger.info("正在检查配置文件：" + new File(StreackLib.dataPath, "config.yml").getPath());
    if (StreackLib.conf.getInt("version", 0) > CONFIG_VERSION) {
      logger.warn("你的配置文件版本过高？请勿自行修改或强行应用高版本配置文件，否则可能引发意料之外的错误。当前版本：" + StreackLib.conf.getInt("version", 0) + "，适配版本：" + CONFIG_VERSION);
    }
    if (StreackLib.conf.getInt("version", 0) < CONFIG_VERSION) {
      logger.severe("注意：你的配置文件版本过低，请参阅config.new.yml修改你的配置文件；现在未配置的项将使用默认值。当前版本：" + StreackLib.conf.getInt("version", 0) + "，适配版本：" + CONFIG_VERSION);
      try(
        InputStream is = this.getResource("config.yml");
        OutputStream os = Files.newOutputStream(new File(StreackLib.dataPath, "config.new.yml").toPath());
      ) {
          byte[] buffer = new byte[1024];
          int length;
          while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
          }
          os.close();
      } catch (Exception e) {
        logger.severe("配置文件更新失败：" + e.getLocalizedMessage());
        e.printStackTrace();
      }
    }
  }

  /* HTTPServer */
  private void EnableHTTPServer() {
    String host = StreackLib.conf.getString("http-server.host", "0.0.0.0");
    int port = StreackLib.conf.getInt("http-server.port", 8080);
    logger.info("处理模块：HTTPServer");
    if (StreackLib.conf.getBoolean("http-server.enabled", false)) {
      httpServer = new HTTPServer(host, port, this);
      httpServer.startServer();
      logger.info("HTTP 服务器已启动于 " + host + ":" + port);
    } else {
      httpServer = null;
      logger.info("HTTP 服务器未启用");
    }
  }
  private void DisableHTTPServer() {
    if (httpServer != null) {
      httpServer.stopServer();
    }
  }
}
