package com.github.streackmc.StreackLib.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.github.streackmc.StreackLib.self.manager;

/**
 * 事件数据类型，仿照 JavaScript Event 模型设计。
 * 包含不可变的元数据（timestamp、trust、caller）和自定义业务数据（data）。
 * 
 * <p>
 * 线程安全：该类的实例一旦构造完成即为线程安全且不可变（外部视角）。
 * 内部使用 volatile 和并发容器确保安全发布。
 * 
 * @author KimiAI 编写
 * @author kdxiaoyi 审计
 * @since 0.4.4
 */
final public class SEvent {
  private final long timestamp;
  private final boolean trust;
  private final String caller;
  private volatile Map<String, Object> data; // volatile 确保安全发布

  /**
   * 公共构造器，供外部代码构造事件。
   * 构造的事件 trust 属性为 false，表示非受信任来源。
   */
  public SEvent() {
    this(false);
  }

  /**
   * 包级私有构造器，仅允许 SEventCentral 创建受信任事件。
   *
   * @param trust 是否为受信任事件（true 表示由 SEventCentral 发起）
   */
  SEvent(boolean trust) {
    this.timestamp = System.currentTimeMillis();
    this.trust = trust;

    List<String> callers = manager.getCaller(manager.getCallerMethod.FOR_SEVENT);
    this.caller = (callers != null && !callers.isEmpty()) ? callers.get(0) : "unknown";

    // 使用 ConcurrentHashMap 确保构建阶段线程安全（即使不当共享）
    this.data = new ConcurrentHashMap<>();
  }

  /**
   * 获取事件构造时的时间戳。
   *
   * @return 毫秒级时间戳（System.currentTimeMillis()）
   */
  public long getTimestamp() {
    return timestamp;
  }

  /**
   * 判断事件是否为受信任事件。
   * 只有由 SEventCentral 广播的事件才返回 true。
   *
   * @return true 如果事件由事件中心发起，否则 false
   */
  public boolean isTrust() {
    return trust;
  }

  /**
   * 获取事件构造时的调用者标识。
   *
   * @return 调用者字符串，通常为类名或标识符
   */
  public String getCaller() {
    return caller;
  }

  /**
   * 获取指定键的自定义数据。
   *
   * @param key 数据键名
   * @return 对应的值，如果不存在返回 null
   */
  public Object getData(String key) {
    return data.get(key);
  }

  /**
   * 获取指定键的自定义数据并进行类型转换。
   *
   * @param <T>  期望的类型
   * @param key  数据键名
   * @param type 目标类型的 Class 对象
   * @return 转换后的值，如果不存在返回 null
   * @throws ClassCastException 如果值无法转换为指定类型
   */
  public <T> T getData(String key, Class<T> type) {
    Object value = data.get(key);
    if (value == null) {
      return null;
    }
    if (type.isInstance(value)) {
      return type.cast(value);
    }
    throw new ClassCastException(
        String.format("无法将 %s 转换为 %s", value.getClass().getName(), type.getName()));
  }

  /**
   * 获取所有自定义数据的只读视图。
   * 注意：若在广播前调用此方法，返回的 Map 仍然是可变的。
   *
   * @return 不可变的 Map 视图
   */
  public Map<String, Object> getAllData() {
    return Collections.unmodifiableMap(data);
  }

  /**
   * 包级私有的数据写入方法，仅允许同包（SEventCentral/EventBuilder）调用。
   * 在事件广播前用于设置数据。
   *
   * @param key   键
   * @param value 值，若为null视作空字符串
   */
  void putData(String key, @Nullable Object value) {
    if (!(this.data instanceof ConcurrentHashMap)) {
      throw new IllegalStateException("事件已冻结，禁止修改数据");
    }
    if (value == null) {
      this.data.put(key, "<null>");
    } else {
      this.data.put(key, value);
    }
  }

  /**
   * 冻结事件数据，确保广播后不可变性。
   * 将内部 Map 替换为不可变视图，防止任何后续修改。
   */
  void freeze() {
    // 转换为不可变 Map 并安全发布（volatile 写）
    this.data = Collections.unmodifiableMap(new HashMap<>(this.data));
  }
}