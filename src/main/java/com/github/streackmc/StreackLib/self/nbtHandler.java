package com.github.streackmc.StreackLib.self;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.jetbrains.annotations.ApiStatus.Internal;

import net.querz.nbt.tag.ByteArrayTag;
import net.querz.nbt.tag.ByteTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.DoubleTag;
import net.querz.nbt.tag.FloatTag;
import net.querz.nbt.tag.IntArrayTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;
import net.querz.nbt.tag.LongTag;
import net.querz.nbt.tag.ShortTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;

/**
 * 内部用，将 Querz/NBT 的 NBT 数据与 Java 数据互相翻译
 * @author kdxiaoyi
 */
@Internal
public class nbtHandler {
  /**
   * 根据输入流前两个字节判断输入是否为 GZIP 压缩数据。
   *
   * <p>
   * 如果传入了{@link PushbackInputStream}，会自动回推已读取的字节，以保证不改变原始输入流的可读性。
   * </p>
   *
   * @param in 要检测的输入流（可以是任意 InputStream）
   * @return 如果流以 GZIP 魔数开头则返回 true，否则返回 false
   * @throws IOException 发生 I/O 错误时抛出
   */
  public static boolean detectGZIP(InputStream in) throws IOException {
    PushbackInputStream pbIn;
    if (in instanceof PushbackInputStream) {
      pbIn = (PushbackInputStream) in;
    } else {
      pbIn = new PushbackInputStream(in, 2);
    }
    int first = pbIn.read() & 0xFF;
    int second = pbIn.read() & 0xFF;
    pbIn.unread(second);
    pbIn.unread(first);
    int signature = (second << 8) | first;
    return signature == GZIPInputStream.GZIP_MAGIC;
  }

  /**
   * 将 CompoundTag 转换为 Java Map（保持插入顺序）。
   *
   * @param compound 要转换的 CompoundTag（不为 null）
   * @return 等价的 Map<String, Object>
   */
  public static Map<String, Object> Compound2Map(CompoundTag compound) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (String key : compound.keySet()) {
      Tag<?> tag = compound.get(key);
      result.put(key, Tag2Java(tag));
    }
    return result;
  }

  /**
   * 递归将任意 NBT Tag 转换为 Java 对象：
   * - StringTag -> String
   * - ByteTag -> Boolean (1 -> true, 0 -> false)
   * - Numeric Tags -> 对应的 Number
   * - Array/Compound/List -> 对应的 List/Map/嵌套结构
   *
   * @param tag 要转换的 Tag，可能为 null
   * @return 转换后的 Java 对象，tag 为 null 则返回 null
   * @throws UnsupportedOperationException 遇到未知 Tag 类型时抛出
   */
  public static Object Tag2Java(Tag<?> tag) {
    if (tag == null)
      return null;

    // 基本类型
    if (tag instanceof StringTag) {
      return ((StringTag) tag).getValue();
    } else if (tag instanceof ByteTag) {
      return ((ByteTag) tag).asByte() == 1;
    } else if (tag instanceof ShortTag) {
      return ((ShortTag) tag).asShort();
    } else if (tag instanceof IntTag) {
      return ((IntTag) tag).asInt();
    } else if (tag instanceof LongTag) {
      return ((LongTag) tag).asLong();
    } else if (tag instanceof FloatTag) {
      return ((FloatTag) tag).asFloat();
    } else if (tag instanceof DoubleTag) {
      return ((DoubleTag) tag).asDouble();

      // 数组类型
    } else if (tag instanceof ByteArrayTag) {
      byte[] data = ((ByteArrayTag) tag).getValue();
      List<Boolean> list = new ArrayList<>(data.length);
      for (byte b : data) {
        list.add(b == 1);
      }
      return list;
    } else if (tag instanceof IntArrayTag) {
      int[] data = ((IntArrayTag) tag).getValue();
      List<Integer> list = new ArrayList<>(data.length);
      for (int i : data)
        list.add(i);
      return list;
    } else if (tag instanceof LongArrayTag) {
      long[] data = ((LongArrayTag) tag).getValue();
      List<Long> list = new ArrayList<>(data.length);
      for (long l : data)
        list.add(l);
      return list;

      // 复合类型
    } else if (tag instanceof CompoundTag) {
      return Compound2Map((CompoundTag) tag);
    } else if (tag instanceof ListTag) {
      ListTag<?> listTag = (ListTag<?>) tag;
      List<Object> list = new ArrayList<>(listTag.size());
      for (Tag<?> element : listTag) {
        list.add(Tag2Java(element));
      }
      return list;
    }

    throw new UnsupportedOperationException("不支持的 Tag 类型: " + tag.getClass().getSimpleName());
  }

  /**
   * 将 Java Map 递归转换为 NBT CompoundTag。
   * 支持的类型映射：
   * <ul>
   *   <li>String -> StringTag</li>
   *   <li>Boolean -> ByteTag (1/0)</li>
   *   <li>Number (Byte/Short/Integer/Long/Float/Double) -> 对应数字 Tag</li>
   *   <li>List -> 智能转换：全 Boolean -> ByteArrayTag；全 Integer -> IntArrayTag；全 Long -> LongArrayTag；否则 ListTag</li>
   *   <li>Map -> CompoundTag</li>
   *   <li>null -> 忽略（不会放入）</li>
   * </ul>
   *
   * @param map 要转换的 Map（键为 String）
   * @return 生成的 CompoundTag
   */
  public static CompoundTag Map2Compound(Map<String, Object> map) {
    CompoundTag compound = new CompoundTag();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (value == null)
        continue;

      Tag<?> tag = Java2Tag(value);
      compound.put(key, tag);
    }
    return compound;
  }

  /**
   * 将任意受支持的 Java 对象转换为 NBT Tag。
   *
   * 支持类型参见 translateMapData 中的说明。
   *
   * @param obj 要转换的对象（不能为 null）
   * @return 对应的 Tag
   * @throws UnsupportedOperationException 遇到不支持的类型时抛出
   */
  public static Tag<?> Java2Tag(Object obj) throws UnsupportedOperationException {
    // 先转基本类型
    if (obj instanceof String) {
      return new StringTag((String) obj);
    } else if (obj instanceof Boolean) {
      return new ByteTag((byte) ((Boolean) obj ? 1 : 0));
    } else if (obj instanceof Byte) {
      return new ByteTag((Byte) obj);
    } else if (obj instanceof Short) {
      return new ShortTag((Short) obj);
    } else if (obj instanceof Integer) {
      return new IntTag((Integer) obj);
    } else if (obj instanceof Long) {
      return new LongTag((Long) obj);
    } else if (obj instanceof Float) {
      return new FloatTag((Float) obj);
    } else if (obj instanceof Double) {
      return new DoubleTag((Double) obj);

    // 如果遇到 Map 就递归解析为 CompoundTag
    } else if (obj instanceof Map) {
      @SuppressWarnings("unchecked") // SConfig内部使用，默认Map符合规范
      Map<String, Object> map = (Map<String, Object>) obj;
      return Map2Compound(map);

    // 开始解析数组
    } else if (obj instanceof List) {
      List<?> list = (List<?>) obj;

      // 数组为空
      if (list.isEmpty()) {
        return new ListTag<>(ByteTag.class);
      }

      // 基本数组
      try {
        Object first = list.get(0);
        if (first instanceof Boolean) {
          // 全 Boolean -> ByteArrayTag
          byte[] bytes = new byte[list.size()];
          for (int i = 0; i < list.size(); i++) {
            Boolean b = (Boolean) list.get(i);
            bytes[i] = (byte) (b ? 1 : 0);
          }
          return new ByteArrayTag(bytes);
        } else if (first instanceof Byte) {
          // 全 Byte -> ByteArrayTag
          byte[] bytes = new byte[list.size()];
          for (byte i = 0; i < list.size(); i++) {
            bytes[i] = (byte) list.get(i);
          }
          return new ByteArrayTag(bytes);
        } else if (first instanceof Integer) {
          // 全 Integer -> IntArrayTag
          int[] ints = new int[list.size()];
          for (int i = 0; i < list.size(); i++) {
            ints[i] = (Integer) list.get(i);
          }
          return new IntArrayTag(ints);
        } else if (first instanceof Long) {
          // 全 Long -> LongArrayTag
          long[] longs = new long[list.size()];
          for (int i = 0; i < list.size(); i++) {
            longs[i] = (Long) list.get(i);
          }
          return new LongArrayTag(longs);
        }
        throw new UnsupportedOperationException("不支持的类型：" + first.getClass());
      } catch (Exception e) {
        throw new UnsupportedOperationException("尝试解析数组为 NBT 时发现数组元素类型不正确或不唯一：" + e.getLocalizedMessage(), e);
      }
    } else {
      throw new UnsupportedOperationException("不支持转换为 NBT 的类型: " + obj.getClass());
    }
  }

  private nbtHandler() {}
}
