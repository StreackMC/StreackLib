package com.github.streackmc.StreackLib.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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

  /**
   * 获取文件的MIME类型
   * 
   * @param f 文件对象
   * @return MIME类型
   * @throws IOException 文件不存在、不可达等错误
   */
  public static String getMIME(File f) throws IOException {
    if (!Files.exists(f.toPath())) {
      throw new IOException("目标文件不存在，无法获取MIME类型");
    } else {
      return Files.probeContentType(f.toPath());
    }
  }

  /**
   * 获取文件的MIME类型
   * 
   * @param path 文件路径
   * @return MIME类型
   * @throws IOException 文件不存在、不可达等错误
   */
  public static String getMIME(String path) throws IOException {
    return getMIME(new File(path));
  }
}
