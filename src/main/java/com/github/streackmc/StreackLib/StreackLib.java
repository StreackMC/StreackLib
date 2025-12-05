package com.github.streackmc.StreackLib;

import com.github.streackmc.StreackLib.utils.SConfig;
import com.github.streackmc.StreackLib.utils.HTTPServer;
import java.io.File;

public final class StreackLib {
  private StreackLib() {}

  // HTTP Server
  /**
   * 获取一个HTTPServer对象
   * @return 获取到的对象；若当前未启动服务器则为null
   */
  public static HTTPServer getHttpServer() {
    return libinit.httpServer;
  }

  //Conf Handle
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