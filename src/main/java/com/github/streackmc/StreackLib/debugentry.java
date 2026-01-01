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

import org.ini4j.Ini;
import org.yaml.snakeyaml.Yaml;

import com.github.streackmc.StreackLib.utils.SConfig;
import com.google.gson.Gson;
import com.moandjiezana.toml.TomlWriter;

/**
 * 自动化快速测试一些不需要MC服务器环境也能运行的模块
 * 
 * @author KimiAI 编写框架
 * @author kdxiaoyi 审核
 */
public class debugentry {

  public static void main(String[] args) {
    info(">>>>>>>>>> TEST STARTED <<<<<<<<<<");
    info("========== Basic Info ==========");
    // 系统核心信息
    info("==> Running Time Meta");
    info("user.name = " + System.getProperty("user.name"));
    info("user.dir = " + System.getProperty("user.dir"));
    info("user.home = " + System.getProperty("user.home"));
    info("java.version = " + System.getProperty("java.version"));
    info("java.home = " + System.getProperty("java.home"));
    // info("java.class.path = " + System.getProperty("java.class.path"));
    // 操作系统信息
    info("==> OS Info");
    info("os.name = " + System.getProperty("os.name"));
    info("os.version = " + System.getProperty("os.version"));
    info("os.arch = " + System.getProperty("os.arch"));
    // 路径与编码
    info("==> File System");
    info("java.io.tmpdir = " + System.getProperty("java.io.tmpdir"));
    info("file.encoding = " + System.getProperty("file.encoding"));
    info("file.separator = " + System.getProperty("file.separator"));
    // 环境变量（常用）
    info("==> Env");
    info("JAVA_HOME = " + System.getenv("JAVA_HOME"));
    info("PATH = " + System.getenv("PATH"));
    info("CLASSPATH = " + System.getenv("CLASSPATH"));
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
