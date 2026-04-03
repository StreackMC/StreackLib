package com.github.streackmc.StreackLib;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.util.InternalApi;
import org.ini4j.Ini;
import org.jetbrains.annotations.VisibleForTesting;
import org.yaml.snakeyaml.Yaml;

import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.self.manager;
import com.github.streackmc.StreackLib.utils.MCColor;
import com.github.streackmc.StreackLib.utils.SConfig;
import com.github.streackmc.StreackLib.utils.SEventCentral;
import com.github.streackmc.StreackLib.utils.SFile;
import com.google.gson.GsonBuilder;
import com.moandjiezana.toml.TomlWriter;

/**
 * 自动化快速测试一些不需要MC服务器环境也能运行的模块
 * 
 * @author KimiAI 编写框架
 * @author kdxiaoyi 审计
 */
@InternalApi
@VisibleForTesting
public class debugentry {

  @InternalApi
  @VisibleForTesting
  public static void main(String[] args) {
    try {
      StreackLib.ENV.conf = new SConfig("debug: true", "yaml", "c");
      StreackLib.ENV.defaultConf = StreackLib.ENV.conf;
      StreackLib.ENV.buildConf = new SConfig("version: 0.0.1", "yaml", "v");
    } catch (Exception e) {
      err("无法初始化测试！" + e.getLocalizedMessage());
      e.printStackTrace();
    }

    info(">>>>>>>>>> TEST STARTED <<<<<<<<<<");
    info("========== Basic Info ==========");
    info("\n" + manager.generateDebugInfo());
    info("======= logger.java =======");
    try {
      logger.info("info from logger.java");
      logger.warn("warn from logger.java");
      logger.severe("severe from logger.java");
      logger.debug("debug from logger.java");
    } catch (Exception e) {
      err("[!] Caught Error @[ebugentry.test/logger] :" + e.getLocalizedMessage());
      e.printStackTrace();
    }
    info("======= SEventCentral.java =======");
    try {
      test_SEvent();
    } catch (Exception e) {
      err("[!] Caught Error @[ebugentry.test/SEventCentral] :" + e.getLocalizedMessage());
      e.printStackTrace();
    }
    info("========== SConfig.java ==========");
    try {
      SEventCentral.addEventListener(SConfig.EVENTS.CHANGED, event -> {
        info(String.format("ID为 %s 的配置文件变更", event.CALLER_ID));
      });
      test_SConfig("./target/debugCI-tmp/SConfig");
    } catch (Exception e) {
      err("[!] Caught Error @[ebugentry.test/SConfig] :" + e.getLocalizedMessage());
      e.printStackTrace();
    }
    info("========== SFile.java ==========");
    try {
      test_SFile(new File("./target/debugCI-tmp/SFileTest"));
    } catch (Exception e) {
      err("[!] Caught Error @[debugentry.test/SFile] :" + e.getLocalizedMessage());
      e.printStackTrace();
    }
    info("========== MCColor.java ==========");
    try {
      test_MCColor();
    } catch (Exception e) {
      err("[!] Caught Error @[debugentry.test/MCColor] :" + e.getLocalizedMessage());
      e.printStackTrace();
    }
    info(">>>>>>>>>> TEST DONE <<<<<<<<<<");
    info("Press Enter to exit...");
    try {
      System.in.read();
    } catch (Exception ignored) {
    }
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

  private static void test_SFile(File testDir) throws Exception {
    File testFile = new File(testDir, "testFile.txt");

    if (SFile.mkdir(testDir.getAbsolutePath())) {
      info("Directory created: " + testDir.getAbsolutePath());
    } else {
      warn("Failed to create directory: " + testDir.getAbsolutePath());
    }

    if (SFile.touch(testFile)) {
      info("File created: " + testFile.getAbsolutePath());
    } else {
      warn("Failed to create file: " + testFile.getAbsolutePath());
    }

    if (SFile.copy(testFile, new File(testDir, "testFileCopy.txt"))) {
      info("File copied: " + testFile.getAbsolutePath());
    } else {
      warn("Failed to copy the file: " + testFile.getAbsolutePath());
    }

    if (SFile.copyJoin(testFile, new File(testDir, "testFileCopy.txt"))) {
      info("File copied [JoinMode]: " + testFile.getAbsolutePath());
    } else {
      warn("Failed to copy [JoinMode] the file: " + testFile.getAbsolutePath());
    }

    if (SFile.mv(testFile, new File(testDir, "testFileMoved.txt"))) {
      info("File moved: " + testFile.getAbsolutePath());
    } else {
      warn("Failed to move the file: " + testFile.getAbsolutePath());
    }

    if (SFile.ren(new File(testDir, "testFileCopy.txt"), "testFileCopy-Renamed.txt")) {
      info("File renamed: " + testFile.getAbsolutePath());
    } else {
      warn("Failed to rename the file: " + testFile.getAbsolutePath());
    }

    if (SFile.rm(new File(testDir, "testFileCopy-Renamed.txt"))) {
      info("File deleted: " + testFile.getAbsolutePath());
    } else {
      warn("Failed to delete file: " + testFile.getAbsolutePath());
    }

    if (SFile.rm(testDir)) {
      info("Directory deleted: " + testDir.getAbsolutePath());
    } else {
      warn("Failed to delete directory: " + testDir.getAbsolutePath());
    }
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
      Thread.sleep(100); // 等待 WatchService 就绪

      // 修改文件内容（保留原有字段，只修改 str）
      Map<String, Object> hot = new LinkedHashMap<>();
      hot.put("str", "hot");
      hot.put("num", 123);
      hot.put("dbl", 3.14);
      hot.put("bool", true);
      hot.put("list", Arrays.asList("a", "b", "c"));
      Map<String, Object> sec = new LinkedHashMap<>();
      sec.put("k1", "v1");
      sec.put("k2", 42);
      hot.put("sec", sec);
      // 保留之前 put 的新字段
      hot.put("newStr", "world");
      hot.put("newInt", 999);
      hot.put("newDbl", 2.718);
      hot.put("newBool", false);
      hot.put("newList", Arrays.asList("x", "y", "z"));
      Map<String, Object> newSec = new LinkedHashMap<>();
      newSec.put("a", "1");
      newSec.put("b", 2);
      hot.put("newSec", newSec);

      switch (ext) {
        case "json":
          Files.write(f.toPath(),
              new GsonBuilder().setPrettyPrinting().create().toJson(hot).getBytes(StandardCharsets.UTF_8),
              StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
          break;
        case "yaml":
          Files.write(f.toPath(), new Yaml().dump(hot).getBytes(StandardCharsets.UTF_8),
              StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
          break;
        case "toml":
          // 先停止监听避免自己写入触发重载冲突
          cfg.stopAutoReload();
          new TomlWriter().write(hot, new FileWriter(f));
          cfg.startAutoReload();
          break;
        case "ini":
          Ini iniHot = new Ini();
          // INI 需要扁平化处理，或者使用你现有的 putSection 逻辑
          iniHot.add("keys").put("str", "hot");
          iniHot.add("keys").put("num", "123");
          iniHot.add("keys").put("dbl", "3.14");
          iniHot.add("keys").put("bool", "true");
          iniHot.add("keys").put("list", "[\"a\", \"b\", \"c\"]");
          iniHot.add("sec").put("k1", "v1");
          iniHot.add("sec").put("k2", "42");
          iniHot.add("newSec").put("a", "1");
          iniHot.add("newSec").put("b", "2");
          iniHot.store(Files.newOutputStream(f.toPath(), StandardOpenOption.TRUNCATE_EXISTING));
          break;
      }

      // 等待 WatchService 检测文件变化（通常 1-2 秒内）
      Thread.sleep(2000);

      // 验证热加载是否生效：str 应该变成 "hot"，同时保留其他字段
      info("hot-reload:str      = " + cfg.getString("str")); // 期望 "hot"
      info("hot-reload:newInt   = " + cfg.getInt("newInt")); // 期望 999（保留）
      info("hot-reload:sec.k1   = " + cfg.getString("sec.k1")); // 期望 "v1"（保留）
      // cfg.stopAutoReload();
    }

    info("==> all tests done.");
  }
  
  private static void test_SEvent() throws Exception {
    final String STR = "test:test";
    @SuppressWarnings("unused")
    int Id = SEventCentral.addEventListener(STR, event -> {
      info("监听到测试事件，模式：永久。" + event.getTimestamp() + "\nCaller = " + event.getCaller());
    });
    SEventCentral.addWeakEventListener(STR, event -> {
      info("监听到测试事件，模式：临时。" + event.getTimestamp() + "\nCaller = " + event.getCaller());
    });
    info("正在触发事件……");
    SEventCentral.broadcastEvent(STR, null).broadcast();
  }
  
  private static void test_MCColor() throws Exception {
    info("[&aHello&bWorld] 消除     → " + MCColor.remove("[&aHello&bWorld]"));
    info("[&aHello&bWorld] 转义     → " + MCColor.parse("[&aHello&bWorld]"));
    info("[§aHello§bWorld] 转HTML   → " + MCColor.toHtml("[§aHello§bWorld]"));
    info("[&#ffcd1aH&#ffbb29e&#ffaa37l&#ff9846l&#ff8654o&#ff7563W&#ff6371o&#ff5180r&#ff408el&#ff2e9dd] 转义     → " + MCColor.toHtml("[&#ffcd1aH&#ffbb29e&#ffaa37l&#ff9846l&#ff8654o&#ff7563W&#ff6371o&#ff5180r&#ff408el&#ff2e9dd]"));
    info("[§#ffcd1aH§#ffbb29e§#ffaa37l§#ff9846l§#ff8654o§#ff7563W§#ff6371o§#ff5180r§#ff408el§#ff2e9dd] 转为HTML → " + MCColor.toHtml("[§#ffcd1aH§#ffbb29e§#ffaa37l§#ff9846l§#ff8654o§#ff7563W§#ff6371o§#ff5180r§#ff408el§#ff2e9dd]"));
  }

  /**
   * @see #StreackLib.isDebugMode()
   * @Deprecated 请使用StreackLib中的同名方法
   * @return 当前是否是调试模式
   */
  @Deprecated
  public static boolean isDebugMode() {
    return StreackLib.ENV.conf.getBoolean("debug", false);
  }
}
