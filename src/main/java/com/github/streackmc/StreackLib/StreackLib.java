package com.github.streackmc.StreackLib;

import com.github.streackmc.StreackLib.utils.ConfHandler;
import com.github.streackmc.StreackLib.utils.HTTPServer;
import java.io.File;

public final class StreackLib {
  private StreackLib() {}

  // HTTP Server
  /**
   * 获取一个HTTPServer对象
   * @return null/HTTPServer | 获取到的对象；若当前未启动服务器则为null
   */
  public static HTTPServer getHttpServer() {
    return libinit.httpServer;
  }

  //Conf Handle
  /**
   * 获取一个指向一个文件的配置文件对象。使用此对象方法可以更快捷地操作配置文件。建议使用前先使用Bukkit自带的释放配置文件以放出默认配置文件。
   * @param file File | 配置文件的对象
   * @param type String | 配置文件的类型
   * @return ConfHandler | 一个配置文件对象
   */
  public static ConfHandler initConf(File file, String type) {
    return new ConfHandler(file, type);
  }
}