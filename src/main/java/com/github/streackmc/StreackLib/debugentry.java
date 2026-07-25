package com.github.streackmc.StreackLib;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.logging.log4j.util.InternalApi;
import org.ini4j.Ini;
import org.jetbrains.annotations.VisibleForTesting;
import org.yaml.snakeyaml.Yaml;

import com.github.streackmc.StreackLib.self.manager;
import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.SMail;
import com.github.streackmc.StreackLib.types.SDatabase.SdbAction;
import com.github.streackmc.StreackLib.types.SDatabase.SdbDataEntry;
import com.github.streackmc.StreackLib.types.SDatabase.SdbDatabase;
import com.github.streackmc.StreackLib.types.SDatabase.SdbEnums;
import com.github.streackmc.StreackLib.types.SDatabase.SdbStatement;
import com.github.streackmc.StreackLib.utils.MCColor;
import com.github.streackmc.StreackLib.utils.SEventCentral;
import com.github.streackmc.StreackLib.utils.SFile;
import com.google.gson.GsonBuilder;
import com.moandjiezana.toml.TomlWriter;

/**
 * 交互式自动化测试入口。启动后列出可选测试单元，输入对应缩写执行。
 *
 * <p>缩写规则：<b>1-9 a-z A-Z</b>，每个测试单元一个唯一缩写。空输入 = 全部测试。
 *
 * @author KimiAI 编写框架与测试体
 * @author kdxiaoyi 审计与交互式重构
 */
@InternalApi
@VisibleForTesting
public class debugentry {

  // ==========================================
  // TestUnit 接口
  // ==========================================

  /** 一个可注册的测试单元：唯一缩写 + 名称 + 执行体 */
  private interface TestUnit {
    /** @return 用于菜单选择的缩写（1-9 a-z A-Z） */
    String key();
    /** @return 显示名称 */
    String name();
    /** @throws Exception 测试内部异常由调用者捕获并报告 */
    void run() throws Exception;
  }

  // ==========================================
  // main
  // ==========================================

  @InternalApi
  @VisibleForTesting
  public static void main(String[] args) {
    // ---------- 初始化 ENV ----------
    try {
      StreackLib.ENV.conf = new SConfig("debug: true", "yaml", "c");
      StreackLib.ENV.defaultConf = StreackLib.ENV.conf;
      StreackLib.ENV.buildConf = new SConfig("version: 0.0.1", "yaml", "v");
    } catch (Exception e) {
      err("初始化失败！" + e.getLocalizedMessage());
      e.printStackTrace();
      return;
    }

    // ---------- 注册测试单元 ----------
    List<TestUnit> tests = new ArrayList<>();
    tests.add(unit("1", "Basic Info + Logger",    debugentry::test_Basic));
    tests.add(unit("2", "SEventCentral",           debugentry::test_SEvent));
    tests.add(unit("3", "SConfig",                 debugentry::test_SConfig));
    tests.add(unit("4", "SFile",                   debugentry::test_SFile));
    tests.add(unit("5", "MCColor",                 debugentry::test_MCColor));
    tests.add(unit("6", "SMail",                   debugentry::test_SMail));
    tests.add(unit("7", "SDatabase",               debugentry::test_SDatabase));

    // ---------- 交互选择 ----------
    try (Scanner scanner = new Scanner(System.in)) {
      while (true) {
        info("");
        info("========== 选择测试 ==========");
        info("   [0] 全部测试");
        for (TestUnit t : tests) {
          info(String.format("   [%s] %s", t.key(), t.name()));
        }
        info("   [q] 退出");
        info("------------------------------");
        System.out.print  ("输入测试编号（空=全部）> ");
        System.out.flush();

        String line = scanner.nextLine().trim();
        if (line.isEmpty()) {
          // 全部测试
          runTests(tests);
          break;
        }
        if ("q".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
          info("退出。");
          break;
        }

        // 按字符匹配测试单元
        List<TestUnit> selected = new ArrayList<>();
        for (char ch : line.toCharArray()) {
          boolean found = false;
          for (TestUnit t : tests) {
            if (t.key().indexOf(ch) >= 0) {
              selected.add(t);
              found = true;
              break;
            }
          }
          if (!found && ch != ' ' && ch != ',') {
            warn("未知测试缩写: '" + ch + "'");
          }
        }
        if (selected.isEmpty()) {
          warn("未匹配到任何测试，请重新输入。");
          continue;
        }
        runTests(selected);
        break;
      }
    }

    info("");
    info("Press Enter to exit...");
    try { System.in.read(); } catch (Exception ignored) {}
  }

  // ==========================================
  // 工具
  // ==========================================

  private static TestUnit unit(String key, String name, ThrowingRunnable body) {
    return new TestUnit() {
      @Override public String key()  { return key; }
      @Override public String name() { return name; }
      @Override public void run() throws Exception { body.run(); }
    };
  }

  @FunctionalInterface
  private interface ThrowingRunnable { void run() throws Exception; }

  private static void runTests(List<TestUnit> selected) {
    info(">>>>>>>>>> TEST STARTED <<<<<<<<<<");
    for (TestUnit t : selected) {
      info("========== " + t.name() + " ==========");
      try {
        t.run();
      } catch (Exception e) {
        err("[!] " + t.name() + " 测试失败: " + e.getLocalizedMessage());
        e.printStackTrace();
      }
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

  // ==========================================
  // TestUnit: Basic Info + Logger
  // ==========================================

  private static void test_Basic() throws Exception {
    info("info from java");
    warn("warn from java");
    err("severe from java");
    info("\n" + manager.generateDebugInfo());
  }

  // ==========================================
  // TestUnit: SEventCentral
  // ==========================================

  @SuppressWarnings("deprecation")
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
    SEventCentral.broadcastEvent(STR, (Long) null).broadcast();
  }

  // ==========================================
  // TestUnit: SConfig
  // ==========================================

  private static void test_SConfig() throws Exception {
    // 注册配置文件变更事件
    SEventCentral.addEventListener(SConfig.EVENTS.CHANGED, event -> {
      info(String.format("ID为 %s 的配置文件变更", event.CALLER_ID));
    });

    String basePath = "./target/debugCI-tmp/SConfig";
    File dir = new File(basePath, "sconfig-test");
    dir.mkdirs();

    // 1) 生成 4 种格式文件
    File json = new File(dir, "test.json");
    File yaml = new File(dir, "test.yaml");
    File toml = new File(dir, "test.toml");
    File ini  = new File(dir, "test.ini");

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

    // NBT 准备
    File nbtSrc = new File("./mcserver/world/level.dat");
    File nbtTestFile = new File(dir, "level.dat");
    try {
      if (nbtSrc.exists()) {
        Files.copy(nbtSrc.toPath(), nbtTestFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        info("复制 NBT 测试文件到: " + nbtTestFile.getAbsolutePath());
      } else {
        if (!nbtTestFile.exists()) Files.createFile(nbtTestFile.toPath());
        info("未找到源 NBT，已创建空测试文件: " + nbtTestFile.getAbsolutePath());
      }
    } catch (Exception e) {
      warn("准备 NBT 测试文件失败: " + e.getMessage());
    }

    // 2) 分别对每种格式执行相同测试
    for (File f : new File[] { json, yaml, toml, ini }) {
      String ext = f.getName().substring(f.getName().lastIndexOf('.') + 1);
      info("==> " + ext);
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

      // 热加载
      cfg.setAutoReload(true);
      Thread.sleep(100);

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
          cfg.setAutoReload(false);
          new TomlWriter().write(hot, new FileWriter(f));
          cfg.setAutoReload(true);
          break;
        case "ini":
          Ini iniHot = new Ini();
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

      Thread.sleep(2000);
      info("hot-reload:str      = " + cfg.getString("str"));
      info("hot-reload:newInt   = " + cfg.getInt("newInt"));
      info("hot-reload:sec.k1   = " + cfg.getString("sec.k1"));
    }

    info("==> all config formats done.");

    // ----- NBT 测试 -----
    try {
      info("==> nbt");
      if (!nbtTestFile.exists()) {
        warn("NBT 测试文件不存在");
      } else {
        SConfig ncfg = new SConfig(nbtTestFile, SConfig.TYPES.NBT);
        info("nbt: root=" + ncfg.getRootName());
        info("nbt: Data.version=" + ncfg.getInt("Data.version"));
        info("nbt: Data.LevelName=" + ncfg.getString("Data.LevelName"));
        info("nbt: Data.GameType=" + ncfg.getInt("Data.GameType"));
        info("nbt: Data.allowCommands=" + ncfg.getBoolean("Data.allowCommands"));
        info("nbt: topKeys=" + ncfg.getRawData().keySet());
        try {
          ncfg.putString("Test.WriteMarker", "written-by-nbt-test");
          ncfg.reload();
          info("nbt: reload 后 Test.WriteMarker=" + ncfg.getString("Test.WriteMarker"));
        } catch (Exception ew) {
          warn("nbt: 写入测试失败: " + ew.getMessage());
        }
      }
    } catch (Exception e) {
      warn("NBT 测试失败: " + e.getMessage());
    }

    // ----- SNBT 测试 -----
    try {
      info("==> snbt");
      File snbtFile = new File(dir, "test.snbt");
      String sampleSNBT = "{Data:{LevelName:\"snbt-test\",version:42,GameType:1,allowCommands:1b}}";
      try {
        Files.write(snbtFile.toPath(), sampleSNBT.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
      } catch (Exception e2) {
        warn("snbt: 无法写入示例文件: " + e2.getMessage());
      }
      if (snbtFile.exists()) {
        SConfig sc = new SConfig(snbtFile, SConfig.TYPES.SNBT);
        info("snbt: root=" + sc.getRootName());
        info("snbt: Data.version=" + sc.getInt("Data.version"));
        info("snbt: Data.LevelName=" + sc.getString("Data.LevelName"));
        try {
          sc.putString("Data.LevelName", "snbt-modified");
          sc.putInt("Data.version", 9001);
          sc.reload();
          info("snbt: reload 后 LevelName=" + sc.getString("Data.LevelName")
              + ", version=" + sc.getInt("Data.version"));
        } catch (Exception es) {
          warn("snbt: 写入测试失败: " + es.getMessage());
        }
      }
    } catch (Exception e) {
      warn("SNBT 测试失败: " + e.getMessage());
    }
  }

  // ==========================================
  // TestUnit: SFile
  // ==========================================

  private static void test_SFile() throws Exception {
    File testDir = new File("./target/debugCI-tmp/SFileTest");
    File testFile = new File(testDir, "testFile.txt");

    if (SFile.mkdir(testDir.getAbsolutePath())) {
      info("Directory created: " + testDir.getAbsolutePath());
    } else {
      warn("Failed to create directory: " + testDir.getAbsolutePath());
    }

    if (SFile.touch(testFile)) info("File created: " + testFile.getAbsolutePath());
    else warn("Failed to create file: " + testFile.getAbsolutePath());

    if (SFile.copy(testFile, new File(testDir, "testFileCopy.txt"))) info("File copied");
    else warn("Failed to copy");

    if (SFile.copyJoin(testFile, new File(testDir, "testFileCopy.txt"))) info("File copied [JoinMode]");
    else warn("Failed to copy [JoinMode]");

    if (SFile.mv(testFile, new File(testDir, "testFileMoved.txt"))) info("File moved");
    else warn("Failed to move");

    if (SFile.ren(new File(testDir, "testFileCopy.txt"), "testFileCopy-Renamed.txt")) info("File renamed");
    else warn("Failed to rename");

    if (SFile.rm(new File(testDir, "testFileCopy-Renamed.txt"))) info("File deleted");
    else warn("Failed to delete file");

    if (SFile.rm(testDir)) info("Directory deleted: " + testDir.getAbsolutePath());
    else warn("Failed to delete directory");
  }

  // ==========================================
  // TestUnit: MCColor
  // ==========================================

  private static void test_MCColor() throws Exception {
    info("[&aHello&bWorld] 消除     → " + MCColor.remove("[&aHello&bWorld]"));
    info("[&aHello&bWorld] 转义     → " + MCColor.parse("[&aHello&bWorld]"));
    info("[§aHello§bWorld] 转HTML   → " + MCColor.toHtml("[§aHello§bWorld]"));
    info("[&#ffcd1aH&#ffbb29e&#ffaa37l&#ff9846l&#ff8654o&#ff7563W&#ff6371o&#ff5180r&#ff408el&#ff2e9dd] 转义 → "
        + MCColor.toHtml("[&#ffcd1aH&#ffbb29e&#ffaa37l&#ff9846l&#ff8654o&#ff7563W&#ff6371o&#ff5180r&#ff408el&#ff2e9dd]"));
    info("[§#ffcd1aH§#ffbb29e§#ffaa37l§#ff9846l§#ff8654o§#ff7563W§#ff6371o§#ff5180r§#ff408el§#ff2e9dd] 转HTML → "
        + MCColor.toHtml("[§#ffcd1aH§#ffbb29e§#ffaa37l§#ff9846l§#ff8654o§#ff7563W§#ff6371o§#ff5180r§#ff408el§#ff2e9dd]"));
  }

  // ==========================================
  // TestUnit: SMail
  // ==========================================

  private static void test_SMail() throws Exception {
    File secretFile = new File("./email-secret.yml");
    boolean hasSecrets = secretFile.exists() && secretFile.isFile();

    if (hasSecrets) {
      info("发现 email-secret.yml，正在加载邮件凭据…");
      SConfig secretConf = new SConfig(secretFile, SConfig.TYPES.YAML);
      Map<String, Object> emails = secretConf.getSection("emails");
      if (emails != null && !emails.isEmpty()) {
        StreackLib.ENV.conf.putSection("emails", emails);
        info(String.format("已加载 %d 个邮件 Profile", emails.size()));
      }
    } else {
      warn("未找到 email-secret.yml，跳过真实发送测试");
      warn("提示: 复制 email-secret.temple.yml 为 email-secret.yml 并填入凭据即可启用");
    }

    // 构建测试（不发送）
    info("--- SMail[构建测试] ---");
    SMail.builder("profile_smtp")
        .to("test@example.com")
        .cc("cc@example.com")
        .bcc("bcc@example.com")
        .subject("SMail 单元测试")
        .body("<h1>测试</h1><p>这是 SMail 的构建测试</p>", true)
        .alternative("这是 SMail 的构建测试（纯文本降级）")
        .charset("UTF-8")
        .priority(1);
    info("Builder 构建完成");

    SConfig reuseConf = new SConfig("", "json", null);
    reuseConf.putString("subject", "复用配置测试");
    SMail.builder("profile_selfsign", reuseConf)
        .to("another@example.com")
        .body("纯文本测试", false);
    info("复用 SConfig 的 Builder 构建完成");

    if (!hasSecrets) return;

    // SMTP 发送
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
          warn("profile_smtp 的 mode 不是 smtp，跳过");
        }
      } catch (Exception e) {
        err("SMTP 发送失败: " + e.getLocalizedMessage());
      }
    }

    // SELFSIGN 发送
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
          warn("profile_selfsign 的 mode 不是 selfsign，跳过");
        }
      } catch (Exception e) {
        err("SELFSIGN 发送失败: " + e.getLocalizedMessage());
      }
    }
  }

  // ==========================================
  // TestUnit: SDatabase
  // ==========================================

  private static void test_SDatabase() throws Exception {
    // 设置测试用数据库 Profile
    info("--- SDatabase[配置] ---");
    File sqliteDir = new File("./target/debugCI-tmp/SDatabase");
    sqliteDir.mkdirs();
    File sqliteFile = new File(sqliteDir, "test.db");
    if (sqliteFile.exists()) sqliteFile.delete();

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

    info("SQLite: " + sqliteFile.getAbsolutePath());
    info("MySQL: 127.0.0.1:3306/test (user=test)");

    // ----- SQLite -----
    info("--- SDatabase[SQLite] ---");
    SdbDatabase db = new SdbDatabase("sdbtest");
    info("已连接 SQLite");

    db.newTable("players");
    info("已创建表: players");

    db.act("ALTER TABLE players ADD COLUMN name TEXT");
    db.act("ALTER TABLE players ADD COLUMN score INTEGER DEFAULT 0");
    info("已添加列: name, score");

    try (SdbAction action = db.act()) {
      action.apply("INSERT INTO players (name, score) VALUES ('Alice', 100)");
      action.apply("INSERT INTO players (name, score) VALUES ('Bob', 200)");
      action.apply("INSERT INTO players (name, score) VALUES ('Charlie', 150)");
      action.commit();
    }
    info("已插入 3 行");

    SdbDataEntry result = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
        ctx.table("players").filter(new SdbStatement().larger("score", "120")));
    info("查询 score>120: rows=" + result.InfluencedLines);
    for (SConfig row : result.ResultLines) {
      info("  结果: name=" + row.getString("name") + ", score=" + row.getInt("score"));
    }

    db.act("UPDATE players SET score=250 WHERE name='Alice'");
    SdbDataEntry updated = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
        ctx.table("players").filter(new SdbStatement().equal("name", "Alice")));
    info("更新 Alice.score=250 → score=" + updated.ResultLines.get(0).getInt("score"));

    db.act("DELETE FROM players WHERE name='Charlie'");
    SdbDataEntry all = db.act(SdbEnums.ACTION_TYPE.SELECT, ctx -> ctx.table("players"));
    info("删除 Charlie 后总行数: " + all.InfluencedLines);

    db.moveTable("players", "users");
    info("重命名: players → users");
    db.moveTable("users", null);
    info("删除表: users");
    info("SDatabase SQLite 测试通过");

    // ----- MySQL -----
    info("--- SDatabase[MySQL] ---");
    try {
      SdbDatabase mysqlDb = new SdbDatabase("sdbtest_mysql");
      info("已连接 MySQL");
      try { mysqlDb.act("DROP TABLE IF EXISTS test_users"); } catch (Exception ignored) {}
      mysqlDb.newTable("test_users");
      mysqlDb.act("ALTER TABLE test_users ADD COLUMN name VARCHAR(50)");
      mysqlDb.act("ALTER TABLE test_users ADD COLUMN age INT DEFAULT 0");
      mysqlDb.act("ALTER TABLE test_users ADD COLUMN active BOOLEAN DEFAULT TRUE");
      info("已创建表: test_users (id, name, age, active)");

      try (SdbAction action = mysqlDb.act()) {
        action.apply("INSERT INTO test_users (name, age, active) VALUES ('MySQL-User1', 25, TRUE)");
        action.apply("INSERT INTO test_users (name, age, active) VALUES ('MySQL-User2', 30, FALSE)");
        action.commit();
      }
      info("已插入 2 行");

      SdbDataEntry mr = mysqlDb.act(SdbEnums.ACTION_TYPE.SELECT, ctx ->
          ctx.table("test_users")
             .filter(new SdbStatement().equal("active", "1"))
             .limit(10));
      info("查询 active=1: rows=" + mr.InfluencedLines);
      for (SConfig row : mr.ResultLines) {
        info("  结果: name=" + row.getString("name")
            + ", age=" + row.getInt("age")
            + ", active=" + row.getBoolean("active"));
      }

      mysqlDb.act("DROP TABLE IF EXISTS test_users");
      info("已清理 test_users");
      info("SDatabase MySQL 测试通过");
    } catch (Exception e) {
      warn("MySQL 连接失败，跳过 MySQL 测试: " + e.getLocalizedMessage());
    }
  }

  // ==========================================
  // 遗留兼容
  // ==========================================

  /** @deprecated 请使用 StreackLib 中的同名方法 */
  @Deprecated
  public static boolean isDebugMode() {
    return StreackLib.ENV.conf.getBoolean("debug", false);
  }
}
