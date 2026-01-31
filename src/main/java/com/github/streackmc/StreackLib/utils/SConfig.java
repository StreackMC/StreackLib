package com.github.streackmc.StreackLib.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.ini4j.Ini;
import org.ini4j.Profile;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;

/**
 * 高性能多格式（json/yaml/toml/xml/ini）配置中心。
 * 支持运行时热加载与严格类型读写。
 *
 * @author kdxiaoyi
 * @author KimiAI 亦有贡献
 * @since 0.2.0
 */
public class SConfig {


  /* ==========================================
   * 初始化与变量
   * ========================================== */

  // formats
  private enum ConfigType {
    JSON, YAML, TOML, INI
  }
  // file lock
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private volatile Map<String, Object> cache = new ConcurrentHashMap<>();
  private volatile long lastModified = 0;
  // auto reload
  private WatchService watchService;
  private Thread watchThread;
  private volatile boolean watching = false;
  // conf meta
  private final File conf;
  private final ConfigType type;

  /**
   * 构造配置对象
   * @param file 配置文件
   * @param ctype 格式，支持 json/yml/yaml/toml/ini（大小写不敏感）
   * @throws UnsupportedOperationException 不支持的格式
   */
  public SConfig(File file, String ctype) {
    this.conf = file;
    this.type = parseType(ctype);
    load();
  }

  /**
   * 构造配置对象
   * @param file 配置文件
   * @param ctype 格式，支持 json/yml/yaml/toml/ini（大小写不敏感）
   * @throws UnsupportedOperationException 不支持的格式
   * @since 0.4.4
   */
  public SConfig(Path file, String ctype) {
    this.conf = file.toFile();
    this.type = parseType(ctype);
    load();
  }

  /**
   * 构造临时配置对象
   * @param conf 配置文件内容原始来源
   * @param ctype 格式，支持 json/yml/yaml/toml/ini（大小写不敏感）
   * @throws UnsupportedOperationException 不支持的格式
   * @throws IOException 读写错误
   * @see #SConfig(File, String)
   * @since 0.4.4
   */
  public static SConfig initInput(String conf, String ctype) throws Exception {
    File tmp = Files.createTempFile("sconfig-tmp-", ctype).toFile();
    Writer w = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8);
    w.write(conf);
    w.close();
    return new SConfig(tmp, ctype);
  }

  /**
   * 构造配置对象
   * @param path 配置文件路径
   * @param ctype 格式，支持 json/yml/yaml/toml/ini（大小写不敏感）
   * @throws UnsupportedOperationException 不支持的格式
   * @since 0.4.4
   */
  public SConfig(String path, String ctype) {
    this.conf = new File(path);
    this.type = parseType(ctype);
    load();
  }

  private static ConfigType parseType(String ctype) {
    if (ctype == null) throw new IllegalArgumentException("ctype 不能为空");
    switch (ctype.toLowerCase(Locale.ROOT)) {
      case "json":
        return ConfigType.JSON;
      case "yml":
        return ConfigType.YAML;
      case "yaml":
        return ConfigType.YAML;
      case "toml":
        return ConfigType.TOML;
      case "ini":
        return ConfigType.INI;
      default: throw new UnsupportedOperationException("不支持的文件类型：" + ctype);
    }
  }

  /* ==========================================
   * 增删查改
   * ========================================== */

  /**
   * **已弃用，请使用严格类型 API**
   * 获取指定配置项
   * @param <T> 可为String/List/Int/Number
   * @param key 目标配置项，没有自动新增
   * @param fallback 默认值，如果没有传入则为空字符串
   * @return 获取到的值
   * @deprecated
   */
  @Deprecated
  @SuppressWarnings("unchecked")
  public <T> T get(String key, T fallback) {
    lock.readLock().lock();
    try {
      if (fallback == null) fallback = (T) "";
      if (!cache.containsKey(key)) {
        put(key, fallback);
        return fallback;
      }
      return (T) cache.get(key);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * **已弃用，请使用严格类型 API**
   * 写入配置项
   * @param <T> 可为String/List/Int/Number
   * @param key 目标配置项，没有自动新增
   * @param value 目标值
   * @deprecated
   */
  @Deprecated
  public <T> void put(String key, T value) {
    lock.writeLock().lock();
    try {
      cache.put(key, value);
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // String
  /** 获取字符串，缺失返回空串；支持嵌套 key，如 "server.port" */
  public String getString(String key) {
    return getString(key, "");
  }
  /** 获取字符串，缺失返回默认值；支持嵌套 key，如 "server.port" */
  public String getString(String key, String def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      return v == null ? def : String.valueOf(v);
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入字符串；支持嵌套 key，如 "server.port" */
  public void putString(String key, String value) {
    lock.writeLock().lock();
    try {
      putNested(key, value); // 改为调用嵌套版本
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // Int
  /** 获取 int，缺失返回 0；支持嵌套 key，如 "server.port" */
  public int getInt(String key) {
    return getInt(key, 0);
  }
  /** 获取 int，缺失返回默认值；支持嵌套 key，如 "server.port" */
  public int getInt(String key, int def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      if (v instanceof Number)
        return ((Number) v).intValue();
      if (v instanceof String) {
        try {
          return Integer.parseInt((String) v);
        } catch (NumberFormatException ignore) {
        }
      }
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入 int；支持嵌套 key，如 "server.port" */
  public void putInt(String key, int value) {
    lock.writeLock().lock();
    try {
      putNested(key, value); // 改为调用嵌套版本
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // Long
  /** 获取 long，缺失返回 0L；支持嵌套 key，如 "server.port" */
  public long getLong(String key) {
    return getLong(key, 0L);
  }
  /** 获取 long，缺失返回默认值；支持嵌套 key，如 "server.port" */
  public long getLong(String key, long def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      if (v instanceof Number)
        return ((Number) v).longValue();
      if (v instanceof String) {
        try {
          return Long.parseLong((String) v);
        } catch (NumberFormatException ignore) {
        }
      }
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入 long；支持嵌套 key，如 "server.port" */
  public void putLong(String key, long value) {
    lock.writeLock().lock();
    try {
      putNested(key, value); // 改为调用嵌套版本
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // Double
  /** 获取 double，缺失返回 0.0；支持嵌套 key，如 "server.port" */
  public double getDouble(String key) {
    return getDouble(key, 0.0);
  }
  /** 获取 double，缺失返回默认值；支持嵌套 key，如 "server.port" */
  public double getDouble(String key, double def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      if (v instanceof Number)
        return ((Number) v).doubleValue();
      if (v instanceof String) {
        try {
          return Double.parseDouble((String) v);
        } catch (NumberFormatException ignore) {
        }
      }
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入 double；支持嵌套 key，如 "server.port" */
  public void putDouble(String key, double value) {
    lock.writeLock().lock();
    try {
      putNested(key, value); // 改为调用嵌套版本
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // Boolean
  /** 获取 boolean，缺失返回 false；支持嵌套 key，如 "server.port" */
  public boolean getBoolean(String key) {
    return getBoolean(key, false);
  }
  /** 获取 boolean，缺失返回默认值；支持嵌套 key，如 "server.port" */
  public boolean getBoolean(String key, boolean def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      if (v instanceof Boolean)
        return (Boolean) v;
      if (v instanceof String)
        return Boolean.parseBoolean((String) v);
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入 boolean；支持嵌套 key，如 "server.port" */
  public void putBoolean(String key, boolean value) {
    lock.writeLock().lock();
    try {
      putNested(key, value); // 改为调用嵌套版本
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // StringList
  /** 获取字符串列表，缺失返回空列表（不可变）；支持嵌套 key，如 "server.hosts" */
  public List<String> getListOfString(String key) {
    return getListOfString(key, Collections.emptyList());
  }
  /** 获取字符串列表，缺失返回默认值；支持嵌套 key，如 "server.hosts" */
  @SuppressWarnings("unchecked")
  public List<String> getListOfString(String key, List<String> def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      if (v instanceof List) {
        List<?> raw = (List<?>) v;
        if (raw.isEmpty() || raw.get(0) instanceof String) {
          return (List<String>) v;
        }
        List<String> list = new ArrayList<>(raw.size());
        for (Object o : raw)
          list.add(String.valueOf(o));
        return list;
      }
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入字符串列表；支持嵌套 key，如 "server.hosts" */
  public void putListOfString(String key, List<String> value) {
    lock.writeLock().lock();
    try {
      putNested(key, new ArrayList<>(value)); // 改为调用嵌套版本
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // List
  /** 获取一般列表，缺失返回空列表（不可变）；支持嵌套 key，如 "server.hosts" */
  public List<Object> getList(String key) {
    return getList(key, Collections.emptyList());
  }
  /** 获取一般列表，缺失返回默认值；支持嵌套 key，如 "server.hosts" */
  @SuppressWarnings("unchecked")
  public List<Object> getList(String key, List<Object> def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      if (v instanceof List) {
        List<?> raw = (List<?>) v;
        if (raw.isEmpty() || raw.get(0) instanceof Object) {
          return (List<Object>) v;
        }
        List<Object> list = new ArrayList<>(raw.size());
        for (Object o : raw)
          list.add(String.valueOf(o));
        return list;
      }
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入一般列表；支持嵌套 key，如 "server.hosts" */
  public void putList(String key, List<Object> value) {
    lock.writeLock().lock();
    try {
      putNested(key, new ArrayList<>(value)); // 改为调用嵌套版本
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  // Section (Map<String,Object>)
  /** 获取子配置段，缺失返回空 Map（不可变）；支持嵌套 key，如 "server" */
  public Map<String, Object> getSection(String key) {
    return getSection(key, Collections.emptyMap());
  }
  /** 获取子配置段，缺失返回默认值；支持嵌套 key，如 "server" */
  @SuppressWarnings("unchecked")
  public Map<String, Object> getSection(String key, Map<String, Object> def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      if (v instanceof Map)
        return new LinkedHashMap<>((Map<String, Object>) v);
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入子配置段；支持嵌套 key，如 "server" */
  public void putSection(String key, Map<String, Object> section) {
    lock.writeLock().lock();
    try {
      putNested(key, new LinkedHashMap<>(section)); // 改为调用嵌套版本
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 删除配置项；支持嵌套 key，如 "server.port"
   * 若路径不存在或中途类型不匹配，静默返回
   */
  public void remove(String key) {
    lock.writeLock().lock();
    try {
      int lastDot = key.lastIndexOf(NESTED_SEP);
      if (lastDot == -1) {
        cache.remove(key);
      } else {
        Map<String, Object> parent = ensureNestedMap(key);
        if (parent != null) {
          String lastKey = key.substring(lastDot + 1);
          parent.remove(lastKey);
        }
      }
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /* ==========================================
   * 工具
   * ========================================== */

  /** @return 当前配置文件对象 */
  public File getFile() {
    return conf;
  }

  private static final String NESTED_SEP = ".";

  /**
   * 从嵌套路径读取值，路径不存在或中途类型不匹配返回 null
   */
  @SuppressWarnings("unchecked")
  private Object getNested(String key) {
    int dot = key.indexOf(NESTED_SEP);
    if (dot == -1)
      return cache.get(key);

    String first = key.substring(0, dot);
    Object current = cache.get(first);
    if (!(current instanceof Map))
      return null;

    Map<String, Object> map = (Map<String, Object>) current;
    String rest = key.substring(dot + 1);
    return drillDown(map, rest);
  }

  /**
   * 递归或迭代下探到最后一级 Map
   */
  @SuppressWarnings("unchecked")
  private Object drillDown(Map<String, Object> map, String path) {
    int dot = path.indexOf(NESTED_SEP);
    if (dot == -1)
      return map.get(path);

    String first = path.substring(0, dot);
    Object next = map.get(first);
    if (!(next instanceof Map))
      return null;

    String rest = path.substring(dot + 1);
    return drillDown((Map<String, Object>) next, rest);
  }
  
  /**
   * 确保嵌套路径存在，返回最后一级 Map 以便写入
   * 若中途遇到非 Map 节点，返回 null 表示无法创建
  */
  @SuppressWarnings("unchecked")
  private Map<String, Object> ensureNestedMap(String key) {
    int lastDot = key.lastIndexOf(NESTED_SEP);
    if (lastDot == -1)
      return cache;

    String[] parts = key.split("\\.");
    Map<String, Object> current = cache;
    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      Object next = current.get(part);
      if (next == null) {
        Map<String, Object> newMap = new LinkedHashMap<>();
        current.put(part, newMap);
        current = newMap;
      } else if (next instanceof Map) {
        current = (Map<String, Object>) next;
      } else {
        // 中途节点非 Map，无法继续嵌套
        return null;
      }
    }
    return current;
  }

  /**
   * 向嵌套路径写入值，若路径非法（中间节点非 Map）则退化为普通 key 写入顶层
   */
  private void putNested(String key, Object value) {
    Map<String, Object> targetMap = ensureNestedMap(key);
    if (targetMap == null) {
      // 嵌套失败，退化为顶层写入（保持兼容）
      cache.put(key, value);
      return;
    }

    int lastDot = key.lastIndexOf(NESTED_SEP);
    String lastKey = (lastDot == -1) ? key : key.substring(lastDot + 1);
    targetMap.put(lastKey, value);
  }

  /* ==========================================
   * 自动重载
   * ========================================== */

  /** 
   * 启动自动重载
   * 若当前已启用会静默处理。
   */
  public void startAutoReload() {
    if (watching)
      return;
    try {
      watchService = FileSystems.getDefault().newWatchService();
      Path confPath = conf.toPath().toAbsolutePath();
      Path dir = confPath.getParent();
      dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
      watching = true;

      watchThread = new Thread(() -> {
        while (watching && !Thread.currentThread().isInterrupted()) {
          try {
            WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
            if (key == null)
              continue;
            for (WatchEvent<?> event : key.pollEvents()) {
              Path changed = dir.resolve((Path) event.context());
              if (changed.toAbsolutePath().equals(confPath)
                  && conf.lastModified() > lastModified) {
                reload();
              }
            }
            key.reset();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
          }
        }
      }, "conf-reload-" + conf.getName());
      watchThread.setDaemon(true);
      watchThread.start();
    } catch (IOException e) {
      e.printStackTrace();
      stopAutoReload();
      throw new UncheckedIOException("无法启用自动重载：", e);
    }
  }

  /** 停止自动重载 */
  public void stopAutoReload() {
    watching = false;
    if (watchThread != null)
      watchThread.interrupt();
    try {
      if (watchService != null)
        watchService.close();
    } catch (IOException ignore) {
    } finally {
      watchService = null;
      watchThread = null;
    }
  }

  /** @return 是否正在自动重载 */
  public boolean isAutoReloading() {
    return watching;
  }

  /* ==========================================
   * 读写
   * ========================================== */
  
  /** 立即重新加载文件到缓存 */
  public void reload() {
    load();
  }

  /**
   * 加载文件到缓存
   */
  private void load() {
    lock.writeLock().lock();
    try {
      if (!conf.exists()) {
        cache = new ConcurrentHashMap<>();
        return;
      }
      Map<String, Object> loaded;
      try (InputStream in = new FileInputStream(conf)) {
        switch (type) {
          case JSON:
            loaded = loadJson(in);
            break;
          case YAML:
            loaded = loadYaml(in);
            break;
          case TOML:
            loaded = loadToml(in);
            break;
          case INI:
            loaded = loadIni(in);
            break;
          default:
            throw new UnsupportedOperationException("不支持的文件类型：" + type);
        }
      }
      cache = loaded == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(loaded);
      lastModified = conf.lastModified();
    } catch (IOException e) {
      throw new UncheckedIOException("无法加载配置文件", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private Map<String, Object> loadJson(InputStream in) {
    JsonElement el = JsonParser.parseReader(new InputStreamReader(in));
    if (el.isJsonObject()) {
      Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
      return new Gson().fromJson(el, mapType);
    }
    return new HashMap<>();
  }

  private Map<String, Object> loadYaml(InputStream in) {
    Yaml yaml = new Yaml();
    Map<String, Object> m = yaml.load(in);
    return m == null ? new HashMap<>() : m;
  }

  private Map<String, Object> loadToml(InputStream in) throws IOException {
    Toml toml = new Toml();
    try (InputStreamReader r = new InputStreamReader(in)) {
      toml.read(r);
    }
    return toml.toMap();
  }

  private Map<String, Object> loadIni(InputStream in) throws IOException {
    Ini ini = new Ini();
    ini.load(in);
    Map<String, Object> root = new LinkedHashMap<>();
    for (Map.Entry<String, Profile.Section> entry : ini.entrySet()) {
      String secName = entry.getKey();
      Profile.Section sec = entry.getValue();
      Map<String, Object> section = new LinkedHashMap<>();
      for (Map.Entry<String, String> e : sec.entrySet()) {
        section.put(e.getKey(), e.getValue());
      }
      root.put(secName, section);
    }
    return root;
  }

  /**
   * 将缓存写入磁盘
   */
  private void flush() {
    lock.writeLock().lock();
    try {
      atomicWrite(conf.toPath(), w -> {
        switch (type) {
          case JSON:
            flushJson(w);
            break;
          case YAML:
            flushYaml(w);
            break;
          case TOML:
            flushToml(w);
            break;
          case INI:
            flushIni(w);
            break;
          default:
            throw new UnsupportedOperationException("不支持的文件类型：" + type);
        }
      });
      lastModified = conf.lastModified();
    } catch (IOException e) {
      throw new UncheckedIOException("无法写入配置文件", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private void flushJson(Writer w) {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    gson.toJson(cache, w);
  }

  private void flushYaml(Writer w) {
    DumperOptions opts = new DumperOptions();
    opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    opts.setPrettyFlow(true);
    new Yaml(opts).dump(cache, w);
  }

  private void flushToml(Writer w) throws IOException {
    new TomlWriter().write(cache, w);
  }

  @SuppressWarnings("unchecked")
  private void flushIni(Writer w) throws IOException {
    Ini ini = new Ini();
    for (Map.Entry<String, Object> e : cache.entrySet()) {
      if (e.getValue() instanceof Map) {
        Profile.Section sec = ini.add(e.getKey());
        Map<String, Object> section = (Map<String, Object>) e.getValue();
        for (Map.Entry<String, Object> se : section.entrySet()) {
          sec.put(se.getKey(), String.valueOf(se.getValue()));
        }
      }
    }
    ini.store(w);
  }

  /** 原子替换文件：先写临时文件，再 move */
  private void atomicWrite(Path target, IOConsumer<Writer> writerBlock) throws IOException {
    Path dir = target.toAbsolutePath().getParent();
    Path tmp = dir.resolve(target.getFileName().toString() + ".tmp");
    try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
      writerBlock.accept(w);
    }
    try {
      Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ignore) {
      // 某些文件系统不支持原子 move，退化为复制后删除
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** 简化函数式接口 */
  @FunctionalInterface
  private interface IOConsumer<T> {
    void accept(T t) throws IOException;
  }
}
