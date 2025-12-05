package com.github.streackmc.StreackLib.utils;

import java.io.File;
import java.io.IOException;

public final class SFile {
  private SFile() {}

  /**
   * 在一个父路径下创建文件夹
   * @param path 父路径
   * @param name 文件夹
   * @throws IOException
   */
  public static void mkdir(File path, String name) throws IOException {
    File goal = new File(path, name);
    if (goal.exists()) {
      if (!goal.isDirectory()) {
        throw new IOException("目标路径已存在文件，因此无法创建文件夹");
      }
    } else {
      try {
        goal.mkdirs();
      } catch (Exception e) {
        throw new IOException("无法创建目标文件夹：" + e.getMessage());
      }
    }
  }
}
