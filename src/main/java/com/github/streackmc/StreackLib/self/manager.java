package com.github.streackmc.StreackLib.self;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Nullable;

import com.github.streackmc.StreackLib.StreackLib;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * 提供插件内部管理的类
 * 
 * @author kdxiaoyi
 * @since 0.4.1
 */
@Internal
public class manager {

  private manager() {}// 禁止实例化


  /**
   * 提取JAR内部资源文件
   * 
   * @param name 要提取的资源文件
   * @return 资源文件对象
   * @throws FileNotFoundException 没有找到指定的资源文件
   * @throws IOException           无法创建指定的临时文件
   */
  @Internal
  public static File getResourceAsFile(String name) throws Exception {
    InputStream in = StreackLib.class.getResourceAsStream(name);
    if (in == null) {
      throw new FileNotFoundException(String.format("没有找到 %s ，打包时是否包括了它？", name));
    }
    Path tmp = File.createTempFile("extract-", ".tmp").toPath();
    Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
    tmp.toFile().deleteOnExit();
    return tmp.toFile();
  }

  /**
   * 获取当前StreackLib是否为预览版构建
   * 
   * @return 若为预览版构建则返回true，否则返回false
   */
  @Internal
  public static boolean isPreviewBuild() {
    if (System.getProperty("build.type", "preview").equals("release")) {
      return false;
    }
    return true;
  }

  /**
   * 获取当前StreackLib的版本
   * 
   * @return 版本信息
   */
  public static String getBuildVersion() {
    return StreackLib.buildConf.getString("version", null);
  }

  /**
   * 生成当前环境信息
   * 
   * @return 环境信息
   */
  @Internal
  public static String generateDebugInfo() {
    /* JVM Info */
    MemoryMXBean mmxb = ManagementFactory.getMemoryMXBean();
    RuntimeMXBean rmxb = ManagementFactory.getRuntimeMXBean();

    /* Hardware */
    SystemInfo si = new SystemInfo();
    HardwareAbstractionLayer hw = si.getHardware();
    CentralProcessor cpu = hw.getProcessor();
    GlobalMemory mem = hw.getMemory();
    GraphicsCard[] gpus = hw.getGraphicsCards().toArray(new GraphicsCard[0]);

    /* Get GPU List */
    String gpu_listed = "";
    int loop = 0;
    for (GraphicsCard g : gpus) {
      loop++;
      if (loop > 1) {
        gpu_listed += "\n                  ";
      }
      gpu_listed += "[" + loop + "] " + g.getName() + " <" + g.getVendor() + ">";
    }
    System.out.println();

    /* Build */
    return
        /* 系统核心信息 */
        "==> Running Time Meta" +
        "\nlocalTimestamp  = " + System.currentTimeMillis() +
        "\nuser.name       = " + System.getProperty("user.name") +
        "\nuser.dir        = " + System.getProperty("user.dir") +
        "\nuser.home       = " + System.getProperty("user.home") +
        "\njava.version    = " + System.getProperty("java.version") +
        "\njava.home       = " + System.getProperty("java.home") +
        "\nStarted Since   = "
        + java.time.LocalDateTime
            .ofInstant(java.time.Instant.ofEpochMilli(rmxb.getStartTime()), java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSS"))
        + " | " + rmxb.getStartTime() +
        "\nJVM Name        = " + rmxb.getName() +
        "\nJVM Cmdline     = " + String.join(" ", rmxb.getInputArguments()) +
        "\nJVM Memory      = " + (mmxb.getHeapMemoryUsage().getUsed() / 1024 / 1024) + " MB used / "
        + (mmxb.getHeapMemoryUsage().getMax() / 1024 / 1024) + " MB in total" +
        "\nJava PID         = " + rmxb.getPid() +
        /* 操作系统信息 */
        "\n==> OS Info" +
        "\nos.name         = " + System.getProperty("os.name") +
        "\nos.version      = " + System.getProperty("os.version") +
        "\nos.arch         = " + System.getProperty("os.arch") +
        /* 设备信息 */
        "\n==> Hardware Info" +
        "\nCPU             = " + cpu.getProcessorIdentifier().getName() +
        "\nCPU Core        = " + cpu.getLogicalProcessorCount() + "x Logical / " + cpu.getPhysicalProcessorCount()
        + "x Physical" +
        "\nMemory          = " + (mem.getAvailable() / 1024 / 1024) + " MB free / " + (mem.getTotal() / 1024 / 1024)
        + " MB in total" +
        "\nGPUs            = " + gpu_listed +
        /* 路径与编码 */
        "\n==> File System" +
        "\njava.io.tmpdir  = " + System.getProperty("java.io.tmpdir") +
        "\nfile.encoding   = " + System.getProperty("file.encoding") +
        "\nfile.separator  = " + System.getProperty("file.separator") +
        /* 环境变量（常用） */
        "\n==> Env" +
        "\n$JAVA_HOME      = " + System.getenv("JAVA_HOME") +
        "\n$PATH           = " + System.getenv("PATH") +
        "\n$CLASSPATH      = " + System.getenv("CLASSPATH");
  }

  private static final Set<String> SKIP_PACKAGES = Set.of(// 栈追踪白名单
      "java.lang.", // 跳过 Thread 等 JDK 基础类
      "sun.reflect.", // 跳过反射内部实现
      "java.lang.reflect.", // 跳过反射 API
      "com.github.streackmc.StreackLib.self.", // StreackLib自身
      "com.github.streackmc.StreackLib.utils.",
      "com.github.streackmc.StreackLib.bukkit.",
      "com.github.streackmc.StreackLib.fabric.",
      "com.github.streackmc.StreackLib.forge.",
      "com.github.streackmc.StreackLib.neoforge.",
      "com.github.streackmc.StreackLib.");
  private static final Set<String> SKIP_PACKAGES_WITHOUT_STREACKLIB = Set.of(// 栈追踪白名单，不含StreackLib
      "java.lang.", // 跳过 Thread 等 JDK 基础类
      "sun.reflect.", // 跳过反射内部实现
      "java.lang.reflect.", // 跳过反射 API
      "com.github.streackmc.StreackLib.self.manager" // 不允许追踪到self.manager中，否则总是输出getCaller()
      );
  private static final StackWalker WALKER = StackWalker.getInstance();
  
  /**
   * 返回第一个非反射的调用者
   * @param filter 过滤模式，默认不含StreackLib，为"allowInternal"时则可以包含。通常没有意义。
   * @return 以列表格式存储，索引对应：
   *         <p>
   *         0: ClassName:method@line
   *         <p>
   *         1: SimpleClassName:method@line
   *         <p>
   *         2: ClassName
   *         <p>
   *         3: SimpleClassName
   *         <p>
   *         4: method
   *         <p>
   *         5: line ("-1"表示InternalCall)
   * @see #getCallerMethod
   * @since 0.4.4
   */
  @Internal
  public static List<String> getCaller(@Nullable String filter) {
    final String filterFinal;
    if (filter == null) {
      filterFinal = "";
    } else {
      filterFinal = filter;
    }
    return WALKER.walk(frames -> frames
        .skip(1)
        .filter(frame -> {
          switch (filterFinal.toLowerCase()) {
            case "allowinternal":
              return SKIP_PACKAGES_WITHOUT_STREACKLIB.stream().noneMatch(pkg -> frame.getClassName().startsWith(pkg));
            default:
              return SKIP_PACKAGES.stream().noneMatch(pkg -> frame.getClassName().startsWith(pkg));
          }
        })
        .findFirst()
        .map(frame -> {
          String fullName = frame.getClassName();
          String simpleName = fullName.contains(".") ? fullName.substring(fullName.lastIndexOf('.') + 1) : fullName;
          String method = frame.getMethodName();
          String line = frame.getLineNumber() > 0 ? String.valueOf(frame.getLineNumber()) : "-1";
          return List.of(
              fullName + ":" + method + "@" + line,
              simpleName + ":" + method + "@" + line,
              fullName,
              simpleName,
              method,
              line);
        })
        .orElse(List.of(
            "StreackLib-InternalCall:method@-1",
            "InternalCall:method@-1",
            "StreackLib-InternalCall",
            "InternalCall",
            "method",
            "-1")));
  }
  /**
   * 提供给getCaller的过滤器常量
   * @since 0.4.4
   */
  @Internal
  public class getCallerMethod {
    final String DEFAULT = "";
    final String ALLOW_INTERNAL = "allowInternal";
  }
}
