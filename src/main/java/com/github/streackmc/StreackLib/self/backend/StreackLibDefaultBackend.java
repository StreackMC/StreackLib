package com.github.streackmc.StreackLib.self.backend;

import org.jetbrains.annotations.ApiStatus.Internal;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.utils.HTTPServer;

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
      this.httpServer.startServer();
      logger.info("HTTP 服务器已启动于 " + host + ":" + port);
    } else {
      logger.info("HTTP 服务器未启用");
    }
  }

  /** 不进行任何初始化，仅供manager新建默认用 */
  public StreackLibDefaultBackend(String v) {
  }

  /** StreackLib内部持有的HTTP服务器 */
  public HTTPServer httpServer = null;

  /** @return {@type double[5]} TPS数据 
   * @throws Exception */
  public double[] getLiveTPS() throws Exception {
    double[] defaultData = { -1.0, -1.0, -1.0, -1.0, System.currentTimeMillis() };
    return defaultData;
  }
}
