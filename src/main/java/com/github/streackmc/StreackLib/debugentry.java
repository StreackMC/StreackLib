package com.github.streackmc.StreackLib;

import java.io.File;
import java.io.FileWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ForkJoinPool.ManagedBlocker;

import org.ini4j.Ini;
import org.yaml.snakeyaml.Yaml;

import com.github.streackmc.StreackLib.utils.SConfig;
import com.google.gson.Gson;
import com.moandjiezana.toml.TomlWriter;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * 自动化快速测试一些不需要MC服务器环境也能运行的模块
 * 
 * @author KimiAI 编写框架
 * @author kdxiaoyi 审核
 */
public class debugentry {

  /**
   * 生成当前环境信息
   * @return 环境信息
   */
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
    System.out.println(
        );

    /* Build */
    return
        /* 系统核心信息 */
        "==> Running Time Meta" +
        "\nuser.name       = " + System.getProperty("user.name") +
        "\nuser.dir        = " + System.getProperty("user.dir") +
        "\nuser.home       = " + System.getProperty("user.home") +
        "\njava.version    = " + System.getProperty("java.version") +
        "\njava.home       = " + System.getProperty("java.home") +
        "\nStarted Since   = " + java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(rmxb.getStartTime()),java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSS")) + " | " + rmxb.getStartTime() +
        "\nJVM Name        = " + rmxb.getName() +
        "\nJVM Cmdline     = " + String.join(" ",rmxb.getInputArguments()) +
        "\nJVM Memory      = " + (mmxb.getHeapMemoryUsage().getUsed() / 1024 / 1024) + " MB used / " + (mmxb.getHeapMemoryUsage().getMax() / 1024 / 1024) + " MB in total" +
        /* 操作系统信息 */
        "\n==> OS Info" +
        "\nos.name         = " + System.getProperty("os.name") +
        "\nos.version      = " + System.getProperty("os.version") +
        "\nos.arch         = " + System.getProperty("os.arch") +
        /* 设备信息 */
        "\n==> Hardware Info" +
        "\nCPU             = " + cpu.getProcessorIdentifier().getName() +
        "\nCPU Core        = " + cpu.getLogicalProcessorCount() + "x Logical / " + cpu.getPhysicalProcessorCount() + "x Physical" +
        "\nMemory          = " + (mem.getAvailable() / 1024 / 1024) + " MB free / "  + (mem.getTotal() / 1024 / 1024) + " MB in total" +
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

  public static void main(String[] args) {
    info(">>>>>>>>>> TEST STARTED <<<<<<<<<<");
    info("========== Basic Info ==========");
    info("\n" + generateDebugInfo());
    info("========== SConfig.java ==========");
    try {
      test_SConfig("./target/debugCI-tmp/SConfig");
    } catch (Exception e) {
      err("[!] Caught Error @[ebugentry.test/SConfig] :" + e.getMessage());
      e.printStackTrace();
    }
    info(">>>>>>>>>> TEST DONE <<<<<<<<<<");
  }

  private static void info(String txt) {
    System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "][INFO] " + txt);
  }

  private static void warn(String txt) {
    System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "][WARN] " + txt);
  }

  private static void err(String txt) {
    System.out.println("[" + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "][ERROR] " + txt);
  }

  private static void test_SConfig(String path) throws Exception {
    File dir = new File(path, "sconfig-test");
    dir.mkdirs();

    // 1) 生成 4 种格式文件
    File json = new File(dir, "test.json");
    File yaml = new File(dir, "test.yaml");
    File toml = new File(dir, "test.toml");
    File ini = new File(dir, "test.ini");

    // JSON
    Files.write(json.toPath(),
      ("{\n" +
        "  \"str\": \"hello\",\n" +
        "  \"num\": 123,\n" +
        "  \"dbl\": 3.14,\n" +
        "  \"bool\": true,\n" +
        "  \"list\": [\"a\", \"b\", \"c\"],\n" +
        "  \"sec\": { \"k1\": \"v1\", \"k2\": 42 }\n" +
        "}").getBytes(StandardCharsets.UTF_8));

    // YAML
    Files.write(yaml.toPath(),
      ("str: hello\n" +
        "num: 123\n" +
        "dbl: 3.14\n" +
        "bool: true\n" +
        "list: [a, b, c]\n" +
        "sec:\n" +
        "  k1: v1\n" +
        "  k2: 42\n").getBytes(StandardCharsets.UTF_8));

    // TOML
    Files.write(toml.toPath(),
      ("str = \"hello\"\n" +
        "num = 123\n" +
        "dbl = 3.14\n" +
        "bool = true\n" +
        "list = [\"a\", \"b\", \"c\"]\n" +
        "[sec]\n" +
        "k1 = \"v1\"\n" +
        "k2 = 42\n").getBytes(StandardCharsets.UTF_8));

    // INI
    Files.write(ini.toPath(),
      ("[keys]\n" +
          "str = hello\n" +
          "num = 123\n" +
          "dbl = 3.14\n" +
          "bool = true\n" +
          "list = [\"a\", \"b\", \"c\"]\n" +
          "[sec]\n" +
          "k1 = v1\n" +
          "k2 = 42\n").getBytes(StandardCharsets.UTF_8));

    // 2) 分别对每种格式执行相同测试
    for (File f : new File[] { json, yaml, toml, ini }) {
      String ext = f.getName().substring(f.getName().lastIndexOf('.') + 1);
      info("==> " + ext + "");
      SConfig cfg = new SConfig(f, ext);

      // 读
      info("getString(str)   = " + cfg.getString("str"));
      info("getInt(num)      = " + cfg.getInt("num"));
      info("getDouble(dbl)   = " + cfg.getDouble("dbl"));
      info("getBoolean(bool) = " + cfg.getBoolean("bool"));
      info("getListOfString  = " + cfg.getListOfString("list"));
      info("getSection(sec)  = " + cfg.getSection("sec"));
      info("getString(sec.k1)= " + cfg.getString("sec.k1"));

      // 写
      cfg.putString("newStr", "world");
      cfg.putInt("newInt", 999);
      cfg.putDouble("newDbl", 2.718);
      cfg.putBoolean("newBool", false);
      cfg.putListOfString("newList", Arrays.asList("x", "y", "z"));
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("a", "1");
      m.put("b", 2);
      cfg.putSection("newSec", m);
      info("put:newInt       = " + cfg.getInt("newInt"));

      // 删除
      cfg.remove("str");
      info("removed:str      = " + cfg.getString("str"));

      // 落盘再读
      cfg.reload();
      info("reloaded:newStr  = " + cfg.getString("newStr"));

      // 热加载：把刚才写入的内容再写回文件，触发检测
      cfg.startAutoReload();
      Map<String, Object> hot = new LinkedHashMap<>();
      hot.put("str", "hot");
      switch (ext) {
        case "json":
          Files.write(f.toPath(), new Gson().toJson(hot).getBytes(StandardCharsets.UTF_8),
              StandardOpenOption.TRUNCATE_EXISTING);
          break;
        case "yaml":
          Files.write(f.toPath(), new Yaml().dump(hot).getBytes(StandardCharsets.UTF_8),
              StandardOpenOption.TRUNCATE_EXISTING);
          break;
        case "toml":
          new TomlWriter().write(hot, new FileWriter(f));
          break;
        case "ini":
          Ini iniHot = new Ini();
          iniHot.add("str").put("str", "hot");
          iniHot.store(Files.newOutputStream(f.toPath(), StandardOpenOption.TRUNCATE_EXISTING));
          break;
      }
      Thread.sleep(1200);
      info("hot_reload:str   =" + cfg.getString("str"));
      cfg.stopAutoReload();

    }

    info("==> all tests done.");
  }
}
