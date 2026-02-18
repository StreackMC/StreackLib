package com.github.streackmc.StreackLib.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.self.manager;

/**
 * 文件相关工具类，按Linux/MSDOS命令格式快速进行文件操作，支持File和String两种输入。
 * 
 * @author @kdxiaoyi
 * @since 0.4.1
 */
public final class SFile {
  private SFile() {//禁止实例化
  }

  // ==================== 暴露API ====================

  /**
   * 下载一个文件
   * <p>
   * 注意：没有内置非法参数验证，需自行 try...catch 或自行校验。
   * 
   * @param url        目标链接
   * @param targetPath 保存到的父路径
   * @return 下载好的文件
   * @throws IOException              目标服务器返回了错误等导致下载失败
   * @throws IlleaglArgumentException 输入的链接格式有误
   * @throws NullPointerException     传入了 null 参数
   */
  public static File wget(String url, Path targetPath) throws Exception {
    return wgetInternal(url, targetPath, null, null, null, null);
  }

  /**
   * 下载一个文件
   * <p>
   * 注意：没有内置非法参数验证，需自行 try...catch 或自行校验。
   * 
   * @param url        目标链接
   * @param targetPath 保存到的父路径
   * @param fileName   目标文件名
   * @return 下载好的文件
   * @throws IOException              目标服务器返回了错误等导致下载失败
   * @throws IlleaglArgumentException 输入的链接格式有误
   */
  public static File wget(String url, Path targetPath, String fileName) throws Exception {
    return wgetInternal(url, targetPath, fileName, null, null, null);
  }

  /**
   * 下载一个文件
   * <p>
   * 注意：没有内置非法参数验证，需自行 try...catch 或自行校验。
   * 
   * @param url        目标链接
   * @param targetPath 保存到的父路径
   * @param fileName   目标文件名
   * @param ua         自定义 User-Agent
   * @return 下载好的文件
   * @throws IOException              目标服务器返回了错误等导致下载失败
   * @throws IlleaglArgumentException 输入的链接格式有误
   */
  public static File wget(String url, Path targetPath, String fileName, String ua) throws Exception {
    return wgetInternal(url, targetPath, fileName, null, null, ua);
  }

  /**
   * 下载一个文件
   * <p>
   * 注意：没有内置非法参数验证，需自行 try...catch 或自行校验。
   * 
   * @param url        目标链接
   * @param targetPath 保存到的父路径
   * @param fileName   目标文件名
   * @param username   进行鉴权的用户名，附加到标头里
   * @param pwd        进行鉴权的密码，附加到标头里
   * @return 下载好的文件
   * @throws IOException              目标服务器返回了错误等导致下载失败
   * @throws IlleaglArgumentException 输入的链接格式有误
   */
  public static File wget(String url, Path targetPath, String fileName, String username, String pwd) throws Exception {
    return wgetInternal(url, targetPath, fileName, username, pwd, null);
  }

  /**
   * 下载一个文件
   * <p>
   * 注意：没有内置非法参数验证，需自行 try...catch 或自行校验。
   * 
   * @param url        目标链接
   * @param targetPath 保存到的父路径
   * @param fileName   目标文件名
   * @param username   进行鉴权的用户名，附加到标头里
   * @param pwd        进行鉴权的密码，附加到标头里
   * @param ua         自定义 User-Agent
   * @return 下载好的文件
   * @throws IOException              目标服务器返回了错误等导致下载失败
   * @throws IlleaglArgumentException 输入的链接格式有误
   */
  public static File wget(String url, Path targetPath, String fileName, String username, String pwd, String ua) throws Exception {
    return wgetInternal(url, targetPath, fileName, username, pwd, ua);
  }

  /**
   * 在一个父路径下创建文件夹
   * 
   * @param path 父路径
   * @param name 文件夹
   * @return 是否成功创建
   * @since 0.3.0
   */
  public static boolean mkdir(File path, String name) {
    File goal = new File(path, name);
    if (goal.exists()) {
      if (!goal.isDirectory()) {
        return false;
      }
    } else {
      try {
        return goal.mkdirs();
      } catch (Exception e) {
        return false;
      }
    }
    return false;
  }
  
  /**
   * 在一个父路径下创建文件夹
   * 
   * @param path 路径
   * @return 是否成功创建
   * @since 0.3.0
   */
  public static boolean mkdir(String path) {
    File goal = new File(path);
    if (goal.exists()) {
      if (!goal.isDirectory()) {
        return false;
      }
    } else {
      try {
        return goal.mkdirs();
      } catch (Exception e) {
        return false;
      }
    }
    return false;
  }

  /**
   * 在一个父路径下创建文件夹
   * 
   * @param path 父路径
   * @param name 文件夹
   * @return 是否成功创建
   * @since 0.3.0
   */
  public static boolean md(File path, String name) {
    return mkdir(path, name);
  }
  
  /**
   * 在一个父路径下创建文件夹
   * 
   * @param path 路径
   * @return 是否成功创建
   * @since 0.3.0
   */
  public static boolean md(String path) {
    return mkdir(path);
  }

  /**
   * 获取文件的MIME类型
   * 
   * @param f 文件对象
   * @return MIME类型
   * @throws IOException 文件不存在、不可达等错误
   * @since 0.4.0
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
   * @since 0.4.0
   */
  public static String getMIME(String path) throws IOException {
    return getMIME(new File(path));
  }

  /**
   * 获取文件的MIME类型
   * 
   * @param f 文件对象
   * @return MIME类型
   * @throws IOException 文件不存在、不可达等错误
   * @since 0.4.0
   */
  public static String fileMimeType(File f) throws IOException {
    return getMIME(f);
  }

  /**
   * 获取文件的MIME类型
   * 
   * @param path 文件路径
   * @return MIME类型
   * @throws IOException 文件不存在、不可达等错误
   * @since 0.4.0
   */
  public static String fileMimeType(String path) throws IOException {
    return getMIME(new File(path));
  }

  /**
   * 获取文件的MIME类型
   * 
   * @param f 文件对象
   * @return MIME类型
   * @throws IOException 文件不存在、不可达等错误
   * @since 0.4.0
   */
  public static String AddType(File f) throws IOException {
    return getMIME(f);
  }

  /**
   * 获取文件的MIME类型
   * 
   * @param path 文件路径
   * @return MIME类型
   * @throws IOException 文件不存在、不可达等错误
   * @since 0.4.0
   */
  public static String AddType(String path) throws IOException {
    return getMIME(new File(path));
  }

    /**
   * 创建空文件（类似Linux的touch命令）
   * 如果文件已存在，则更新其修改时间
   * 
   * @param path 文件路径
   * @return true如果文件创建成功或已存在
   * @throws IOException 如果权限不足或创建失败
   */
  public static boolean touch(String path) throws IOException {
    return createFileInternal(path);
  }

  /**
   * 创建空文件
   * 
   * @param file 文件对象
   * @return true如果文件创建成功或已存在
   * @throws IOException 如果权限不足或创建失败
   */
  public static boolean touch(File file) throws IOException {
    return createFileInternal(file.getPath());
  }

  /**
   * 删除文件（别名：rm）
   * 如果文件不存在，视为成功
   * 
   * @param path 文件路径
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean rm(String path) throws IOException {
    return deleteFileInternal(path);
  }

  /**
   * 删除文件
   * 
   * @param file 文件对象
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean rm(File file) throws IOException {
    return deleteFileInternal(file.getPath());
  }

  /**
   * 删除文件（别名：remove）
   * 
   * @param path 文件路径
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean remove(String path) throws IOException {
    return deleteFileInternal(path);
  }

  /**
   * 删除文件
   * 
   * @param file 文件对象
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean remove(File file) throws IOException {
    return deleteFileInternal(file.getPath());
  }

  /**
   * 删除文件（别名：eraser）
   * 
   * @param path 文件路径
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean eraser(String path) throws IOException {
    return deleteFileInternal(path);
  }

  /**
   * 删除文件
   * 
   * @param file 文件对象
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean eraser(File file) throws IOException {
    return deleteFileInternal(file.getPath());
  }

  /**
   * 删除文件（别名：del）
   * 
   * @param path 文件路径
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean del(String path) throws IOException {
    return deleteFileInternal(path);
  }

  /**
   * 删除文件
   * 
   * @param file 文件对象
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean del(File file) throws IOException {
    return deleteFileInternal(file.getPath());
  }

  /**
   * 删除文件（别名：delete）
   * 
   * @param path 文件路径
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean delete(String path) throws IOException {
    return deleteFileInternal(path);
  }

  /**
   * 删除文件
   * 
   * @param file 文件对象
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  public static boolean delete(File file) throws IOException {
    return deleteFileInternal(file.getPath());
  }

  /**
   * 复制文件
   * 如果目标已存在，则覆盖
   * 如果目标是目录，则将源文件名拼接到目录后
   * 
   * @param source 源文件路径
   * @param target 目标路径（文件或目录）
   * @return true如果复制成功
   * @throws IOException 如果源文件不存在、权限不足或复制失败
   */
  public static boolean cp(String source, String target) throws IOException {
    return copyFileInternal(source, target);
  }

  /**
   * 复制文件
   * 
   * @param source 源文件
   * @param target 目标文件或目录
   * @return true如果复制成功
   * @throws IOException 如果源文件不存在、权限不足或复制失败
   */
  public static boolean cp(File source, File target) throws IOException {
    return copyFileInternal(source.getPath(), target.getPath());
  }

  /**
   * 复制文件
   * 如果目标已存在，则覆盖
   * 如果目标是目录，则将源文件名拼接到目录后
   * 
   * @param source 源文件路径
   * @param target 目标路径（文件或目录）
   * @return true如果复制成功
   * @throws IOException 如果源文件不存在、权限不足或复制失败
   */
  public static boolean copy(String source, String target) throws IOException {
    return copyFileInternal(source, target);
  }

  /**
   * 复制文件
   * 
   * @param source 源文件
   * @param target 目标文件或目录
   * @return true如果复制成功
   * @throws IOException 如果源文件不存在、权限不足或复制失败
   */
  public static boolean copy(File source, File target) throws IOException {
    return copyFileInternal(source.getPath(), target.getPath());
  }

  /**
   * 复制文件并拼接（追加模式）
   * 将源文件内容追加到目标文件末尾，类似MSDOS的copy file1 + file2 file3
   * 如果目标不存在则创建，如果存在则追加
   * 
   * @param source 源文件路径
   * @param target 目标文件路径
   * @return true如果拼接成功
   * @throws IOException 如果源文件不存在、权限不足或拼接失败
   */
  public static boolean copyJoin(String source, String target) throws IOException {
    return copyFileJoinInternal(source, target);
  }

  /**
   * 复制文件并拼接
   * 
   * @param source 源文件
   * @param target 目标文件
   * @return true如果拼接成功
   * @throws IOException 如果源文件不存在、权限不足或拼接失败
   */
  public static boolean copyJoin(File source, File target) throws IOException {
    return copyFileJoinInternal(source.getPath(), target.getPath());
  }

  /**
   * 移动文件
   * 使用组合方法：先复制再删除源文件
   * 如果目标已存在，则覆盖
   * 如果目标是目录，则将源文件名拼接到目录后
   * 
   * @param source 源文件路径
   * @param target 目标路径（文件或目录）
   * @return true如果移动成功
   * @throws IOException 如果源文件不存在、权限不足或移动失败
   */
  public static boolean mv(String source, String target) throws IOException {
    return moveFileInternal(source, target);
  }

  /**
   * 移动文件
   * 
   * @param source 源文件
   * @param target 目标文件或目录
   * @return true如果移动成功
   * @throws IOException 如果源文件不存在、权限不足或移动失败
   */
  public static boolean mv(File source, File target) throws IOException {
    return moveFileInternal(source.getPath(), target.getPath());
  }

  /**
   * 移动文件
   * 使用组合方法：先复制再删除源文件
   * 如果目标已存在，则覆盖
   * 如果目标是目录，则将源文件名拼接到目录后
   * 
   * @param source 源文件路径
   * @param target 目标路径（文件或目录）
   * @return true如果移动成功
   * @throws IOException 如果源文件不存在、权限不足或移动失败
   */
  public static boolean move(String source, String target) throws IOException {
    return moveFileInternal(source, target);
  }

  /**
   * 移动文件
   * 
   * @param source 源文件
   * @param target 目标文件或目录
   * @return true如果移动成功
   * @throws IOException 如果源文件不存在、权限不足或移动失败
   */
  public static boolean move(File source, File target) throws IOException {
    return moveFileInternal(source.getPath(), target.getPath());
  }

  /**
   * 移动文件并拼接（追加模式）
   * 将源文件内容追加到目标文件后删除源文件
   * 使用组合方法：先拼接再删除源文件
   * 
   * @param source 源文件路径
   * @param target 目标文件路径
   * @return true如果移动拼接成功
   * @throws IOException 如果源文件不存在、权限不足或操作失败
   */
  public static boolean moveJoin(String source, String target) throws IOException {
    return moveFileJoinInternal(source, target);
  }

  /**
   * 移动文件并拼接
   * 
   * @param source 源文件
   * @param target 目标文件
   * @return true如果移动拼接成功
   * @throws IOException 如果源文件不存在、权限不足或操作失败
   */
  public static boolean moveJoin(File source, File target) throws IOException {
    return moveFileJoinInternal(source.getPath(), target.getPath());
  }

  /**
   * 重命名文件
   * 如果目标已存在，则抛出异常（不覆盖）
   * 
   * @param path 原文件路径
   * @param newName 新文件名（可以是相对名或完整路径）
   * @return true如果重命名成功
   * @throws IOException 如果源文件不存在、目标已存在或权限不足
   */
  public static boolean rename(String path, String newName) throws IOException {
    return renameFileInternal(path, newName);
  }

  /**
   * 重命名文件
   * 
   * @param file 原文件
   * @param newName 新文件名（可以是相对名或完整路径）
   * @return true如果重命名成功
   * @throws IOException 如果源文件不存在、目标已存在或权限不足
   */
  public static boolean rename(File file, String newName) throws IOException {
    return renameFileInternal(file.getPath(), newName);
  }

  /**
   * 重命名文件
   * 如果目标已存在，则抛出异常（不覆盖）
   * 
   * @param path 原文件路径
   * @param newName 新文件名
   * @return true如果重命名成功
   * @throws IOException 如果源文件不存在、目标已存在或权限不足
   */
  public static boolean ren(String path, String newName) throws IOException {
    return renameFileInternal(path, newName);
  }

  /**
   * 重命名文件
   * 
   * @param file 原文件
   * @param newName 新文件名
   * @return true如果重命名成功
   * @throws IOException 如果源文件不存在、目标已存在或权限不足
   */
  public static boolean ren(File file, String newName) throws IOException {
    return renameFileInternal(file.getPath(), newName);
  }

  /**
   * 列出目录内容（别名：ls）
   * 返回目录中的文件列表
   * 
   * @param path 目录路径
   * @return 文件对象列表
   * @throws IOException 如果路径不存在或不是目录
   */
  public static List<File> ls(String path) throws IOException {
    return lsInternal(path);
  }

  /**
   * 列出目录内容
   * 
   * @param dir 目录对象
   * @return 文件对象列表
   * @throws IOException 如果路径不存在或不是目录
   */
  public static List<File> ls(File dir) throws IOException {
    return lsInternal(dir.getPath());
  }

  /**
   * 列出目录内容（别名：ls）
   * 返回目录中的文件列表
   * 
   * @param path 目录路径
   * @return 文件对象列表
   * @throws IOException 如果路径不存在或不是目录
   */
  public static List<File> dir(String path) throws IOException {
    return lsInternal(path);
  }

  /**
   * 列出目录内容
   * 
   * @param dir 目录对象
   * @return 文件对象列表
   * @throws IOException 如果路径不存在或不是目录
   */
  public static List<File> dir(File dir) throws IOException {
    return lsInternal(dir.getPath());
  }

  /**
   * 列出目录内容为字符串列表
   * 返回目录中的文件名列表
   * 
   * @param path 目录路径
   * @return 文件名列表
   * @throws IOException 如果路径不存在或不是目录
   */
  public static List<String> lsStr(String path) throws IOException {
    List<File> files = lsInternal(path);
    return files.stream().map(File::getName).collect(Collectors.toList());
  }

  /**
   * 列出目录内容为字符串列表
   * 
   * @param dir 目录对象
   * @return 文件名列表
   * @throws IOException 如果路径不存在或不是目录
   */
  public static List<String> lsStr(File dir) throws IOException {
    List<File> files = lsInternal(dir.getPath());
    return files.stream().map(File::getName).collect(Collectors.toList());
  }

  /**
   * 列出目录内容为字符串列表
   * 返回目录中的文件名列表
   * 
   * @param path 目录路径
   * @return 文件名列表
   * @throws IOException 如果路径不存在或不是目录
   */
  public static List<String> dirStr(String path) throws IOException {
    return lsStr(path);
  }
  
  /**
   * 列出目录内容为字符串列表
   * 
   * @param dir 目录对象
   * @return 文件名列表
   * @throws IOException 如果路径不存在或不是目录
   */
  public static List<String> dirStr(File dir) throws IOException {
    return lsStr(dir);
  }

  /**
   * 创建符号链接（别名：sn，类似Linux的ln -s）
   * 创建指向目标文件的软链接
   * 
   * @param target 目标文件路径
   * @param link 链接文件路径
   * @return true如果创建成功
   * @throws IOException 如果权限不足或创建失败
   */
  public static boolean sn(String target, String link) throws IOException {
    return createLinkInternal(target, link);
  }

  /**
   * 创建符号链接
   * 
   * @param target 目标文件
   * @param link 链接文件
   * @return true如果创建成功
   * @throws IOException 如果权限不足或创建失败
   */
  public static boolean sn(File target, File link) throws IOException {
    return createLinkInternal(target.getPath(), link.getPath());
  }



    // ==================== 具体实现 ====================

  /**
   * 内部实现：创建空文件
   * @param path 文件路径
   * @return true如果文件创建成功或已存在
   * @throws IOException 如果权限不足或创建失败
   */
  private static boolean createFileInternal(String path) throws IOException {
    File file = new File(path);
    
    if (file.exists()) {
      // 文件已存在，更新修改时间（类似touch的行为）
      return file.setLastModified(System.currentTimeMillis());
    }
    
    // 确保父目录存在
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      if (!parent.mkdirs() && !parent.exists()) {
        throw new IOException("无法创建父目录: " + parent.getPath());
      }
    }
    
    try {
      // 尝试获取文件锁
      RandomAccessFile raf = new RandomAccessFile(file, "rw");
      FileLock lock = raf.getChannel().tryLock();
      if (lock != null) {
        lock.release();
        raf.close();
      }
    } catch (IOException e) {
      // 锁获取失败，但仍尝试创建文件
    }
    
    return file.createNewFile();
  }

  /**
   * 内部实现：删除文件
   * @param path 文件路径
   * @return true如果文件删除成功
   * @throws IOException 只有权限严重不足等严重问题才抛出
   */
  private static boolean deleteFileInternal(String path) throws IOException {
    File file = new File(path);
    
    if (!file.exists()) {
      return true; // 文件不存在，视为成功
    }
    
    if (!file.canWrite()) {
      throw new IOException("权限不足，无法删除文件: " + path);
    }
    
    // 尝试删除，无论成功失败都不抛异常（除非是权限问题）
    boolean result = file.delete();
    return result;
  }

  /**
   * 内部实现：复制文件（覆盖模式）
   * @param sourcePath 源文件路径
   * @param targetPath 目标路径（可以是文件或目录）
   * @return true如果复制成功
   * @throws IOException 如果源文件不存在、权限不足或复制失败
   */
  private static boolean copyFileInternal(String sourcePath, String targetPath) throws IOException {
    File source = new File(sourcePath);
    File target = new File(targetPath);
    
    if (!source.exists()) {
      throw new IOException("源文件不存在: " + sourcePath);
    }
    
    if (!source.isFile()) {
      throw new IOException("源路径不是文件: " + sourcePath);
    }
    
    // 如果目标是目录，则将源文件名拼接到目录后
    if (target.exists() && target.isDirectory()) {
      target = new File(target, source.getName());
    }
    
    // 如果目标文件已存在，直接覆盖
    if (target.exists() && target.isFile()) {
      if (!target.delete()) {
        throw new IOException("无法删除已存在的目标文件: " + target.getPath());
      }
    }
    
    // 确保目标父目录存在
    File parent = target.getParentFile();
    if (parent != null && !parent.exists()) {
      if (!parent.mkdirs() && !parent.exists()) {
        throw new IOException("无法创建目标父目录: " + parent.getPath());
      }
    }
    
    // 执行复制（使用NIO提高性能，并加锁）
    try (FileInputStream fis = new FileInputStream(source);
       FileOutputStream fos = new FileOutputStream(target);
       FileChannel sourceChannel = fis.getChannel();
       FileChannel targetChannel = fos.getChannel();
       FileLock lock = targetChannel.tryLock()) {
      
      if (lock == null) {
        throw new IOException("无法获取目标文件锁: " + target.getPath());
      }
      
      targetChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
    }
    
    return true;
  }

  /**
   * 内部实现：复制文件（拼接模式）
   * 将源文件内容追加到目标文件末尾
   * @param sourcePath 源文件路径
   * @param targetPath 目标文件路径（必须是具体文件，不能是目录）
   * @return true如果拼接成功
   * @throws IOException 如果源文件不存在、权限不足或拼接失败
   */
  private static boolean copyFileJoinInternal(String sourcePath, String targetPath) throws IOException {
    File source = new File(sourcePath);
    File target = new File(targetPath);
    
    if (!source.exists()) {
      throw new IOException("源文件不存在: " + sourcePath);
    }
    
    if (!source.isFile()) {
      throw new IOException("源路径不是文件: " + sourcePath);
    }
    
    if (target.exists() && target.isDirectory()) {
      throw new IOException("目标不能是目录: " + targetPath);
    }
    
    // 确保目标父目录存在
    File parent = target.getParentFile();
    if (parent != null && !parent.exists()) {
      if (!parent.mkdirs() && !parent.exists()) {
        throw new IOException("无法创建目标父目录: " + parent.getPath());
      }
    }
    
    // 拼接文件内容（源文件追加到目标文件）
    try (FileInputStream fis = new FileInputStream(source);
       FileOutputStream fos = new FileOutputStream(target, true); // 追加模式
       FileChannel sourceChannel = fis.getChannel();
       FileChannel targetChannel = fos.getChannel();
       FileLock lock = targetChannel.tryLock()) {
      
      if (lock == null) {
        throw new IOException("无法获取目标文件锁: " + target.getPath());
      }
      
      targetChannel.transferFrom(sourceChannel, targetChannel.size(), sourceChannel.size());
    }
    
    return true;
  }

  /**
   * 内部实现：移动文件（组合方法：复制+删除）
   * @param sourcePath 源文件路径
   * @param targetPath 目标路径（可以是文件或目录）
   * @return true如果移动成功
   * @throws IOException 如果源文件不存在、权限不足或移动失败
   */
  private static boolean moveFileInternal(String sourcePath, String targetPath) throws IOException {
    // 先复制文件
    boolean copySuccess = copyFileInternal(sourcePath, targetPath);
    
    if (!copySuccess) {
      return false;
    }
    
    // 再删除源文件
    return deleteFileInternal(sourcePath);
  }

  /**
   * 内部实现：移动文件并拼接（组合方法：拼接+删除）
   * 将源文件拼接至目标文件后删除源文件
   * @param sourcePath 源文件路径
   * @param targetPath 目标文件路径
   * @return true如果移动拼接成功
   * @throws IOException 如果源文件不存在、权限不足或操作失败
   */
  private static boolean moveFileJoinInternal(String sourcePath, String targetPath) throws IOException {
    // 先拼接文件
    boolean joinSuccess = copyFileJoinInternal(sourcePath, targetPath);
    
    if (!joinSuccess) {
      return false;
    }
    
    // 再删除源文件
    return deleteFileInternal(sourcePath);
  }

  /**
   * 内部实现：重命名文件
   * @param path 原文件路径
   * @param newName 新文件名（可以是相对名或完整路径）
   * @return true如果重命名成功
   * @throws IOException 如果源文件不存在、目标已存在或权限不足
   */
  private static boolean renameFileInternal(String path, String newName) throws IOException {
    File source = new File(path);
    
    if (!source.exists()) {
      throw new IOException("源文件不存在: " + path);
    }
    
    // 构建目标文件
    File target;
    File newNameFile = new File(newName);
    
    if (newNameFile.isAbsolute()) {
      // 如果提供了绝对路径，直接使用
      target = newNameFile;
    } else {
      // 如果是相对路径，在当前目录下重命名
      target = new File(source.getParent(), newName);
    }
    
    // 如果目标已存在，抛出异常（rename命令不覆盖）
    if (target.exists()) {
      throw new IOException("目标文件已存在: " + target.getPath());
    }
    
    // 尝试获取源文件锁
    try {
      RandomAccessFile raf = new RandomAccessFile(source, "rw");
      FileLock lock = raf.getChannel().tryLock();
      if (lock != null) {
        lock.release();
        raf.close();
      }
    } catch (IOException e) {
      // 锁获取失败，但仍尝试重命名
    }
    
    // 执行重命名
    if (!source.renameTo(target)) {
      throw new IOException("无法重命名文件: " + path + " to " + newName);
    }
    
    return true;
  }

  /**
   * 内部实现：列出目录内容
   * @param path 目录路径
   * @return 文件对象列表
   * @throws IOException 如果路径不存在或不是目录
   */
  private static List<File> lsInternal(String path) throws IOException {
    File dir = new File(path);
    
    if (!dir.exists()) {
      throw new IOException("路径不存在: " + path);
    }
    
    if (!dir.isDirectory()) {
      throw new IOException("路径不是目录: " + path);
    }
    
    File[] files = dir.listFiles();
    List<File> result = new ArrayList<>();
    
    if (files != null) {
      for (File file : files) {
        result.add(file);
      }
    }
    
    return result;
  }

  /**
   * 内部实现：创建符号链接（软链接）
   * @param targetPath 目标文件路径
   * @param linkPath 链接文件路径
   * @return true如果创建成功
   * @throws IOException 如果权限不足或创建失败
   */
  private static boolean createLinkInternal(String targetPath, String linkPath) throws IOException {
    File target = new File(targetPath);
    File link = new File(linkPath);
    
    if (!target.exists()) {
      throw new IOException("目标文件不存在: " + targetPath);
    }
    
    // 确保父目录存在
    File parent = link.getParentFile();
    if (parent != null && !parent.exists()) {
      if (!parent.mkdirs() && !parent.exists()) {
        throw new IOException("无法创建链接父目录: " + parent.getPath());
      }
    }
    
    // 创建符号链接
    Path targetPathObj = Paths.get(target.getAbsolutePath());
    Path linkPathObj = Paths.get(link.getAbsolutePath());
    
    try {
      Files.createSymbolicLink(linkPathObj, targetPathObj);
      return true;
    } catch (UnsupportedOperationException e) {
      throw new IOException("当前操作系统不支持符号链接", e);
    } catch (IOException e) {
      throw new IOException("创建符号链接失败", e);
    }
  }

  private static File wgetInternal(String downloadUrl, Path targetDir, String saveAsName, String username, String password, String customUserAgent) throws Exception {
    Objects.requireNonNull(targetDir, "目标目录不得为 null");
    Objects.requireNonNull(downloadUrl, "目标链接不得为 null");
    String userAgent = (customUserAgent != null && !customUserAgent.isEmpty())
        ? customUserAgent
        : String.format("StreackLib/%s (Java %s; %s %s)",
            manager.getBuildVersion(),
            System.getProperty("java.version"),
            System.getProperty("os.name"),
            System.getProperty("os.arch"));
            // 获取文件名
            String fileName = saveAsName != null && !saveAsName.isEmpty()
            ? saveAsName
        : downloadUrl.substring(downloadUrl.lastIndexOf("/") + 1);
    if (fileName.isEmpty()) {
      fileName = String.format("wget-output-%s", UUID.randomUUID().toString());
    }
    
    Path targetFile = targetDir.resolve(fileName);
    // 创建临时文件（原子写入）
    Path tempFile = targetDir.resolve(fileName + ".tmp." + UUID.randomUUID().toString());
    
    logger.debug(String.format("开始以[%s]为UA下载 %s 至 %s", userAgent, downloadUrl, targetDir.toString()));

    HttpURLConnection conn = null;
    InputStream inputStream = null;
    OutputStream outputStream = null;

    try {
      // 构建请求
      URL urlObj = new URI(downloadUrl).toURL();
      conn = (HttpURLConnection) urlObj.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(60000);
      conn.setInstanceFollowRedirects(true);
      conn.setRequestProperty("User-Agent", userAgent);
      conn.setRequestProperty("Accept", "application/octet-stream");

      // 设置基础鉴权（如果提供了用户名和密码）
      if (username != null && !username.isEmpty() && password != null) {
        String auth = username + ":" + password;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
      }

      int responseCode = conn.getResponseCode();
      if (responseCode != HttpURLConnection.HTTP_OK) {
        throw new IOException(String.format(
            "无法下载更新文件，远程服务器返回了: %d %s",
            responseCode,
            conn.getResponseMessage()));
      }

      // 下载到临时文件
      int fileSize = conn.getContentLength();
      logger.debug(String.format("文件大小: %.2f MB", fileSize / 1024.0 / 1024.0));

      inputStream = conn.getInputStream();
      outputStream = Files.newOutputStream(
          tempFile,
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING);

      byte[] buffer = new byte[8192];
      long totalBytesRead = 0;
      int bytesRead;
      long lastLogTime = System.currentTimeMillis();

      while ((bytesRead = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, bytesRead);
        totalBytesRead += bytesRead;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLogTime > 5000 && fileSize > 0) {
          logger.debug(String.format(
              "下载进度: %.1f%%",
              (totalBytesRead * 100.0) / fileSize));
          lastLogTime = currentTime;
        }
      }

      outputStream.flush();

      // 原子移动：临时文件 -> 目标文件
      // 使用 ATOMIC_MOVE 确保原子性，REPLACE_EXISTING 覆盖已存在文件
      Files.move(tempFile, targetFile,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);

      logger.debug(String.format("下载完成：%s", targetFile.toString()));

      return targetFile.toFile();

    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("无效的URL: " + downloadUrl, e);
    } finally {
      // 关闭流
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException ignored) {
        }
      }
      if (outputStream != null) {
        try {
          outputStream.close();
        } catch (IOException ignored) {
        }
      }
      if (conn != null) {
        conn.disconnect();
      }
      // 清理临时文件（如果还存在）
      if (tempFile != null) {
        try {
          Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
      }
    }
  }
}
