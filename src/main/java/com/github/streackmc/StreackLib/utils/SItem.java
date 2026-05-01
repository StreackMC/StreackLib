package com.github.streackmc.StreackLib.utils;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.github.streackmc.StreackLib.StreackLib;

/**
 * <h3>SItem</h3>
 * 统一 Minecraft 物品接口，内部以物品堆叠组件格式存储物品信息，并支持导出为各种格式。
 * 
 * @author kdxiaoyi
 * @since 0.5.0
 * @deprecated 还没做完
 */
@Deprecated
public class SItem {
  /** 当前实例的唯一ID */
  public final Long INSTANCE_ID = StreackLib.getUniqueID();

  /** ID命名空间 */
  private String namespace = "minecraft";
  /** ID */
  private String id = "air";
  /** 物品堆叠组件 */
  private final Map<String, SConfig> isc = new ConcurrentHashMap<>();
  /** 数量 */
  private int count = 1;

  /** 默认命名空间 */
  private static final String MC = "minecraft";
  /** 空气的固定字符 */
  private static final String[] AIR = { "minecraft", "air", "minecraft:air" };
  /** missingno的固定字符，这是原版错误物品的彩蛋，继承行为 */
  private static final String[] MISSINGNO = { "minecraft", "missingno", "minecraft:missingno" };

  // ======================================================
  // 管理物品堆叠组件
  // ======================================================

  /**
   * 新建一个堆叠组件的数据部分。
   *
   * @param rawData SNBT格式的原始数据，为 null 或空字符串时创建空组件
   * @return 包含组件数据的 SConfig 实例（SNBT格式，仅内存模式）
   * @throws IOException 当无法解析数据时抛出
   * @since 0.5.0
   */
  public static SConfig createISC(String rawData) {
    return new SConfig((rawData == null || rawData.isEmpty()) ? "" : rawData,
        SConfig.TYPES.SNBT, ".snbt");
  }

  /**
   * 获取指定物品堆叠组件的数据部分，若组件不存在则创建新的空组件。
   *
   * @param namespace 命名空间，可为 null 表示使用默认 "minecraft"
   * @param name      组件名称
   * @return 对应的 SConfig 实例，可直接修改
   * @since 0.5.0
   */
  public SConfig getISC(@Nullable String namespace, String name) {
    String key = joinNamespacedID(namespace, name, MISSINGNO);
    return isc.computeIfAbsent(key, k -> {
      return createISC("");
    });
  }

  /**
   * 覆写指定堆叠组件，原有数据将被舍弃。
   *
   * @param namespace 命名空间，可为 null 表示使用默认 "minecraft"
   * @param name      组件名称
   * @param data      目标数据
   * @since 0.5.0
   */
  public SItem putISC(@Nullable String namespace, String name, SConfig data) {
    if (data.getType() != SConfig.TYPES.SNBT) {
      throw new IllegalArgumentException("预期的 SConfig 类型应为 SNBT ，但发现了 " + data.getType());
    }
    isc.put(joinNamespacedID(namespace, name, MISSINGNO), data);
    return this;
  }

  /**
   * 删除指定物品堆叠组件。
   *
   * @param namespace 命名空间，可为 null 表示使用默认 "minecraft"
   * @param name      组件名称
   * @return 当前 SItem 实例，支持链式调用
   * @since 0.5.0
   */
  public SItem removeISC(@Nullable String namespace, String name) {
    String key = joinNamespacedID(namespace, name, MISSINGNO);
    isc.remove(key);
    return this;
  }

  // ======================================================
  // 管理ID与数量
  // ======================================================

  /**
   * 设置命名空间，自动处理无效字符。为空视作 "minecraft"。
   *
   * @param namespace 命名空间，可为 null
   * @return 当前 SItem 实例，支持链式调用
   * @since 0.5.0
   */
  public SItem setNamespace(@Nullable String namespace) {
    this.namespace = parseValidString(namespace, MC);
    return this;
  }

  /**
   * 获取当前命名空间。
   *
   * @return 命名空间字符串
   * @since 0.5.0
   */
  public String getNamespace() {
    return namespace;
  }

  /**
   * 设置物品ID，自动处理无效字符。为空视作 "air"。
   *
   * @param newId 物品ID，可为 null
   * @return 当前 SItem 实例，支持链式调用
   * @since 0.5.0
   */
  public SItem setId(@Nullable String newId) {
    this.id = parseValidString(newId, AIR[1]);
    return this;
  }

  /**
   * 获取当前物品ID。
   *
   * @return 物品ID字符串
   * @since 0.5.0
   */
  public String getId() {
    return id;
  }

  /**
   * 设置完整ID（命名空间:ID），自动处理无效字符。为空视作 "minecraft:air"。
   *
   * @param fullId 完整的物品标识符，如 "minecraft:apple"
   * @return 当前 SItem 实例，支持链式调用
   * @since 0.5.0
   */
  public SItem setNamespacedID(String fullId) {
    if (fullId == null || fullId.trim().isEmpty()) {
      this.namespace = MC;
      this.id = AIR[1];
      return this;
    }
    String[] parts = fullId.split(":", 2);
    if (parts.length == 2) {
      this.namespace = parseValidString(parts[0], MC);
      this.id = parseValidString(parts[1], AIR[1]);
    } else {
      this.namespace = MC;
      this.id = parseValidString(parts[0], AIR[1]);
    }
    return this;
  }

  /**
   * 获取完整的物品标识符（命名空间:ID）。
   *
   * @return 格式为 "namespace:id" 的字符串
   * @since 0.5.0
   */
  public String getNamespacedID() {
    return namespace + ":" + id;
  }

  /**
   * 设置数量。
   *
   * @param newCount 任意整数，为负数时自动取相反数，为 0 时继续保存，并且会导致部分行为有不同。
   * @return 当前 SItem 实例，支持链式调用
   * @since 0.5.0
   */
  public SItem setCount(int newCount) {
    this.count = (newCount < 0) ? -newCount : newCount;
    return this;
  }

  /**
   * 获取物品数量。
   *
   * @return 当前数量
   * @since 0.5.0
   */
  public int getCount() {
    return count;
  }

  // ======================================================
  // 工具方法
  // ======================================================

  /**
   * 语义化输入文本，使之（不为空白或Null）且已被标准化。
   * 注意只会保留 小写字母 a-z / 数字 0-9 / 下划线 _ / 点 . 。
   *
   * @param input    输入字符串
   * @param defaultS 默认值，当 input 无效时使用
   * @return 标准化后的字符串
   * @since 0.5.0
   */
  private static String parseValidString(@Nullable String input, @Nullable String defaultS) {
    // 选择有效文本
    String txt;
    if (input == null || input.trim().isEmpty()) {
      txt = (defaultS == null) ? "" : defaultS;
    } else {
      txt = input;
    }
    // 标准化：转小写，移除非法字符
    return txt.trim().toLowerCase().replaceAll("[^a-z0-9_.]", "");
  }

  /**
   * 将两串文本按顺序解析为完整的物品ID，格式为 "namespace:id"。
   *
   * @param namespace 命名空间，可为 null 使用默认值
   * @param id        物品ID，可为 null 使用默认值
   * @param token     默认模式数组，推荐使用 {@link #AIR} 或 {@link #MISSINGNO}
   * @return 完整的物品标识符
   * @since 0.5.0
   */
  private static String joinNamespacedID(@Nullable String namespace, @Nullable String id, String[] token) {
    // 确定默认值来源
    String[] defaults;
    if (token == AIR) {
      defaults = AIR;
    } else if (token == MISSINGNO) {
      defaults = MISSINGNO;
    } else if (token != null && (token.length == 2 || token.length == 3)) {
      // 创建新的默认数组，长度固定为3，便于统一处理
      defaults = new String[3];
      defaults[0] = parseValidString(token[0], MISSINGNO[0]);
      defaults[1] = parseValidString(token[1], MISSINGNO[1]);
      defaults[2] = parseValidString(token.length > 2 ? token[2] : (token[0] + ":" + token[1]), MISSINGNO[2]);
    } else {
      defaults = MISSINGNO;
    }

    String ns = (namespace == null) ? defaults[0] : parseValidString(namespace, defaults[0]);
    String i = (id == null) ? defaults[1] : parseValidString(id, defaults[1]);
    return ns + ":" + i;
  }

  // ======================================================
  // 解析已有物品、新建
  // ======================================================

  /**
   * 新建一个 minecraft:air * 1 。请使用链式方法修改本空白物品。
   * 
   * @see SItem.PARSE
   */
  public SItem() {
  }

}