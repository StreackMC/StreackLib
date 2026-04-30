package com.github.streackmc.StreackLib.utils;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.github.streackmc.StreackLib.self.logger;

/**
 * 全局事件处理中心，负责事件的监听注册、移除与广播分发。
 * 仿照 JavaScript 的事件驱动模型，提供高性能的线程安全实现。
 * 你可自行分发事件或参考文档来监听StreackLib的事件。
 * 
 * <p>
 * 该类为静态工具类，不可实例化。所有方法均为线程安全。
 * 
 * <p>
 * 使用示例：
 * 
 * <pre>
 * // 监听事件（强引用，需手动移除）
 * int listenerId = SEventCentral.addEventListener("user:login", event -> {
 *   String username = event.getData("username", String.class);
 *   logger.info("用户登录:", username, "来自:", event.getCaller());
 * });
 * 
 * // 弱引用监听（自动清理，适合临时组件）
 * SEventCentral.addWeakEventListener("user:logout", event -> {
 *   // 当此 lambda 不再被外部强引用时，监听器自动失效
 * });
 * 
 * // 广播事件（链式设置数据）
 * SEventCentral.broadcastEvent("user:login")
 *     .set("username", "Streack")
 *     .set("timestamp", System.currentTimeMillis())
 *     .broadcast();
 * 
 * // 移除监听器（O(1) 时间复杂度）
 * SEventCentral.removeEventListener(listenerId);
 * </pre>
 * 
 * @author KimiAI 编写
 * @author kdxiaoyi 审计
 * @since 0.4.4
 */
public final class SEventCentral {

  /**
   * 事件构建器，用于链式设置事件数据并执行广播。
   * 
   * @author KimiAI 编写
   * @author kdxiaoyi 审计
   * @since 0.4.4
   * @see SEventCentral#broadcastEvent(String)
   */
  public final static class EventBuilder {
    private final String name;
    private final SEvent event;
    private final AtomicBoolean broadcasted = new AtomicBoolean(false);

    /**
     * 包级私有构造器，仅允许 SEventCentral 创建。
     *
     * @param name 事件名称
     */
    EventBuilder(String name, Long id) {
      this.name = name;
      this.event = new SEvent(true, id);
    }

    /**
     * 设置事件的自定义数据键值对。
     * 可以链式调用以设置多个属性。
     *
     * @param key   数据键名，不能为 null
     * @param value 数据值，可以为 null
     * @return 当前构建器实例，支持链式调用
     * @throws IllegalStateException 如果已经执行过广播
     */
    public EventBuilder set(String key, Object value) {
      if (broadcasted.get()) {
        throw new IllegalStateException("事件已广播，禁止修改数据");
      }
      Objects.requireNonNull(key, "数据键名不能为 null");
      this.event.putData(key, value);
      return this;
    }

    /**
     * 执行事件广播，将事件分发给所有已注册的监听器。
     * 广播完成后，事件对象进入只读状态，后续 set 调用将抛出异常。
     * 
     * <p>
     * 异常处理策略：单个监听器的异常不会影响其他监听器的执行，
     * 异常信息将通过 logger.serve 记录。
     */
    public void broadcast() {
      if (!broadcasted.compareAndSet(false, true)) {
        throw new IllegalStateException("事件已广播，禁止重复广播");
      }
      this.event.freeze();
      SEventCentral.dispatchEvent(name, event);
    }

  }

  /** 原子性 ID 生成器，确保监听器 ID 唯一性 */
  private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

  /**
   * 监听器存储结构。
   * 第一层：事件名 -> 监听器集合
   * 第二层：监听器 ID -> 监听器包装器（支持强/弱引用）
   */
  private static final ConcurrentHashMap<String, ConcurrentHashMap<Integer, ListenerHolder>> LISTENERS = 
      new ConcurrentHashMap<>();

  /**
   * 反向索引：监听器 ID -> 事件名。
   * 用于实现 O(1) 时间复杂度的 removeEventListener。
   */
  private static final ConcurrentHashMap<Integer, String> ID_TO_EVENT = new ConcurrentHashMap<>();

  /**
   * 监听器包装器，统一处理强引用和弱引用。
   */
  private static final class ListenerHolder {
    private final String eventName;
    private final Consumer<SEvent> strongRef;
    private final WeakReference<Consumer<SEvent>> weakRef;
    private final boolean isWeak;

    ListenerHolder(String eventName, Consumer<SEvent> listener, boolean isWeak) {
      this.eventName = eventName;
      this.isWeak = isWeak;
      if (isWeak) {
        this.strongRef = null;
        this.weakRef = new WeakReference<>(listener);
      } else {
        this.strongRef = listener;
        this.weakRef = null;
      }
    }

    Consumer<SEvent> get() {
      if (isWeak) {
        return weakRef.get();
      }
      return strongRef;
    }

    boolean isWeak() {
      return isWeak;
    }

    void cleanIfWeak() {
      if (isWeak && weakRef.get() == null) {
        // 弱引用已失效，触发清理
        SEventCentral.cleanExpiredWeakListener(eventName, this);
      }
    }
  }

  /**
   * 私有构造器，防止实例化。
   *
   * @throws UnsupportedOperationException 当尝试实例化时抛出
   */
  private SEventCentral() {
    throw new UnsupportedOperationException("SEventCentral 为静态工具类，禁止实例化");
  }

  /**
   * 为指定事件名注册监听器（强引用）。
   * 该方法线程安全，支持高并发注册。
   * 
   * <p>
   * 注意：强引用监听器不会被垃圾回收，必须使用 {@link #removeEventListener(int)} 手动移除，
   * 否则会导致内存泄漏。
   *
   * @param name     事件名称，不能为 null
   * @param listener 事件处理器（Consumer&lt;SEvent&gt;），不能为 null
   * @return 监听器唯一 ID（正整数），用于后续移除
   */
  public static int addEventListener(String name, Consumer<SEvent> listener) {
    return addListenerInternal(name, listener, false);
  }

  /**
   * 为指定事件名注册监听器（弱引用）。
   * 当监听器对象不再被任何强引用持有时，将自动从事件中心移除，无需手动清理。
   * 适合临时组件或短期监听需求。
   * 
   * <p>
   * 注意：若使用 Lambda 表达式作为监听器，确保将其赋值给变量或字段以保持强引用，
   * 否则 Lambda 可能立即被回收导致监听无效。
   *
   * @param name     事件名称，不能为 null
   * @param listener 事件处理器（Consumer&lt;SEvent&gt;），不能为 null
   * @return 监听器唯一 ID（正整数），用于监控或手动移除
   */
  public static int addWeakEventListener(String name, Consumer<SEvent> listener) {
    return addListenerInternal(name, listener, true);
  }

  /**
   * 内部注册方法。
   */
  private static int addListenerInternal(String name, Consumer<SEvent> listener, boolean isWeak) {
    Objects.requireNonNull(name, "事件名不能为 null");
    Objects.requireNonNull(listener, "监听器不能为 null");

    int id = ID_GENERATOR.incrementAndGet();
    ListenerHolder holder = new ListenerHolder(name, listener, isWeak);

    // 原子操作：添加监听器和反向索引
    LISTENERS.computeIfAbsent(name, k -> new ConcurrentHashMap<>()).put(id, holder);
    ID_TO_EVENT.put(id, name);

    return id;
  }

  /**
   * 根据 ID 移除指定的监听器。
   * 如果 ID 不存在或已被移除，静默执行。
   * 
   * <p>
   * 时间复杂度：O(1)
   *
   * @param id 监听器 ID（由 addEventListener 返回）
   */
  public static void removeEventListener(int id) {
    String eventName = ID_TO_EVENT.remove(id);
    if (eventName == null) {
      return;
    }

    ConcurrentHashMap<Integer, ListenerHolder> handlers = LISTENERS.get(eventName);
    if (handlers != null) {
      handlers.remove(id);
      // 如果该事件名下无监听器，清理空 Map 以释放内存
      if (handlers.isEmpty()) {
        LISTENERS.remove(eventName, handlers);
      }
    }
  }

  /**
   * 创建事件广播构建器，准备广播指定名称的事件。
   * 
   * <p>
   * 注意：此方法返回 {@link EventBuilder}，需要通过链式调用设置数据后，
   * 显式调用 {@link EventBuilder#broadcast()} 完成广播。
   *
   * @param name 事件名称，不能为 null
   * @param id   可选的事件 ID（用于标识发起者）
   * @return 事件构建器，用于链式设置数据和执行广播
   */
  public static EventBuilder broadcastEvent(String name, @Nullable Long id) {
    Objects.requireNonNull(name, "事件名不能为 null");
    return new SEventCentral.EventBuilder(name, id);
  }

  /**
   * 创建事件广播构建器，准备广播指定名称的事件。
   * 
   * <p>
   * 注意：此方法返回 {@link EventBuilder}，需要通过链式调用设置数据后，
   * 显式调用 {@link EventBuilder#broadcast()} 完成广播。
   *
   * @param name 事件名称，不能为 null
   * @return 事件构建器，用于链式设置数据和执行广播
   */
  public static EventBuilder broadcastEvent(String name) {
    return broadcastEvent(name, null);
  }

  /**
   * 内部事件分发方法，由 EventBuilder 调用。
   * 采用异常隔离策略，确保单个监听器异常不会中断其他监听器的执行。
   * 自动清理已失效的弱引用监听器。
   *
   * @param name  事件名
   * @param event 构造完成的事件对象（trust 已为 true）
   */
  static void dispatchEvent(String name, SEvent event) {
    ConcurrentHashMap<Integer, ListenerHolder> handlers = LISTENERS.get(name);
    if (handlers == null || handlers.isEmpty()) {
      return;
    }

    // 遍历并执行监听器，清理失效的弱引用
    handlers.entrySet().removeIf(entry -> {
      Integer id = entry.getKey();
      ListenerHolder holder = entry.getValue();
      
      Consumer<SEvent> listener = holder.get();
      if (listener == null) {
        // 弱引用已失效，清理
        ID_TO_EVENT.remove(id);
        return true; // 从 Map 中移除
      }

      try {
        listener.accept(event);
      } catch (Exception e) {
        // 使用 logger 记录异常，支持 Throwable 参数自动附加 stackTrace
        logger.severe("事件监听器执行异常 [事件=", name, ", 监听器ID=", id, "]：" + e.getLocalizedMessage(), e);
      }
      
      return false; // 保留
    });

    // 如果清理后该事件名下无监听器，移除空 Map
    if (handlers.isEmpty()) {
      LISTENERS.remove(name);
    }
  }

  /**
   * 清理已失效的弱引用监听器（由 ListenerHolder 触发）。
   */
  private static void cleanExpiredWeakListener(String eventName, ListenerHolder holder) {
    // 在下次 dispatchEvent 时会通过 removeIf 清理，此处无需立即处理
    // 若需要立即清理，可启动后台线程扫描，但通常不必要
  }

  /**
   * 获取指定事件当前的监听器数量（用于监控和调试）。
   *
   * @param name 事件名称
   * @return 监听器数量，如果事件不存在返回 0
   */
  public static int getListenerCount(String name) {
    ConcurrentHashMap<Integer, ListenerHolder> handlers = LISTENERS.get(name);
    return handlers == null ? 0 : handlers.size();
  }

  /**
   * 获取所有已注册事件的名称集合（只读）。
   *
   * @return 不可变的事件名称集合
   */
  public static java.util.Set<String> getRegisteredEvents() {
    return Collections.unmodifiableSet(LISTENERS.keySet());
  }

  /**
   * 清空所有已注册的事件监听器。
   * 此方法主要用于测试环境或系统重置场景。
   * 生产环境请谨慎使用。
   */
  protected static void removeAllListeners() {
    LISTENERS.clear();
    ID_TO_EVENT.clear();
  }
}
