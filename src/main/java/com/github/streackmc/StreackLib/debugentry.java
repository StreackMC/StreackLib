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

import com.github.streackmc.StreackLib.self.manager;
import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.SDatabase.SdbAction;
import com.github.streackmc.StreackLib.types.SDatabase.SdbActionContext;
import com.github.streackmc.StreackLib.types.SDatabase.SdbDataEntry;
import com.github.streackmc.StreackLib.types.SDatabase.SdbDatabase;
import com.github.streackmc.StreackLib.types.SDatabase.SdbEnums;
import com.github.streackmc.StreackLib.types.SDatabase.SdbStatement;
import com.github.streackmc.StreackLib.types.SMail;
import com.github.streackmc.StreackLib.utils.MCColor;
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
    info("======= java =======");
    try {
      info("info from java");
      warn("warn from java");
      err("severe from java");
    } catch (Exception e) {
      err("[!] Caught Error @[ebugentry.test/logger] :" + e.getLocalizedMessage());
      e.printStackTrace();
    }
    // NBT 测试已合并进下面的 test_SConfig 方法，统一在同一测试目录执行
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
      test_SConfig_NBT();
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
    info("========== SMail.java ==========");
    try {
      test_SMail();
    } catch (Exception e) {
      err("[!] Caught Error @[debugentry.test/SMail] :" + e.getLocalizedMessage());
      e.printStackTrace();
    }
    info("========== SDatabase ==========");
    try {
      test_SDatabase();
    } catch (Exception e) {
      err("[!] Caught Error @[ebugentry.test/SDatabase] :" + e.getLocalizedMessage());
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

  /**
   * 测试 SConfig 对 NBT (Minecraft level.dat) 格式的读取能力。
   * 文件路径为 ./mcserver/world/level.dat，不强制存在。
   */
  private static void test_SConfig_NBT() {
    info("======= SConfig[NBT] =======");
    File src = new File("./mcserver/world/level.dat");
    File dir = new File("./target/debugCI-tmp/SConfig");
    dir.mkdirs();
    File testFile = new File(dir, "level.dat");

    try {
      if (src.exists()) {
        // 如果源存在，复制到测试目录
        try {
          java.nio.file.Files.copy(src.toPath(), testFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          info("复制 NBT 测试文件到: " + testFile.getAbsolutePath());
        } catch (Exception e) {
          warn("复制 NBT 文件失败，尝试使用原文件: " + e.getMessage());
          // fallback to src
          testFile = src;
        }
      } else {
        // 源不存在，创建一个空文件以便测试读取逻辑的健壮性
        try {
          if (!testFile.exists()) {
            java.nio.file.Files.createFile(testFile.toPath());
            info("未找到源 NBT，已创建空测试文件: " + testFile.getAbsolutePath());
          }
        } catch (Exception e) {
          warn("无法创建测试 NBT 文件，尝试使用原路径作为回退: " + e.getMessage());
          testFile = src; // will likely not exist, but let SConfig handle it
        }
      }

      // 使用大端序 NBT 读取 (Java 版)
      SConfig nbtConfig = new SConfig(testFile, SConfig.TYPES.NBT);
      info("尝试加载 NBT 文件: " + testFile.getAbsolutePath());
      info("根标签名称: " + nbtConfig.getRootName());

      // 读取常用字段（基于 level.dat 的典型结构）
      // 版本信息通常位于 Data.version
      int version = nbtConfig.getInt("Data.version");
      info("世界版本 (Data.version): " + version);

      // 世界名称
      String levelName = nbtConfig.getString("Data.LevelName");
      info("世界名称 (Data.LevelName): " + levelName);

      // 游戏类型 (0=生存,1=创造,2=冒险,3=旁观)
      int gameType = nbtConfig.getInt("Data.GameType");
      info("游戏类型 (Data.GameType): " + gameType);

      // 是否允许命令
      boolean allowCommands = nbtConfig.getBoolean("Data.allowCommands");
      info("允许命令 (Data.allowCommands): " + allowCommands);

      // 展示原始数据概览（仅打印一级键名）
      Map<String, Object> raw = nbtConfig.getRawData();
      info("顶层键集合: " + raw.keySet());

      // 注意：NBT 后端可能未实现 flush；此处不进行写入测试
      info("NBT 读取测试完成（若文件为空则仅验证读取路径与错误处理）");
    } catch (Exception e) {
      warn("NBT 读取失败: " + e.getLocalizedMessage());
      e.printStackTrace();
    }
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

    // NBT: 将 mcserver/world/level.dat 复制到测试目录（或创建空文件），以便后续测试
    File nbtSrc = new File("./mcserver/world/level.dat");
    File nbtTestFile = new File(dir, "level.dat");
    try {
      if (nbtSrc.exists()) {
        Files.copy(nbtSrc.toPath(), nbtTestFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        info("复制 NBT 测试文件到: " + nbtTestFile.getAbsolutePath());
      } else {
        if (!nbtTestFile.exists()) {
          Files.createFile(nbtTestFile.toPath());
          info("未找到源 NBT，已创建空测试文件: " + nbtTestFile.getAbsolutePath());
        }
      }
    } catch (Exception e) {
      warn("准备 NBT 测试文件失败: " + e.getMessage());
    }

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
      cfg.setAutoReload(true);
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
          cfg.setAutoReload(false);
          new TomlWriter().write(hot, new FileWriter(f));
          cfg.setAutoReload(true);
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

    // ----- 对 NBT 执行一组读取测试 -----
    try {
      info("==> nbt");
      if (!nbtTestFile.exists()) {
        warn("NBT 测试文件不存在，跳过 NBT 读取测试: " + nbtTestFile.getAbsolutePath());
      } else {
        SConfig ncfg = new SConfig(nbtTestFile, SConfig.TYPES.NBT);
        info("nbt: root=" + ncfg.getRootName());
        info("nbt: Data.version=" + ncfg.getInt("Data.version"));
        info("nbt: Data.LevelName=" + ncfg.getString("Data.LevelName"));
        info("nbt: Data.GameType=" + ncfg.getInt("Data.GameType"));
        info("nbt: Data.allowCommands=" + ncfg.getBoolean("Data.allowCommands"));
        info("nbt: topKeys=" + ncfg.getRawData().keySet());
        // 尝试写入 NBT（如果后端支持写入）并验证
        try {
          ncfg.putString("Test.WriteMarker", "written-by-nbt-test");
          info("nbt: 写入标记 Test.WriteMarker=written-by-nbt-test");
          // reload 后再读取以确保写入已持久化
          ncfg.reload();
          info("nbt: reload 后 Test.WriteMarker=" + ncfg.getString("Test.WriteMarker"));
        } catch (Exception ew) {
          warn("nbt: 写入测试失败: " + ew.getMessage());
        }
      }
    } catch (Exception e) {
      warn("NBT 读取测试失败: " + e.getMessage());
      e.printStackTrace();
    }

    // ----- SNBT 读取与写入测试 -----
    try {
      info("==> snbt");
      File snbtFile = new File(dir, "test.snbt");
      String sampleSNBT = "{Data:{LevelName:\"snbt-test\",version:42,GameType:1,allowCommands:1b}}";
      try {
        Files.write(snbtFile.toPath(), sampleSNBT.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
        info("snbt: 写入示例文件: " + snbtFile.getAbsolutePath());
      } catch (Exception e2) {
        warn("snbt: 无法写入示例 SNBT 文件: " + e2.getMessage());
      }

      if (!snbtFile.exists()) {
        warn("snbt: 示例文件不存在，跳过 SNBT 测试: " + snbtFile.getAbsolutePath());
      } else {
        SConfig sc = new SConfig(snbtFile, SConfig.TYPES.SNBT);
        info("snbt: root=" + sc.getRootName());
        info("snbt: Data.version=" + sc.getInt("Data.version"));
        info("snbt: Data.LevelName=" + sc.getString("Data.LevelName"));

        // 尝试写入 SNBT
        try {
          sc.putString("Data.LevelName", "snbt-modified");
          sc.putInt("Data.version", 9001);
          info("snbt: 写入 Data.LevelName=snbt-modified, Data.version=9001");
          sc.reload();
          info("snbt: reload 后 Data.LevelName=" + sc.getString("Data.LevelName") + ", version=" + sc.getInt("Data.version"));
        } catch (Exception es) {
          warn("snbt: 写入测试失败: " + es.getMessage());
        }
      }
    } catch (Exception e) {
      warn("SNBT 测试失败: " + e.getMessage());
      e.printStackTrace();
    }
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

  private static void test_SMail() throws Exception {
    // ---------- 加载秘密凭据 ----------
    File secretFile = new File("./email-secret.yml");
    boolean hasSecrets = secretFile.exists() && secretFile.isFile();

    if (hasSecrets) {
      info("发现 email-secret.yml，正在加载邮件凭据…");
      SConfig secretConf = new SConfig(secretFile, SConfig.TYPES.YAML);
      // 合并到 ENV.conf 中
      Map<String, Object> emails = secretConf.getSection("emails");
      if (emails != null && !emails.isEmpty()) {
        StreackLib.ENV.conf.putSection("emails", emails);
        info(String.format("已加载 %d 个邮件 Profile", emails.size()));
      }
    } else {
      warn("未找到 email-secret.yml，跳过真实发送测试");
      warn("提示: 复制 email-secret.yml.example 为 email-secret.yml 并填入凭据即可启用");
    }

    // ---------- 测试 1：Builder 构建（不发送） ----------
    info("--- SMail[构建测试] ---");
    SMail.Builder builder = SMail.builder("profile_smtp");
    builder.to("test@example.com")
        .cc("cc@example.com")
        .bcc("bcc@example.com")
        .subject("SMail 单元测试")
        .body("<h1>测试</h1><p>这是 SMail 的构建测试</p>", true)
        .alternative("这是 SMail 的构建测试（纯文本降级）")
        .charset("UTF-8")
        .priority(1);
    info("Builder 构建完成");

    // 测试复用 SConfig 的 Builder
    SConfig reuseConf = new SConfig("", "json", null);
    reuseConf.putString("subject", "复用配置测试");
    SMail.Builder builderReuse = SMail.builder("profile_selfsign", reuseConf);
    builderReuse.to("another@example.com")
        .body("纯文本测试", false);
    info("复用 SConfig 的 Builder 构建完成");

    // ---------- 测试 2：按 Profile 加载配置 ----------
    info("--- SMail[Profile 配置解析] ---");
    if (hasSecrets) {
      for (String profileName : new String[] { "profile_smtp", "profile_selfsign" }) {
        Map<String, Object> profileRaw = StreackLib.ENV.conf.getSection("emails." + profileName);
        if (profileRaw != null && !profileRaw.isEmpty()) {
          info(String.format("Profile [%s] 已加载: mode=%s, from=%s",
              profileName,
              profileRaw.getOrDefault("mode", "?"),
              profileRaw.getOrDefault("from", "?")));
        } else {
          warn(String.format("Profile [%s] 未在 email-secret.yml 中定义", profileName));
        }
      }
    }

    // ---------- 测试 3：真实发送 ----------
    if (hasSecrets) {
      // ---- SMTP 发送 ----
      if (StreackLib.ENV.conf.getSection("emails.profile_smtp") != null) {
        info("--- SMail[SMTP 发送测试] ---");
        try {
          String mode = new SConfig(StreackLib.ENV.conf.getSection("emails.profile_smtp"), "yaml", ".yml")
              .getString("mode", "");
          if (mode.equalsIgnoreCase("smtp")) {
            SMail.builder("profile_smtp")
                .to(StreackLib.ENV.conf.getString("test.email_to", "bar@example.com"))
                .subject("SMail SMTP 测试")
                .body("<h1>SMTP 测试</h1><p>如果收到这封邮件，说明 SMTP 模式正常工作。</p>", true)
                .alternative("SMTP 测试 — 如果收到这封邮件，说明 SMTP 模式正常工作。")
                .build()
                .send();
            info("SMTP 发送完成（若未报错则成功）");
          } else {
            warn("profile_smtp 的 mode 不是 smtp，跳过 SMTP 发送测试");
          }
        } catch (Exception e) {
          err(String.format("SMTP 发送失败: %s", e.getLocalizedMessage()));
        }
      } else {
        warn("未配置 profile_smtp，跳过 SMTP 发送测试");
      }

      // ---- SELFSIGN 发送 ----
      if (StreackLib.ENV.conf.getSection("emails.profile_selfsign") != null) {
        info("--- SMail[SELFSIGN 发送测试] ---");
        try {
          String mode = new SConfig(StreackLib.ENV.conf.getSection("emails.profile_selfsign"), "yaml", ".yml")
              .getString("mode", "");
          if (mode.equalsIgnoreCase("selfsign")) {
            SMail.builder("profile_selfsign")
                .to("test@example.com")
                .subject("SMail DKIM 测试")
                .body("DKIM 自签名发送测试", false)
                .build()
                .send();
            info("SELFSIGN 发送完成（若未报错则成功）");
          } else {
            warn("profile_selfsign 的 mode 不是 selfsign，跳过 SELFSIGN 发送测试");
          }
        } catch (Exception e) {
          err(String.format("SELFSIGN 发送失败: %s", e.getLocalizedMessage()));
        }
      } else {
        warn("未配置 profile_selfsign，跳过 SELFSIGN 发送测试");
      }
    }

    info("SMail 测试完成");
  }

  private static void test_SDatabase() throws Exception {
    // ---------- 设置测试用数据库 Profile ----------
    info("--- SDatabase[配置] ---");
    File sqliteDir = new File("./target/debugCI-tmp/SDatabase");
    sqliteDir.mkdirs();
    File sqliteFile = new File(sqliteDir, "test.db");
    if (sqliteFile.exists()) sqliteFile.delete(); // 每次测试从空库开始

    Map<String, Object> sqliteProfile = new LinkedHashMap<>();
    sqliteProfile.put("mode", "sqlite");
    sqliteProfile.put("file", sqliteFile.getAbsolutePath());
    StreackLib.ENV.conf.putSection("databases.sdbtest", sqliteProfile);

    Map<String, Object> mysqlProfile = new LinkedHashMap<>();
    mysqlProfile.put("mode", "mysql");
    mysqlProfile.put("host", "127.0.0.1");
    mysqlProfile.put("port", 3306);
    mysqlProfile.put("database", "test");
    mysqlProfile.put("user", "test");
    mysqlProfile.put("password", "a");
    StreackLib.ENV.conf.putSection("databases.sdbtest_mysql", mysqlProfile);

    info("SQLite 数据库路径: " + sqliteFile.getAbsolutePath());
    info("MySQL 数据库: 127.0.0.1:3306/test (user=test)");

    // ---------- SQLite 测试 ----------
    test_SDatabase_SQLite();

    // ---------- MySQL 测试 ----------
    test_SDatabase_MySQL();

    info("SDatabase 测试完成");
  }

  private static void test_SDatabase_SQLite() throws Exception {
    info("--- SDatabase[SQLite] ---");
    SdbDatabase db = new SdbDatabase("sdbtest");
    info("已连接 SQLite");

    // 新建表
    db.newTable("players");
    info("已创建表: players");

    // 添加列（使用原始 SQL）
    db.act("ALTER TABLE players ADD COLUMN name TEXT");
    db.act("ALTER TABLE players ADD COLUMN score INTEGER DEFAULT 0");
    info("已添加列: name, score");

    // 插入数据（事务控制）
    try (SdbAction action = db.act()) {
      action.apply("INSERT INTO players (name, score) VALUES ('Alice', 100)");
      action.apply("INSERT INTO players (name, score) VALUES ('Bob', 200)");
      action.apply("INSERT INTO players (name, score) VALUES ('Charlie', 150)");
      action.commit();
    }
    info("已插入 3 行: Alice(100), Bob(200), Charlie(150)");

    // 查询 —— 语法糖
    SdbDataEntry result = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
        ctx.table("players")
           .filter(new SdbStatement().larger("score", "120")));
    info("查询 score>120: InfluencedLines=" + result.InfluencedLines);
    for (SConfig row : result.ResultLines) {
      info("  结果行: name=" + row.getString("name") + ", score=" + row.getInt("score"));
    }

    // 更新
    db.act("UPDATE players SET score=250 WHERE name='Alice'");
    SdbDataEntry updated = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
        ctx.table("players").filter(new SdbStatement().equal("name", "Alice")));
    info("更新 Alice.score=250 → 查询结果: name="
        + updated.ResultLines.get(0).getString("name")
        + ", score=" + updated.ResultLines.get(0).getInt("score"));

    // 删除
    db.act("DELETE FROM players WHERE name='Charlie'");
    SdbDataEntry allAfterDel = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
        ctx.table("players"));
    info("删除 Charlie 后总行数: " + allAfterDel.InfluencedLines);

    // 重命名表
    db.moveTable("players", "users");
    info("已将 players 重命名为 users");

    // 删除表
    db.moveTable("users", null);
    info("已删除表 users");

    info("SDatabase SQLite 测试通过");
  }

  private static void test_SDatabase_MySQL() throws Exception {
    info("--- SDatabase[MySQL] ---");

    SdbDatabase db;
    try {
      db = new SdbDatabase("sdbtest_mysql");
      info("已连接 MySQL");
    } catch (Exception e) {
      warn("MySQL 连接失败，跳过 MySQL 测试: " + e.getLocalizedMessage());
      return;
    }

    // 新建表
    try { db.act("DROP TABLE IF EXISTS test_users"); } catch (Exception ignored) {}
    db.newTable("test_users");
    db.act("ALTER TABLE test_users ADD COLUMN name VARCHAR(50)");
    db.act("ALTER TABLE test_users ADD COLUMN age INT DEFAULT 0");
    db.act("ALTER TABLE test_users ADD COLUMN active BOOLEAN DEFAULT TRUE");
    info("已创建表: test_users (id, name, age, active)");

    // 事务插入
    try (SdbAction action = db.act()) {
      action.apply("INSERT INTO test_users (name, age, active) VALUES ('MySQL-User1', 25, TRUE)");
      action.apply("INSERT INTO test_users (name, age, active) VALUES ('MySQL-User2', 30, FALSE)");
      action.commit();
    }
    info("已插入 2 行");

    // 断言树查询
    SdbDataEntry result = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
        ctx.table("test_users")
           .filter(new SdbStatement().equal("active", "1"))
           .limit(10));
    info("查询 active=1: InfluencedLines=" + result.InfluencedLines);
    for (SConfig row : result.ResultLines) {
      info("  结果行: name=" + row.getString("name")
          + ", age=" + row.getInt("age")
          + ", active=" + row.getBoolean("active"));
    }

    // 清理
    db.act("DROP TABLE IF EXISTS test_users");
    info("已清理 test_users");
    info("SDatabase MySQL 测试通过");
  }

  /**
   * @see #StreackLib.isDebugMode()
   * @deprecated 请使用StreackLib中的同名方法
   * @return 当前是否是调试模式
   */
  @Deprecated
  public static boolean isDebugMode() {
    return StreackLib.ENV.conf.getBoolean("debug", false);
  }
}
