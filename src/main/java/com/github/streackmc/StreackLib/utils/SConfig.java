package com.github.streackmc.StreackLib.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
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
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

import org.ini4j.Ini;
import org.ini4j.Profile;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.self.nbtHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.moandjiezana.toml.Toml;
import com.moandjiezana.toml.TomlWriter;

import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NBTSerializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.io.SNBTParser;
import net.querz.nbt.io.SNBTWriter;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.Tag;

/**
 * <h3>SConfig</h3>
 * 高性能多格式配置管理器，支持自动重载与严格类型读写。默认使用 UTF-8 格式，未来计划支持更多。
 * <p>
 * 支持多种文件格式，详见 {@link SConfig.TYPES} ：
 * JSON / JSON with Comment / YAML / Properties / TOML / INI / NBT / SNBT
 * <p>
 * 支持JSON的 Array As Root 和 NBT 的 Root Name 特性，见于 {@link SConfig.TYPES#JSON} 和
 * {@link SConfig#setRootName(String)} 。
 *
 * @author kdxiaoyi
 * @author Kimi[AI] ~~亦有贡献~~现因圈钱过度退出开发
 * @author Deepseek[AI] 亦有贡献
 * @since 0.2.0
 */
public class SConfig {

  /* ==========================================
   * 常量
   * ========================================== */

  /** 当前实例的唯一ID */
  public final Long INSTANCE_ID = StreackLib.getUniqueID();

  /** 支持的文件类型的标准化字符串。所有字符串都不区分大小写。 */
  public final static class TYPES {
    /**
     * @apiNote 不支持宽松模式，例如注释和尾随逗号。参见 {@link TYPES#JSONC}
     * @apiNote 根数组类型的JSON会自动将该数组放入键 _root_array 中；在 0.4.6 及更早版本中则会被忽略。
     * 
     *          <pre>
     *          [{data: "abc"}, {data: "abc"}]
     *          </pre>
     */
    public final static String JSON = "json";
    /**
     * 解析宽松的JSON，例如注释和尾随逗号。
     * 
     * @since 0.4.7
     * @apiNote 写入时会以标准JSON覆盖并因此丢失全部注释
     * @apiNote 根数组类型的JSON会自动将该数组放入键 _root_array 中；在 0.4.6 及更早版本中则会被忽略。
     * 
     *          <pre>
     *          [{data: "abc"}, {data: "abc"}]
     *          </pre>
     */
    public final static String JSONC = "jsonc";
    /**
     * 亦作 {@link TYPES#YML}
     */
    public final static String YAML = "yaml";
    /**
     * 亦作 {@link TYPES#YAML}
     */
    public final static String YML = "yaml";
    public final static String TOML = "toml";
    public final static String INI = "ini";
    public final static String PROPERTIES = "prop";
    /**
     * Minecraft NBT (二进制文件)
     * <p>
     * 使用大端序读取，即 Java 版行为；若要使用小端请使用 {@link TYPES#NBTle}
     * 
     * @apiNote 由于 {@link Writer} 不支持操作二进制数据，所以本格式的写入不经过正常流程。但仍可保证原子性。
     */
    public final static String NBT = "nbt";
    /**
     * Minecraft NBT (二进制文件)
     * <p>
     * 使用小端序读取，即基岩版行为；若要使用大端请使用 {@link TYPES#NBT}
     * 
     * @apiNote 由于 {@link Writer} 不支持操作二进制数据，所以本格式的写入不经过正常流程。但仍可保证原子性。
     */
    public final static String NBTle = "nbtle";
    /**
     * Minecraft NBT (人类可读文本)
     * <p>
     * 最大深度为 Int 上限，且会尝试将原文本经多次内存操作，警惕<b>爆堆栈或者内存</b>风险
     */
    public final static String SNBT = "snbt";
  }

  /** SConfig会通过 {@link SEventCentral} 触发的事件命名 */
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

  /** SConfig支持的写入模式。如果设置错误的模式视作 {@link WRITE_MODE#AUTO_SAVE} */
  public final static class WRITE_MODE {
    /** 默认值。自动保存：产生修改后立即保存到文件。 */
    public final static String AUTOSAVE = "autosave";
    /** 手动保存：所有修改必须调用 {@link SConfig#save()} 才能保存到文件。 */
    public final static String INERTIA = "inertia";
    /** 写保护：无法修改文件，强制保存到原文件会抛出不受检异常。 */
    public final static String WRITELOCK = "writelock";
    /** 只读：无法修改缓存和文件，强制修改会抛出不受检异常。 */
    public final static String READONLY = "readonly";
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
  private String confType;

  // save
  private String writeMode = "autosave";

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
   * @param rawData 配置文件内容原始来源
   * @param ctype   格式，支持列表见于 {@link TYPES}
   * @param suffix  临时文件后缀，如 ".yml"，可为Null
   * @apiNote 默认使用 UTF-8 字符集，自定义字符集请用 {@link #SConfig(String, String, String, Charset)}
   * @throws UnsupportedOperationException 不支持的格式
   * @throws IOException                   读写错误
   * @since 0.4.4
   */
  public SConfig(String rawData, String ctype, @Nullable String suffix) throws Exception {
    this.confHandler = this.parseType(ctype);
    this.conf = Files.createTempFile("sconfig-tmp-", suffix).toFile();

    // 先根据String读取
    try {
      Map<String, Object> loaded;
      try (InputStream in = new ByteArrayInputStream(rawData.getBytes(StandardCharsets.UTF_8))) {
        logger.debug("SConfig#%s 正在加载配置文件", INSTANCE_ID);
        loaded = confHandler.load(in);
      }
      cache = loaded == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(loaded);
    } catch (Exception e) {
      SEventCentral.broadcastEvent(EVENTS.WRONG_FORMAT, INSTANCE_ID)
          .set("exception", e)
          .set("msg", e.getLocalizedMessage())
          .broadcast();
      throw new RuntimeException("无法加载配置文件", e);
    } finally {
    }

    // 再根据 cache 落盘
    flush();
  }

  /**
   * 构造临时配置对象
   * 
   * @param rawData 配置文件内容原始来源
   * @param ctype   格式，支持列表见于 {@link TYPES}
   * @param suffix  临时文件后缀，如 ".yml"，可为Null
   * @param charSet 使用的字符集
   * @throws UnsupportedOperationException 不支持的格式
   * @throws IOException                   读写错误
   * @see #SConfig(File, String)
   * @since 0.4.7
   */
  public SConfig(String rawData, String ctype, @Nullable String suffix, Charset charSet) throws Exception {
    this.confHandler = this.parseType(ctype);
    this.conf = Files.createTempFile("sconfig-tmp-", suffix).toFile();

    // 先根据String读取
    try {
      Map<String, Object> loaded;
      try (InputStream in = new ByteArrayInputStream(rawData.getBytes(charSet))) {
        logger.debug("SConfig#%s 正在加载配置文件", INSTANCE_ID);
        loaded = confHandler.load(in);
      }
      cache = loaded == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(loaded);
    } catch (Exception e) {
      SEventCentral.broadcastEvent(EVENTS.WRONG_FORMAT, INSTANCE_ID)
          .set("exception", e)
          .set("msg", e.getLocalizedMessage())
          .broadcast();
      throw new RuntimeException("无法加载配置文件", e);
    } finally {
    }

    // 再根据 cache 落盘
    flush();
  }

  /**
   * 构造临时配置对象
   * 
   * @param rawData   配置文件内容原始来源，为Null时视作空数据
   * @param ctype  格式，支持列表见于 {@link TYPES}
   * @param suffix 临时文件后缀，如 ".yml"，可为Null
   * @throws UnsupportedOperationException 不支持的格式
   * @throws IOException                   读写错误
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
    this.confType = ctype.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    switch (this.confType) {
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
      case "nbtle":
        return new BackendNBT().setLE(true);
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
   * 
   * @param <T>   可为String/List/Int/Number
   * @param key   目标配置项，没有自动新增
   * @param value 目标值
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   * @deprecated
   */
  @Deprecated
  public <T> SConfig put(String key, T value) {
    if (writeMode.equalsIgnoreCase(WRITE_MODE.READONLY)) {
      throw new IllegalStateException("只读模式下无法修改配置");
    }
    lock.writeLock().lock();
    try {
      cache.put(key, value);
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
      return v == null ? def : String.valueOf(v);
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * 写入字符串；支持嵌套 key，如 "server.port"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putString(String key, String value) {
    lock.writeLock().lock();
    try {
      putNested(key, value);
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
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

  /**
   * 写入 int；支持嵌套 key，如 "server.port"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putInt(String key, int value) {
    lock.writeLock().lock();
    try {
      putNested(key, value);
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
  }

  // short
  /** 获取 long，缺失返回 <pre>(short) 0</pre><p>支持嵌套 key，如 "server.port" */
  public long getShort(String key) {
    return getShort(key, (short) 0);
  }
  /** 获取 long，缺失返回默认值；支持嵌套 key，如 "server.port" */
  public long getShort(String key, short def) {
    lock.readLock().lock();
    try {
      Object v = getNested(key);
      if (v instanceof Number)
        return ((Number) v).shortValue();
      if (v instanceof String) {
        try {
          return Short.parseShort((String) v);
        } catch (NumberFormatException ignore) {
        }
      }
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * 写入 long；支持嵌套 key，如 "server.port"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putLong(String key, short value) {
    lock.writeLock().lock();
    try {
      putNested(key, value);
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
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
  
  /**
   * 写入 long；支持嵌套 key，如 "server.port"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putLong(String key, long value) {
    lock.writeLock().lock();
    try {
      putNested(key, value);
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
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
  
  /**
   * 写入 Float；支持嵌套 key，如 "server.port"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putFloat(String key, float value) {
    lock.writeLock().lock();
    try {
      putNested(key, value);
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
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
  
  /**
   * 写入 double；支持嵌套 key，如 "server.port"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putDouble(String key, double value) {
    lock.writeLock().lock();
    try {
      putNested(key, value);
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
      if (v instanceof Boolean)
        return (Boolean) v;
      if (v instanceof String)
        return Boolean.parseBoolean((String) v);
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * 写入 boolean；支持嵌套 key，如 "server.port"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putBoolean(String key, boolean value) {
    lock.writeLock().lock();
    try {
      putNested(key, value);
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
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
  
  /**
   * 写入字符串列表；支持嵌套 key，如 "server.hosts"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putListOfString(String key, List<String> value) {
    lock.writeLock().lock();
    try {
      putNested(key, new ArrayList<>(value));
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
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
  
  /**
   * 写入一般列表；支持嵌套 key，如 "server.hosts"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putList(String key, List<Object> value) {
    lock.writeLock().lock();
    try {
      putNested(key, new ArrayList<>(value));
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
      Object v = getNested(key);
      if (v instanceof Map)
        return new LinkedHashMap<>((Map<String, Object>) v);
      return def;
    } finally {
      lock.readLock().unlock();
    }
  }
  
  /**
   * 写入子配置段；支持嵌套 key，如 "server"
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putSection(String key, Map<String, Object> section) {
    lock.writeLock().lock();
    try {
      putNested(key, new LinkedHashMap<>(section));
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig putSection(String key, SConfig SectionConfig) {
    return putSection(key, SectionConfig.getRawData());
  }

  // 其它

  /**
   * 删除配置项；支持嵌套 key，如 "server.port"
   * 若路径不存在或中途类型不匹配，静默返回
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   */
  public SConfig remove(String key) {
    if (writeMode.equalsIgnoreCase(WRITE_MODE.READONLY)) {
      throw new IllegalStateException("只读模式下无法修改配置");
    }
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
      if (writeMode.equalsIgnoreCase(WRITE_MODE.AUTOSAVE)) flush();
    } finally {
      lock.writeLock().unlock();
    }
    return this;
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
    if (writeMode.equalsIgnoreCase(WRITE_MODE.READONLY)) {
      throw new IllegalStateException("只读模式下无法修改配置");
    }
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

  /*
   * ==========================================
   * 后端读写 路由和interface
   * ==========================================
   */

  /**
   * 将缓存写入磁盘
   * 
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   * @throws RuntimeException 不受检；无法写入配置文件。
   */
  private void flush() {
    if (writeMode.equalsIgnoreCase(WRITE_MODE.WRITELOCK)) {
      throw new IllegalStateException("SConfig的写保护模式下无法写入缓存到文件");
    }
    if (writeMode.equalsIgnoreCase(WRITE_MODE.READONLY)) {
      throw new IllegalStateException("只读模式下无法写入缓存到文件");
    }
    lock.writeLock().lock();
    try {
      atomicWrite(conf.toPath(), (out) -> {
        confHandler.flush(out);
      });
    } catch (Exception e) {
      throw new RuntimeException("无法写入配置文件", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 加载文件到缓存
   * 
   * @throws RuntimeException 不受检；无法加载配置文件。
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
    public Map<String, Object> load(InputStream in) throws Exception;
    public void flush(OutputStream out) throws Exception;
    public String getType();
  }

  private interface RootNamedBackend extends Backend {
    public String getRootName();
    public void setRootName(String name);
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
    public void flush(OutputStream out) throws Exception {
      DumperOptions opts = new DumperOptions();
      opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
      opts.setPrettyFlow(true);
      new Yaml(opts).dump(cache, getWriter(out));
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
    public void flush(OutputStream out) throws Exception {
      Gson gson = new GsonBuilder().setPrettyPrinting().create();
      Object maybeArray = cache.get("_root_array");
      if (cache.size() == 1 && maybeArray != null) {
        // 符合条件，写作纯数组
        gson.toJson(maybeArray, getWriter(out));
      } else {
        // 直接写入
        gson.toJson(cache, getWriter(out));
      }
    };

    @Override
    public String getType() { return TYPES.JSON; };
  }

  private class BackendJSONc extends BackendJSON {
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
    public void flush(OutputStream out) throws Exception {
      new TomlWriter().write(cache, getWriter(out));
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
    public void flush(OutputStream out) throws Exception {
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
      ini.store(getWriter(out));
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
    public void flush(OutputStream out) throws Exception {
      Properties props = new Properties();
      flattenMap("", cache, props);
      props.store(getWriter(out), null);
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

  private class BackendNBT implements RootNamedBackend {
    // 小端模式支持
    private boolean useLE = false;
    public BackendNBT setLE(boolean status) {
      this.useLE = status;
      return this;
    }

    // GZIP压缩
    private boolean compressed = false;

    // 根名称支持
    private String rootName;
    @Override
    public String getRootName() { return Objects.requireNonNullElse(rootName, ""); }
    @Override
    public void setRootName(String name) { this.rootName = Objects.requireNonNullElse(name, ""); }

    // 配置读写
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      // 需要包装为一个可回溯流，要不然前面的GZIP文件头就没了
      PushbackInputStream inPb = new PushbackInputStream(in, 2);
      compressed = nbtHandler.detectGZIP(inPb);
      NamedTag nt = new NBTDeserializer(compressed, useLE).fromStream(inPb);
      setRootName(nt.getName());
      Tag<?> rootTag = nt.getTag();
      // 根标签必须是 CompoundTag，否则包装
      if (rootTag instanceof CompoundTag) {
        return nbtHandler.Compound2Map((CompoundTag) rootTag);
      } else {
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("_root_value", nbtHandler.Tag2Java(rootTag));
        return wrapper;
      }
    }

    @Override
    public void flush(OutputStream out) throws Exception {
      // 构建 NBT 数据
      CompoundTag rootCompound;
      Object maybeArray = cache.get("_root_array");
      if (cache.size() == 1 && maybeArray != null) {
        rootCompound = new CompoundTag();
        rootCompound.put("_root_array", nbtHandler.Java2Tag(maybeArray));
      } else {
        rootCompound = nbtHandler.Map2Compound(cache);
      }
      NamedTag namedTag = new NamedTag(rootName.isEmpty() ? "" : rootName, rootCompound);

      // 根据 load 时检测的压缩标志决定是否包装 GZIP
      OutputStream actualOut = out;
      if (compressed) {
        actualOut = new GZIPOutputStream(out);
      }
      NBTSerializer serializer = new NBTSerializer(useLE);
      serializer.toStream(namedTag, actualOut);
      if (compressed) {
        actualOut.close(); // GZIPOutputStream 需要 close 来写入尾部
      }
    }

    @Override
    public String getType() { return TYPES.NBT; }
  }

  private class BackendSNBT implements Backend {
    @Override
    public Map<String, Object> load(InputStream in) throws Exception {
      // 读取文本内容
      StringBuilder sb = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
      }

      // 转为 NBT
      SNBTParser parser = new SNBTParser(sb.toString());
      Tag<?> parsedTag = parser.parse();

      // 处理根标签
      if (parsedTag instanceof CompoundTag) {
        // 如果是 CompoundTag，直接利用现有的 nbtHandler 工具转换
        return nbtHandler.Compound2Map((CompoundTag) parsedTag);
      } else {
        // 其他情况（理论上 SNBT 根标签应为 CompoundTag，但这里做防御性处理）
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("_root_value", nbtHandler.Tag2Java(parsedTag));
        return wrapper;
      }
    }

   @Override
   public void flush(OutputStream out) throws Exception {
     // 先回转数据为 NBT
     CompoundTag compound;
     Object maybeUnsafeRoot = cache.get("_root_value");
     if (cache.size() == 1 && maybeUnsafeRoot != null) {
       // 这时需要包装成一个自定义根标签，因为 SNBT 根必须是一个 Compound
       compound = new CompoundTag();
       compound.put("_root_value", nbtHandler.Java2Tag(maybeUnsafeRoot));
     } else {
       compound = nbtHandler.Map2Compound(cache);
     }
     // 再把 NBT 写为 SNBT
     SNBTWriter.write(compound, getWriter(out), Integer.MAX_VALUE);
   }

    @Override
    public String getType() { return TYPES.SNBT; };
  }

  /* ==========================================
   * 自动重载
   * ========================================== */

  /** 
   * 启动自动重载
   * 若当前已启用会静默处理。
   * @throws UncheckedIOException 无法启用自动重载时
   */
  private void startAutoReload() {
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
  private void stopAutoReload() {
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

  /**
   * 设置自动重载状态
   * 
   * @throws UncheckedIOException 不受检；无法启用自动重载。
   * @since 0.5.0
   */
  public SConfig setAutoReload(boolean status) {
    if (status) {
      startAutoReload();
    } else {
      stopAutoReload();
    }
    return this;
  }

  /** @return 是否正在自动重载 */
  public boolean isAutoReloading() {
    return watching;
  }

  /* ==========================================
  * 保存与自动保存
  * ========================================== */

  /**
   * 立即将缓存保存到文件中
   * @since 0.5.0
   * @throws IllegalStateException 不受检；当前状态不允许进行此操作。
   * @throws RuntimeException 不受检；无法写入文件。
   */
  public SConfig save() {
    flush();
    return this;
  }

  /**
   * 立即重新加载文件到缓存
   * 
   * @throws RuntimeException 不受检；无法加载配置文件。
   */
  public void reload() {
    load();
  }

  /**
   * 设置当前的加载模式
   * @since 0.5.0
   * @param mode 见于 {@link SConfig.WRITE_MODE}；为 null 时设为默认的 {@link SConfig.WRITE_MODE#AUTOSAVE}；不支持时视作 {@link SConfig.WRITE_MODE#INERTIA}；不区分大小写。
   */
  public SConfig setWriteMode(@Nullable String mode) {
    if (mode == null) {
      writeMode = WRITE_MODE.AUTOSAVE;
    } else {
      String m = mode.trim().toLowerCase();
      switch (m) {
        case WRITE_MODE.AUTOSAVE:
          writeMode = m;
          break;
        case WRITE_MODE.READONLY:
          writeMode = m;
          break;
        case WRITE_MODE.WRITELOCK:
          writeMode = m;
          break;
        default:
          writeMode = WRITE_MODE.INERTIA;
          break;
      }
    }
    return this;
  }

  /**
   * @since 0.5.0
   * @apiNote 加载模式可能不标准，此时应视作 {@link SConfig.WRITE_MODE#INERTIA} 。
   * @return 获取当前的加载模式，见于 {@link SConfig.WRITE_MODE}。
   */
  public String getWriteMode() {
    return writeMode;
  }

  /* ==========================================
  * 工具方法
  * ========================================== */

  /** @return 当前配置文件对象 */
  public File getFile() {
    return conf;
  }

  /** @return {@type String|null} 获取根的名称；如果当前配置格式不支持该特性返回 null 。 */
  public String getRootName() {
    if (confHandler instanceof RootNamedBackend) {
      return ((RootNamedBackend) confHandler).getRootName();
    } else {
      return null;
    }
  }

  /**
   * 设置根的名称；如果当前配置格式不支持该特性静默处理。
   * @deprecated 0.5.0中弃用，请使用 {@link #putRootName(String)} 。
   * @param name 要设置的名称
   * @return 返回自身，允许链式调用
   */
  public SConfig setRootName(String name) {
    return putRootName(name);
  }
  /**
   * 设置根的名称；如果当前配置格式不支持该特性静默处理。
   * @since 0.5.0
   * @param name 要设置的名称
   * @return 返回自身，允许链式调用
   */
  public SConfig putRootName(String name) {
    if (confHandler instanceof RootNamedBackend) {
      ((RootNamedBackend)confHandler).setRootName(name);
    }
    return this;
  }

  /** 原子替换文件：先写临时文件，再 move */
  private void atomicWrite(Path target, IOConsumer<OutputStream> outF) throws Exception {
    Path tmp = Files.createTempFile(target.toAbsolutePath().getParent(), "StreackLib.SConfig-", "." + confType + ".tmp");
    try (OutputStream out = Files.newOutputStream(tmp)) {
        outF.accept(out);
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

  /** 从 OutputStream 解析一个 Writer */
  private Writer getWriter(OutputStream out) {
    return new OutputStreamWriter(out, StandardCharsets.UTF_8);
  }
}
