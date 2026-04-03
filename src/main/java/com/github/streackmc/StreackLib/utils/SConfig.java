package com.github.streackmc.StreackLib.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
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
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nullable;

import org.ini4j.Ini;
import org.ini4j.Profile;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.utils.SConfig.TYPES;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;

import de.pauleff.jnbt.api.ITag;
import de.pauleff.jnbt.api.NBTFactory;

/**
 * 高性能多格式（json/yaml/toml/xml/ini/prop）配置中心。
 * 支持运行时热加载与严格类型读写。
 *
 * @author kdxiaoyi
 * @author KimiAI 亦有贡献
 * @since 0.2.0
 */
public class SConfig {

  /* ==========================================
   * 常量
   * ========================================== */

  public final Long INSTANCE_ID = StreackLib.getUniqueID();

  /** 支持的文件类型的标准化字符串。所有字符串都不区分大小写。 */
  public final static class TYPES {
    /**
     * @apiNote 不支持宽松模式，例如注释和尾随逗号。参见 {@link TYPES.JSONC}
     * @apiNote 根数组类型的JSON会自动将该数组放入键 _root_array 中；在 0.4.6 及更早版本中则会被忽略。
     * 
     *          <pre>
     *          [{data: "abc"}, {data: "abc"}]
     *          </pre>
     */
    public final static String JSON = "json";
    /**
     * 解析宽松的JSON，例如注释和尾随逗号。
     * @since 0.4.7
     * @apiNote 写入时会覆盖并丢失全部注释
     * @apiNote 根数组类型的JSON会自动将该数组放入键 _root_array 。
     * 
     *          <pre>
     *          [{data: "abc"}, {data: "abc"}]
     *          </pre>
     */
    public final static String JSONC = "jsonc";
    /**
     * 亦作 {@link TYPES.YML}
     */
    public final static String YAML = "yaml";
    /**
     * 亦作 {@link TYPES.YAML}
     */
    public final static String YML = "yaml";
    public final static String TOML = "toml";
    public final static String INI = "ini";
    public final static String PROPERTIES = "prop";
    /** Minecraft NBT (二进制文件) */
    public final static String NBT = "nbt";
    /** Minecraft NBT (人类可读文本) */
    public final static String SNBT = "snbt";
  }

  public final static class EVENTS {
    /**
     * 配置文件发生改变
     * @apiNote 仅由自动重载触发
    */
   public final static String CHANGED = "streacklib.sconf:changed";
   /**
     * 配置文件格式错误
     * @param exception {Exception} 原始错误数据
     * @param msg {String} 错误信息
     */
    public final static String WRONG_FORMAT = "streacklib.sconf:wrong_format";
  }

  /* ==========================================
   * 初始化与变量
   * ========================================== */

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
  private final Backend confHandler;

  /**
   * 构造配置对象
   * 
   * @param file  配置文件
   * @param ctype 格式，支持列表见于 {@link TYPES}
   * @throws UnsupportedOperationException 不支持的格式
   */
  public SConfig(File file, String ctype) {
    this.conf = file;
    this.confHandler = this.parseType(ctype);
    load();
  }

  /**
   * 构造配置对象
   * 
   * @param file  配置文件
   * @param ctype 格式，支持列表见于 {@link TYPES}
   * @throws UnsupportedOperationException 不支持的格式
   * @since 0.4.4
   */
  public SConfig(Path file, String ctype) {
    this.conf = file.toFile();
    this.confHandler = this.parseType(ctype);
    load();
  }

  /**
   * 构造配置对象
   * 
   * @param path  配置文件路径
   * @param ctype 格式，支持列表见于 {@link TYPES}
   * @throws UnsupportedOperationException 不支持的格式
   * @since 0.4.4
   */
  public SConfig(String path, String ctype) {
    this.conf = new File(path);
    this.confHandler = this.parseType(ctype);
    load();
  }

  /**
   * 构造临时配置对象
   * 
   * @param conf   配置文件内容原始来源
   * @param ctype  格式，支持列表见于 {@link TYPES}
   * @param suffix 临时文件后缀，如 ".yml"，可为Null
   * @throws UnsupportedOperationException 不支持的格式
   * @throws IOException                   读写错误
   * @see #SConfig(File, String)
   * @since 0.4.4
   */
  public SConfig(String conf, String ctype, @Nullable String suffix) throws Exception {
    this.confHandler = this.parseType(ctype);
    this.conf = Files.createTempFile("sconfig-tmp-", suffix).toFile();
    try (Writer w = Files.newBufferedWriter(this.conf.toPath(), StandardCharsets.UTF_8)) {
      w.write(conf);
    } catch (IOException e) {
      throw e;
    }
  }

  /**
   * 构造临时配置对象
   * 
   * @param rawData   配置文件内容原始来源，为Null时视作空数据
   * @param ctype  格式，支持列表见于 {@link TYPES}
   * @param suffix 临时文件后缀，如 ".yml"，可为Null
   * @throws UnsupportedOperationException 不支持的格式
   * @throws IOException                   读写错误
   * @see #SConfig(File, String)
   * @since 0.4.7
   */
  public SConfig(@Nullable Map<String, Object> rawData, String ctype, @Nullable String suffix) throws Exception {
    Map<String, Object> rD = Objects.requireNonNullElse(rawData, new ConcurrentHashMap<>());
    this.confHandler = this.parseType(ctype);
    this.conf = Files.createTempFile("sconfig-tmp-", suffix).toFile();
    // 将 Map 直接作为数据来源并写入
    this.cache = rD;
    flush();
  }

  private Backend parseType(String ctype) {
    if (ctype == null)
      throw new IllegalArgumentException("ctype 不能为空");
    switch (ctype.replaceAll("\\s+", "").toLowerCase(Locale.ROOT)) {
      case "json":
        return new BackendJSON();
      case "jsonc":
        return new BackendJSONc();
      case "yml":
        return new BackendYaml();
      case "yaml":
        return new BackendYaml();
      case "toml":
        return new BackendToml();
      case "ini":
        return new BackendINI();
      case "properties":
        return new BackendProperties();
      case "prop":
        return new BackendProperties();
      case "snbt":
        return new BackendSNBT();
      case "nbt":
        return new BackendNBT();
      default:
        throw new UnsupportedOperationException(String.format("不支持的文件类型 [%s]", ctype));
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

  // Float
  /** 获取 Float，缺失返回 0L；支持嵌套 key，如 "server.port" */
  public float getFloat(String key) {
    return getFloat(key, 0F);
  }
  /** 获取 Float，缺失返回默认值；支持嵌套 key，如 "server.port" */
  public float getFloat(String key, float def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key); // 改为调用嵌套版本
      if (v instanceof Number)
        return ((Number) v).floatValue();
      if (v instanceof String) {
        try {
          return Float.parseFloat((String) v);
        } catch (NumberFormatException ignore) {
        }
      }
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  /** 写入 Float；支持嵌套 key，如 "server.port" */
  public void putFloat(String key, float value) {
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

  // Section (SConfig)
  /**
   * 获取子配置段。
   * <p>
   * 该方法本质是对 {@link #getSection(String, Map)} 的再包装，自动封装结果
   * <p>
   * 新建的SConfig实例会使用临时文件
   * 
   * @param key    支持嵌套 key，如 "server"
   * @param ctype  格式，支持列表见于 {@link TYPES}
   * @param suffix 临时文件后缀，如 ".yml"，可为Null
   * @throws UnsupportedOperationException 不支持的格式
   * @throws IOException                   读写错误
   * @see #SConfig(File, String)
   * @since 0.4.7
   */
  public SConfig getSection(String key, String ctype, @Nullable String suffix) throws Exception {
    return new SConfig(getSection(key), ctype, suffix);
  }
  /**
   * 写入子配置段；支持嵌套 key，如 "server"
   * <p>
   * 该方法本质是对 {@link #putSection(String, Map)} 的再包装，自动提取 SConfig 的 rawData
   */
  public void putSection(String key, SConfig SectionConfig) {
    putSection(key, SectionConfig.getRawData());
  }

  // 其它

  /**
   * 删除配置项；支持嵌套 key，如 "server.port"
   * 若路径不存在或中途类型不匹配，静默返回
   */
  public void remove(String key) {
    lock.writeLock().lock();
    try {
      int lastDot = getIndexOfNormalDot(key);
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

  /**
   * @apiNote 即使已启用自动重载，仍然建议先使用 {@link #reload()} 刷新数据，以免防止某些边缘情况。
   * @return 当前已加载的数据
   */
  public Map<String, Object> getRawData() {
    return cache;
  }

  /* ==========================================
   * 嵌套路径处理
   * ========================================== */

  /**
   * 获取第一个未被转义的 . 的位置，找不到返回 -1
   */
  private static final int getIndexOfNormalDot(String key) {
    if (key == null)
      return -1;
    boolean escaped = false; // 表示当前字符是否被前一个反斜杠转义
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (c == '\\') {
        // 遇到反斜杠：翻转转义状态（只有未转义的反斜杠才能转义下一个字符）
        escaped = !escaped;
      } else {
        if (c == '.' && !escaped) {
          return i; // 找到未被转义的点
        }
        // 普通字符或已被转义的点：重置转义状态
        escaped = false;
      }
    }
    return -1; // 未找到
  }

  /**
   * 支持转义 . 的切割路径
   */
  private static List<String> splitWithNormalDot(String key) {
    List<String> parts = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (c == '\\') {
        // 检查下一个字符是否是点号（转义点号）
        if (i + 1 < key.length() && key.charAt(i + 1) == '.') {
          // 跳过反斜杠，将点号作为普通字符加入当前段（不触发分割）
          sb.append('.');
          i++; // 跳过点号
          continue;
        } else {
          // 其他情况：保留反斜杠本身
          sb.append('\\');
          continue;
        }
      }
      if (c == '.') {
        // 未转义的点号，分割
        parts.add(sb.toString());
        sb.setLength(0);
      } else {
        sb.append(c);
      }
    }
    parts.add(sb.toString());
    return parts;
  }

  /**
   * 从嵌套路径读取值，路径不存在或中途类型不匹配返回 null
   */
  @SuppressWarnings("unchecked")
  private Object getNested(String key) {
    int dot = getIndexOfNormalDot(key);
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
    int dot = getIndexOfNormalDot(path);
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
    int lastDot = getIndexOfNormalDot(key);
    if (lastDot == -1)
      return cache;

    List<String> parts = splitWithNormalDot(key);
    Map<String, Object> current = cache;
    for (int i = 0; i < parts.size() - 1; i++) {
      String part = parts.get(i);
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

    int lastDot = getIndexOfNormalDot(key);
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
      logger.debug("SConfig#%s 正在启动自动重载", INSTANCE_ID);
      watchService = FileSystems.getDefault().newWatchService();
      Path confPath = conf.toPath().toAbsolutePath();
      Path dir = confPath.getParent();
      dir.register(watchService,
        StandardWatchEventKinds.ENTRY_MODIFY,
          StandardWatchEventKinds.ENTRY_CREATE/* 防止有些编辑器使用原子写入 */);
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
                && conf.lastModified() != lastModified) {
                logger.debug("SConfig#%s 自动重载中……", INSTANCE_ID);
                reload();
                SEventCentral.broadcastEvent(EVENTS.CHANGED, INSTANCE_ID).broadcast();
              }
            }
            key.reset();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          } catch (Exception ignored/* 防止假死 */) {
            continue;
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
    logger.debug("SConfig#%s 正在停止自动重载", INSTANCE_ID);
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

  /*
   * ==========================================
   * 后端读写 路由和interface
   * ==========================================
   */

  /** 立即重新加载文件到缓存 */
  public void reload() {
    load();
  }

  /**
   * 将缓存写入磁盘
   */
  private void flush() {
    lock.writeLock().lock();
    try {
      atomicWrite(conf.toPath(), (w) -> {
        confHandler.flush(w);
      });
    } catch (Exception e) {
      throw new RuntimeException("无法写入配置文件", e);
    } finally {
      lock.writeLock().unlock();
    }
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
        logger.debug("SConfig#%s 正在加载配置文件", INSTANCE_ID);
        loaded = confHandler.load(in);
      }
      cache = loaded == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(loaded);
      lastModified = conf.lastModified();
    } catch (Exception e) {
      SEventCentral.broadcastEvent(EVENTS.WRONG_FORMAT, INSTANCE_ID)
          .set("exception", e)
          .set("msg", e.getLocalizedMessage())
          .broadcast();
      throw new RuntimeException("无法加载配置文件", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private interface Backend {
    Map<String, Object> load(InputStream in) throws Exception;
    void flush(Writer w) throws Exception;
    String getType();
  }

  /*
   * ==========================================
   * 后端读写 具体实现
   * ==========================================
   */

  private class BackendYaml implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      Yaml yaml = new Yaml();
      Map<String, Object> m = yaml.load(in);
      return m == null ? new HashMap<>() : m;
    };

    @Override
    public void flush(Writer w) throws Exception {
      DumperOptions opts = new DumperOptions();
      opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      opts.setPrettyFlow(true);
      new Yaml(opts).dump(cache, w);
    };

    @Override
    public String getType() { return TYPES.YAML; };
  }

  private class BackendJSON implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      JsonElement el = JsonParser.parseReader(new InputStreamReader(in));
      if (el.isJsonObject()) {
        Type mapType = new TypeToken<Map<String, Object>>() {
        }.getType();
        return new Gson().fromJson(el, mapType);
      }
      // 处理根数组：将其包装为单键 Map
      if (el.isJsonArray()) {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("_root_array", el.getAsJsonArray());
        return wrapper;
      }
      return new HashMap<>();
    };

    @Override
    public void flush(Writer w) throws Exception {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      Object maybeArray = cache.get("_root_array");
      if (cache.size() == 1 && maybeArray != null) {
        // 符合条件，写作纯数组
        gson.toJson(maybeArray, w);
      } else {
        // 直接写入
        gson.toJson(cache, w);
      }
    };

    @Override
    public String getType() { return TYPES.JSON; };
  }

  private class BackendJSONc implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      // 启用 lenient 模式，支持注释、尾随逗号等
      Gson gson = new GsonBuilder().setLenient().create();
      try (InputStreamReader reader = new InputStreamReader(in)) {
        JsonElement el = gson.fromJson(reader, JsonElement.class);
        if (el.isJsonObject()) {
          Type mapType = new TypeToken<Map<String, Object>>() {
          }.getType();
          return gson.fromJson(el, mapType);
        }
        // 处理根数组：将其包装为单键 Map
        if (el.isJsonArray()) {
          Map<String, Object> wrapper = new LinkedHashMap<>();
          wrapper.put("_root_array", el.getAsJsonArray());
          return wrapper;
        }
      } catch (Exception ignore) {
      }
      return new HashMap<>();
    };

    @Override
    public void flush(Writer w) throws Exception {// 注释在读取时就被 GSON 抛弃，写回注释不现实
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      Object maybeArray = cache.get("_root_array");
      if (cache.size() == 1 && maybeArray != null) {
        // 符合条件，写作纯数组
        gson.toJson(maybeArray, w);
      } else {
        // 直接写入
        gson.toJson(cache, w);
      }
    };

    @Override
    public String getType() { return TYPES.JSONC; };
  }

  private class BackendToml implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      Toml toml = new Toml();
      try (InputStreamReader r = new InputStreamReader(in)) {
        toml.read(r);
      }
      return toml.toMap();
    };

    @Override
    public void flush(Writer w) throws Exception {
      new TomlWriter().write(cache, w);
    };

    @Override
    public String getType() { return TYPES.TOML; };
  }

  private class BackendINI implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
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
    };

    @Override
    @SuppressWarnings("unchecked")
    public void flush(Writer w) throws Exception {
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
    };

    @Override
    public String getType() { return TYPES.INI; };
  }

  private class BackendProperties implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      Properties props = new Properties();
      // 使用 UTF-8 读取，以支持非 ISO-8859-1 字符
      try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        props.load(reader);
      }
      Map<String, Object> root = new LinkedHashMap<>();
      for (String key : props.stringPropertyNames()) {
        String value = props.getProperty(key);
        // 利用现有嵌套路径工具构建嵌套结构
        putNestedRaw(root, key, value);
      }
      return root;
    };

    @Override
    public void flush(Writer w) throws Exception {
      Properties props = new Properties();
      flattenMap("", cache, props);
      props.store(w, null);
    };

    /**
     * 将嵌套 Map 递归展开为扁平的键值对，存入 Properties
     * 
     * @param prefix 当前路径前缀（用点分隔）
     * @param map    当前层级的 Map
     * @param props  目标 Properties
     */
    @SuppressWarnings("unchecked")
    private static void flattenMap(String prefix, Map<String, Object> map, Properties props) {
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        String key = entry.getKey();
        String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
        Object value = entry.getValue();
        if (value instanceof Map) {
          // 递归处理子 Map
          flattenMap(fullKey, (Map<String, Object>) value, props);
        } else {
          // 非 Map 类型转换为字符串存入
          props.setProperty(fullKey, value == null ? "" : String.valueOf(value));
        }
      }
    }

    /**
     * 辅助方法：将扁平键值对插入嵌套 Map（用于 loadProperties）
     */
    private static void putNestedRaw(Map<String, Object> root, String key, Object value) {
      int lastDot = getIndexOfNormalDot(key);
      if (lastDot == -1) {
        root.put(key, value);
        return;
      }
      List<String> parts = splitWithNormalDot(key);
      Map<String, Object> current = root;
      for (int i = 0; i < parts.size() - 1; i++) {
        String part = parts.get(i);
        Object next = current.get(part);
        if (next == null) {
          Map<String, Object> newMap = new LinkedHashMap<>();
          current.put(part, newMap);
          current = newMap;
        } else if (next instanceof Map) {
          current = (Map<String, Object>) next;
        } else {
          // 类型冲突，无法创建嵌套结构，直接放入根（保持兼容，但通常不会发生）
          root.put(key, value);
          return;
        }
      }
      String lastKey = parts.get(parts.size() - 1);
      current.put(lastKey, value);
    }

    @Override
    public String getType() { return TYPES.PROPERTIES; };
  }

  private class BackendNBT implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      throw new UnsupportedOperationException("尚未实现");// TODO: NBT尚未实现
    };
    
    @Override
    public void flush(Writer w) throws Exception {
      throw new UnsupportedOperationException("尚未实现");
    };

    @Override
    public String getType() { return TYPES.NBT; };
  }

  private class BackendSNBT implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      throw new UnsupportedOperationException("尚未实现");
      // 获取文件内容并转为SNBT
      StringWriter sw = new StringWriter();
      try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        reader.transferTo(sw);
      }
      ITag<?> snbt = NBTFactory.parseFromSNBT(sw.toString());
      Map<String, Object> result = new ConcurrentHashMap<>();

      snbt.applyOperation((item) -> {
        
      });

      return result;
    };

    @Override
    public void flush(Writer w) throws Exception {
      throw new UnsupportedOperationException("尚未实现");
    };

    @Override
    public String getType() { return TYPES.SNBT; };
  }

  /* ==========================================
  * 工具方法
  * ========================================== */

  /** @return 当前配置文件对象 */
  public File getFile() {
    return conf;
  }

  /** 原子替换文件：先写临时文件，再 move */
  private void atomicWrite(Path target, IOConsumer<Writer> writerBlock) throws Exception {
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
    void accept(T t) throws Exception;
  }
}
