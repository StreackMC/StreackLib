package com.github.streackmc.StreackLib;

import java.io.File;

import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.github.streackmc.StreackLib.utils.SConfig;

public final class StreackLib {
  private StreackLib() {
  }
  
  public static SConfig conf;
  public static SConfig defaultConf;
  public static SConfig buildConf;

  // HTTP Server
  /**
   * 获取一个HTTPServer对象
   * @return 获取到的对象；若当前未启动服务器则为null
   */
  public static HTTPServer getHttpServer() {
    return libinit.httpServer;
  }
  /**
   * 新建一个HTTPServer对象
   * @param hostname 监听地址
   * @param port 监听端口
   * @return 获取到的对象
   */
  public static HTTPServer newHttpServer(String hostname, int port) {
    return new HTTPServer(hostname, port, libinit.pluginSelf);
  }

  // Conf Handle
  /**
   * 获取一个指向一个文件的配置文件对象。使用此对象方法可以更快捷地操作配置文件。建议使用前先使用Bukkit自带的释放配置文件以放出默认配置文件。
   * @param file 配置文件的对象
   * @param type 配置文件的类型
   * @return 一个配置文件对象
   */
  public static SConfig initConf(File file, String type) {
    return new SConfig(file, type);
  }
}