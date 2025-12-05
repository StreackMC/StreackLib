package com.github.streackmc.StreackLib.utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SConfig {

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private WatchService watchService;
  private Thread watchThread;
  private volatile boolean watching = false;
  private File conf;
  private String type;
  // 缓存
  private volatile Map<String, Object> cache = new ConcurrentHashMap<>();
  private volatile long lastModified = 0;

  /**
   * 构造一个配置文件对象
   * @param file 文件对象
   * @param ctype 文件类型(json|yml|yaml)，大小写不敏感
   * @throws UnsupportedOperationException 不支持的配置文件格式时报错
   */
  public SConfig(File file, String ctype) throws UnsupportedOperationException {
    this.conf = file;
    if (ctype.toLowerCase() == "json") {
      type = "json";
    } else if (ctype.toLowerCase() == "yml" || ctype.toLowerCase() == "yaml") {
      type = "yml";
    } else {
      throw new UnsupportedOperationException("不支持的文件类型：" + ctype);
    }
    load();
  }

  /**
   * 获取当前配置文件对象
   * @return 当前的配置文件对象
   */
  public File getFile() {
    return conf;
  }

  /**
   * 
   * @param <T> 可为String/List/Int/Number
   * @param key 目标配置项，没有自动新增
   * @param fallback 默认值，如果没有传入则为空字符串
   * @return 获取到的值
   */
  @SuppressWarnings("unchecked")
  public <T> T get(String key, T fallback) {
    lock.readLock().lock();
    if (fallback == null) fallback = (T) new String();
    try {
      if (!cache.containsKey(key)) {
        put(key, fallback);
        return fallback;
      }
      T val = (T) cache.get(key);
      return val;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * 覆写配置项
   * @param <T> 可为String/List/Int/Number
   * @param key 目标配置项，没有自动新增
   * @param value 目标值
   */
  public <T> void put(String key, T value) {
    lock.writeLock().lock();
    try {
      cache.put(key, value);
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 删除配置项
   * @param key 目标配置项
   */
  public void remove(String key) {
    lock.writeLock().lock();
    try {
      cache.remove(key);
      flush();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 启动自动重载。
   * 若当前已启用则自动忽略
   */
  public void startAutoReload() {
    if (watching) return;
    try {
      watchService = FileSystems.getDefault().newWatchService();
      Path path = conf.toPath().toAbsolutePath().getParent();
      path.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
      watching = true;
      watchThread = new Thread(() -> {
        while (watching && !Thread.currentThread().isInterrupted()) {
          try {
            WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.SECONDS);
            if (key != null) {
              for (WatchEvent<?> event : key.pollEvents()) {
                Path changed = path.resolve((Path) event.context());
                if (changed.toFile().equals(conf) && conf.lastModified() > lastModified) {
                  reload();
                }
              }
              key.reset();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }, "conf-reload");
      watchThread.setDaemon(true);
      watchThread.start();
    } catch (IOException e) {
      throw new UncheckedIOException("无法启用自动重载：", e);
    }
  }

  /**
   * 停止自动重载服务
   */
  public void stopAutoReload() {
    watching = false;
    if (watchThread != null) watchThread.interrupt();
    try {
      if (watchService != null) watchService.close();
    } catch (IOException ignored) {}
  }

  /**
   * 获取自动重载状态
   * @return 是否启用了自动重载
   */
  public boolean isAutoReloading() {
    return watching;
  }

  /**
   * 调用后立即重载
   */
  public void reload() {
    load();
  }

  /**
   * 内部加载
   */
  private void load() {
    lock.writeLock().lock();
    try {
      if (!conf.exists()) {
        cache = new ConcurrentHashMap<>();
        return;
      }
      try (InputStream in = new FileInputStream(conf)) {
        if (type.equals("json")) {
          JsonElement el = JsonParser.parseReader(new InputStreamReader(in));
          if (el.isJsonObject()) {
            cache = new Gson().fromJson(el, new TypeToken<Map<String, Object>>(){}.getType());
          } else {
            cache = new ConcurrentHashMap<>();
          }
        } else if (type.equals("yml")) {
          Yaml yaml = new Yaml();
          Map<String, Object> loaded = yaml.load(in);
          cache = loaded == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(loaded);
        } else {
          throw new UnsupportedOperationException("不支持的文件类型：" + type);
        }
        lastModified = conf.lastModified();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("无法加载配置文件", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * 内部写入
   */
  private void flush() {
    lock.readLock().lock();
    try (Writer w = new OutputStreamWriter(new FileOutputStream(conf))) {
      if (type.equals("json")) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        gson.toJson(cache, w);
      } else if (type.equals("yml")) {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        new Yaml(opts).dump(cache, w);
      } else {
        throw new UnsupportedOperationException("不支持的文件类型：" + type);
      }
      lastModified = conf.lastModified();
    } catch (IOException e) {
      throw new UncheckedIOException("无法写入配置文件", e);
    } finally {
      lock.readLock().unlock();
    }
  }
}
