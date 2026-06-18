package com.github.streackmc.StreackLib.self.backend;

import org.jetbrains.annotations.ApiStatus.Internal;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.github.streackmc.StreackLib.utils.SConfig;

/** 内部功能跨平台跳板，需要实现全部方法，否则视作未实现，返回默认值。 */
@Internal
public class StreackLibDefaultBackend {
  /** 调用本方法请保证 {@link StreackLib#ENV} 已被初始化！！ */
  public StreackLibDefaultBackend() {
    // 检查 HTTPServer
    String host = StreackLib.ENV.conf.getString("http-server.host", "0.0.0.0");
    int port = StreackLib.ENV.conf.getInt("http-server.port", 8080);
    logger.info("处理模块：HTTPServer");
    if (StreackLib.ENV.conf.getBoolean("http-server.enabled", false)) {
      this.httpServer = new HTTPServer(host, port);
      this.setHttpServer(httpServer);
      this.httpServer.startServer();
      logger.info("HTTP 服务器已启动于 " + host + ":" + port);
    } else {
      logger.info("HTTP 服务器未启用");
    }
  }

  /** 不进行任何初始化，仅供manager新建默认用 */
  public StreackLibDefaultBackend(String v) {
    logger.debug("正在使用游戏沟通跳板-默认构造" + ((v == null) ? "" : v));
  }

  /** StreackLib内部持有的HTTP服务器 */
  public volatile HTTPServer httpServer = null;
  public void setHttpServer(HTTPServer hs) { httpServer=hs; };

  /** @return {@type double[5]} TPS数据 
   * @throws Exception */
  public double[] getLiveTPS() throws Exception {
    double[] defaultData = { -1.0, -1.0, -1.0, -1.0, System.currentTimeMillis() };
    return defaultData;
  }

  /**
   * @return {@type SConfig} 具有键：
   *         <p>
   *         {@type String} 目标target  原因reason  执行者op
   *         <p>
   *         {@type boolean} 状态banned
   *         <p>
   *         {@type long} 生效时间戳create  过期时间戳expire  (永久封禁则 expire = -1L )
   *         <p>
   */
  public SConfig checkBan(String target) {
    return getDefaultBanEntry(target);
  }

  protected SConfig getDefaultBanEntry(String target) {
    SConfig data = new SConfig("", SConfig.TYPES.JSON, "");
    data.putString("target", target);
    data.putBoolean("banned", false);
    data.putString("reason", "");
    data.putString("op", "");
    data.putLong("create", 0L);
    data.putLong("expire", 0L);
    return data;
  }
}
