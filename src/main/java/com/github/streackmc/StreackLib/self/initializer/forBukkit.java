package com.github.streackmc.StreackLib.self.initializer;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.ApiStatus.Internal;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.self.manager;
import com.github.streackmc.StreackLib.self.updateChecker;
import com.github.streackmc.StreackLib.self.backend.StreackLibBukkitBackend;
import com.github.streackmc.StreackLib.types.SConfig;

@Internal
public class forBukkit extends JavaPlugin {
  private Long CONFIG_VERSION = 0L;

  StreackLibBukkitBackend backend;

  @SuppressWarnings({ "removal", "deprecation" })// 兼容Spigot
  @Override
  public void onEnable() {
    // 展示启动信息
    getLogger().info(
      "\n" +
      "  ____    _                                  _      __  __    ____ " + "\n" +
      " / ___|  | |_   _ __    ___    __ _    ___  | | __ |  \\/  |  / ___|" + "\n" +
      " \\___ \\  | __| | '__|  / _ \\  / _` |  / __| | |/ / | |\\/| | | |    " + "\n" +
      "  ___) | | |_  | |    |  __/ | (_| | | (__  |   <  | |  | | | |___ " + "\n" +
      " |____/   \\__| |_|     \\___|  \\__,_|  \\___| |_|\\_\\ |_|  |_|  \\____|" + "\n" +
      "                                                                   "
    );
    saveDefaultConfig();

    // 填充共享变量
    StreackLib.ENV.dataPath = this.getDataFolder();
    StreackLib.ENV.conf = new SConfig(new File(StreackLib.ENV.dataPath, "config.yml"), "YAML");
    StreackLib.ENV.serverProperties = new SConfig(this.getDataPath().resolve("../../server.properties"), "prop");
    backend = new StreackLibBukkitBackend();
    backend.plugin = this;
    manager.backend = backend;
    backend.init(); // 初始化 HTTP 服务器等（此时 manager.backend 已指向本实例，logger 路由正确）

    // 读取构建信息
    try {
      StreackLib.ENV.buildConf = new SConfig(manager.getResourceAsFile("/plugin.yml"), "yml");
      StreackLib.ENV.defaultConf = new SConfig(manager.getResourceAsFile("/config.yml"), "yml");
      CONFIG_VERSION = StreackLib.ENV.defaultConf.getLong("version", -1L);
    } catch (Exception e) {
      logger.severe("未能获取构建信息：" + e.getLocalizedMessage());
      e.printStackTrace();
      this.getPluginLoader().disablePlugin(this);
    } finally {
      logger.debug(String.format("构建信息：\nversion = %s \nbuild.type = %s \nconf.CONFIG_VERISON = %s", StreackLib.ENV.buildConf.getString("version"), System.getProperty("build.type"), CONFIG_VERSION));
    }
    if (manager.isPreviewBuild()) {
      getLogger().warning("当前StreackLib为预览版构建，可能存在意料之外的错误。如有发现请及时提出Issue以便我们改进！→ https://github.com/StreackMC/StreackLib/issues/new ");
    }

    // 配置文件初始化
    CheckConfigUpdate();
    StreackLib.ENV.conf.setAutoReload(true);
    // debug mode
    if (StreackLib.isDebugMode()) {
      logger.warn("调试模式已启用，你会因此收到更多消息");
      logger.debug("当前环境信息：\n" + manager.generateDebugInfo());
    }

    // 启用组件
    logger.info("初始化成功！正在启用组件。");

    // 计划自动更新
    if (!StreackLib.isDebugMode()) {
      logger.info("强制跳过更新检查，因为此功能尚未完成。");
      return;
    } else {
      backend.UpdateCheckTask = new BukkitRunnable() {
        @Override
        public void run() {
          updateChecker.checkUpdate();
        }
      };
      backend.UpdateCheckTask.runTaskTimerAsynchronously(this, 100L, 86400L);
    }

    // TPS追踪
    backend.UpdateTpsTask = new BukkitRunnable() {
      @Override
      public void run() {
        backend.onTickDoing();
      }
    };
    backend.UpdateTpsTask.runTaskTimer(this, 0L, 1L);

    // 完成
    manager.backend = backend;
    logger.info("已启用StreackLib v" + getDescription().getVersion() + "");
  }
  @Override
  public void onDisable() {
    if (backend.httpServer != null) {
      backend.httpServer.stopServer();
      backend.setHttpServer(null);
    }
  }

  /* 检查配置文件更新 */
  private void CheckConfigUpdate() {
    logger.info("正在检查配置文件：" + new File(StreackLib.ENV.dataPath, "config.yml").getPath());
    if (StreackLib.ENV.conf.getInt("version", 0) > CONFIG_VERSION) {
      logger.warn("你的配置文件版本过高？请勿自行修改或强行应用高版本配置文件，否则可能引发意料之外的错误。当前版本：" + StreackLib.ENV.conf.getInt("version", 0) + "，适配版本：" + CONFIG_VERSION);
    }
    if (StreackLib.ENV.conf.getInt("version", 0) < CONFIG_VERSION) {
      logger.severe("注意：你的配置文件版本过低，请参阅config.new.yml修改你的配置文件；现在未配置的项将使用默认值。当前版本：" + StreackLib.ENV.conf.getInt("version", 0) + "，适配版本：" + CONFIG_VERSION);
      try(
        InputStream is = this.getResource("config.yml");
        OutputStream os = Files.newOutputStream(new File(StreackLib.ENV.dataPath, "config.new.yml").toPath());
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
}
