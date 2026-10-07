package com.github.streackmc.StreackLib.types.SUnixDomainSocket;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

/**
 * <h2>SUDSPayload</h2>
 * 
 * {@link SUDSAbsLink} 链路上一条报文的数据载体。它同时持有两样东西，二者总是配套的：
 * 
 * <ul>
 * <li>{@link #getData()} —— <b>原始字节流</b>，即链路上真正跑着的那些字节；</li>
 * <li>{@link #getValue()} —— <b>解析后的值</b>，由泛型参数 {@code T} 决定其类型。</li>
 * </ul>
 * 
 * <h3>按链路协议决定语义</h3>
 * 
 * <table border="1">
 * <caption>协议模式与载荷语义</caption>
 * <tr><th>链路协议</th><th>{@code T}</th><th>{@link #getData()}</th><th>{@link #getValue()}</th></tr>
 * <tr><td>{@link SUDSProtocol#JSON_LINES}</td><td>{@code Map<String, Object>}</td>
 * <td>JSON 文本的字节（<b>不含</b>结尾 LF）</td><td>解析出的 {@code Map}</td></tr>
 * <tr><td>{@link SUDSProtocol#RAW}</td><td>{@code byte[]}</td>
 * <td>原始字节块</td><td>与 {@code getData()} 等价的字节数组</td></tr>
 * </table>
 * 
 * 换句话说：<b>自定义协议下 Payload 就只是一个字节流容器</b>，链路不会替你解读任何内容；
 * 内置协议下链路已经替你完成了分帧与 JSON 解析，{@link #getValue()} 直接可用。
 * 
 * <h3>扩展性</h3>
 * 
 * 泛型参数 {@code T} 就是扩展点：{@link #json(byte[], Class)} 与 {@link #json(byte[], Type)}
 * 可以把同一段字节直接解析成你自己的 POJO，而 {@link #of(byte[], Object, boolean)} 允许自定义协议
 * 塞入任意类型的载荷。若只想临时换一种解读方式，用 {@link #getValue(Class)} 二次解析即可，
 * 不需要重新构造对象。
 * 
 * <h3>线程安全</h3>
 * 
 * 本类为不可变对象（{@link #getData()} 返回拷贝），可安全地跨线程传递；解引用出的 {@code Map}
 * 由 Gson 一次性构造，之后不再被修改。
 * 
 * @param <T> 解析后载荷的类型；{@link SUDSProtocol#JSON_LINES} 下默认是 {@code Map<String, Object>}，
 *            {@link SUDSProtocol#RAW} 下是 {@code byte[]}
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 * @see SUDSProtocol
 * @see SUDSAbsLink
 */
public class SUDSPayload<T> extends StreackLibNewable {

  /** 共享的 Gson 实例，Gson 本身是线程安全的无状态对象 */
  private static final Gson GSON = new Gson();

  /** {@code Map<String, Object>} 的类型字面量，避免每次解析都重新构造 */
  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
  }.getType();

  /** 链路上的原始字节 */
  private final byte[] data;
  /** 解析后的载荷 */
  private final T value;
  /** 是否来自（或用于）自定义协议 */
  private final boolean raw;

  private SUDSPayload(byte[] data, T value, boolean raw) {
    this.data = data == null ? new byte[0] : data;
    this.value = value;
    this.raw = raw;
  }

  // ----------------------------------------------------------------
  // 静态工厂：自定义协议（RAW）
  // ----------------------------------------------------------------

  /**
   * 用原始字节构造一个自定义协议载荷，发送与接收两个方向都适用。
   * 
   * @param data 原始字节，会被拷贝；为 null 时视作空字节流
   * @return 载荷，其中 {@code T} 为 {@code byte[]}，{@link #getValue()} 与 {@link #getData()} 内容一致
   * @since 0.6.2
   */
  public static SUDSPayload<byte[]> raw(byte[] data) {
    byte[] copy = data == null ? new byte[0] : data.clone();
    return new SUDSPayload<>(copy, copy.clone(), true);
  }

  /**
   * 用原始字节的一个切片构造自定义协议载荷。
   * 
   * @param data   原始字节数组
   * @param offset 起始下标
   * @param length 长度
   * @return 载荷
   * @throws IndexOutOfBoundsException 如果 {@code offset} / {@code length} 越界
   * @since 0.6.2
   */
  public static SUDSPayload<byte[]> raw(byte[] data, int offset, int length) {
    return raw(Arrays.copyOfRange(data, offset, offset + length));
  }

  // ----------------------------------------------------------------
  // 静态工厂：内置协议（JSON_LINES）
  // ----------------------------------------------------------------

  /**
   * 解析一条 JSON 行报文。
   * 
   * @param line 一段 JSON <b>对象</b>文本的字节，可以带结尾的 LF/CRLF（接收路径会保留它）
   * @return 载荷，{@code T} 为 {@code Map<String, Object>}
   * @throws IllegalArgumentException 如果内容不是合法的 JSON，或根节点不是 JSON 对象
   * @since 0.6.2
   */
  public static SUDSPayload<Map<String, Object>> json(byte[] line) {
    return json(line, MAP_TYPE);
  }

  /**
   * 解析一条 JSON 行报文并直接映射为指定类。
   * <p>
   * 这是 {@code SUDSPayload<T>} 的主要扩展点：让链路上的 JSON 一步变成你自己的 POJO。
   * 与 {@link #json(byte[])} 不同，此重载<b>不限制</b> JSON 根节点的种类。
   * 
   * @param <R>  目标类型
   * @param line 一段 JSON 文本的字节
   * @param type 目标类型
   * @return 载荷，{@code T} 为 {@code R}
   * @throws IllegalArgumentException 如果内容不是合法的 JSON，或无法映射为目标类型
   * @since 0.6.2
   */
  public static <R> SUDSPayload<R> json(byte[] line, Class<R> type) {
    return json(line, (Type) type);
  }

  /**
   * 解析一条 JSON 行报文并直接映射为指定类型。
   * <p>
   * 需要泛型信息（如 {@code List<MyPojo>}、{@code Map<String, MyPojo>}）时使用本重载，
   * 类型可由 {@code TypeToken} 提供。
   * 
   * @param <R>  目标类型
   * @param line 一段 JSON 文本的字节
   * @param type 目标类型，可为带泛型的 {@link Type}
   * @return 载荷，{@code T} 为 {@code R}
   * @throws IllegalArgumentException 如果内容不是合法的 JSON，或无法映射为目标类型
   * @since 0.6.2
   */
  @SuppressWarnings("unchecked")
  public static <R> SUDSPayload<R> json(byte[] line, Type type) {
    if (type == null)
      throw new NullPointerException("Target type cannot be null.");
    byte[] raw = line == null ? new byte[0] : line.clone();
    // 结尾的 LF / CRLF 属于链路的分帧信息，不属于报文内容，这里统一剥掉，
    // 使得「收到的载荷」与「要发出的载荷」拿到的是同一种字节
    String text = new String(raw, StandardCharsets.UTF_8).trim();
    if (text.isEmpty())
      throw new IllegalArgumentException("Empty text cannot be parsed as a JSON payload.");

    JsonElement element;
    try {
      element = JsonParser.parseString(text);
    } catch (Exception e) {
      throw new IllegalArgumentException("Malformed JSON payload: " + e.getLocalizedMessage(), e);
    }

    // Map 的默认入口要求根节点是对象，其余重载放开限制
    if (MAP_TYPE.equals(type) && !element.isJsonObject())
      throw new IllegalArgumentException(
          "The built-in JSON protocol requires a JSON object as root, but got: ["
              + rootKind(element) + "]. Use json(byte[], Type) if you need another root kind.");

    try {
      Object parsed = GSON.fromJson(element, type);
      if (MAP_TYPE.equals(type))
        parsed = new LinkedHashMap<>((Map<String, Object>) parsed);
      return new SUDSPayload<>(text.getBytes(StandardCharsets.UTF_8), (R) parsed, false);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Cannot map JSON payload to [" + type + "]: " + e.getLocalizedMessage(), e);
    }
  }

  /**
   * 把一个对象序列化为一条 JSON 报文，用于发送。
   * <p>
   * 结尾的 LF 由链路在写入时统一追加，因此 {@link #getData()} 里只有 JSON 文本本身——
   * 这样「收到再转发」与「自己新建」两种载荷拿到的字节是一致的。
   * 
   * @param <R>   载荷类型
   * @param value 待序列化的对象，序列化结果必须是一个 JSON <b>对象</b>
   * @return 载荷，其中 {@link #getData()} 是 JSON 文本的 UTF-8 字节
   * @throws NullPointerException     如果 value 为 null
   * @throws IllegalArgumentException 如果序列化结果不是 JSON 对象
   * @since 0.6.2
   */
  public static <R> SUDSPayload<R> ofJson(R value) {
    if (value == null)
      throw new NullPointerException("Payload value cannot be null, use an empty Map instead.");
    // 按运行期类型序列化，Map 与 POJO 都能正确展开
    String text = GSON.toJson(value);
    JsonElement element = JsonParser.parseString(text);
    if (!element.isJsonObject())
      throw new IllegalArgumentException(
          "The built-in JSON protocol requires a JSON object as root, but [" + value.getClass().getName()
              + "] serialized to: [" + rootKind(element) + "].");
    return new SUDSPayload<>(text.getBytes(StandardCharsets.UTF_8), value, false);
  }

  // ----------------------------------------------------------------
  // 静态工厂：通用扩展点
  // ----------------------------------------------------------------

  /**
   * 直接用「原始字节 + 已解析值」构造载荷，供自定义协议自行扩展。
   * 
   * @param <R>  载荷类型
   * @param data 链路上的原始字节
   * @param value 与该字节对应的解析值
   * @param raw  是否属于自定义协议；只有为 true 的载荷才能在 {@link SUDSProtocol#RAW} 链路上发送，
   *             反之亦然
   * @return 载荷
   * @since 0.6.2
   */
  public static <R> SUDSPayload<R> of(byte[] data, R value, boolean raw) {
    return new SUDSPayload<>(data, value, raw);
  }

  // ----------------------------------------------------------------
  // 实例访问器
  // ----------------------------------------------------------------

  /**
   * 获取链路上的原始字节流。
   * 
   * @apiNote 返回的是<b>拷贝</b>，调用方可以随意改动而不影响本对象。若在热路径上只为写出去，
   *          链路内部走的是不拷贝的通道。
   * @return 原始字节，永不为 null
   * @since 0.6.2
   */
  public byte[] getData() {
    return data.clone();
  }

  /**
   * 获取解析后的载荷。
   * 
   * @return 解析值；{@link SUDSProtocol#JSON_LINES} 下为 {@code Map<String, Object>}，
   *         {@link SUDSProtocol#RAW} 下为 {@code byte[]}
   * @since 0.6.2
   */
  public T getValue() {
    return value;
  }

  /**
   * @return 该载荷是否属于自定义协议（{@link SUDSProtocol#RAW}）
   * @since 0.6.2
   */
  public boolean isRaw() {
    return raw;
  }

  /**
   * @return 原始字节的长度，不会产生拷贝
   * @since 0.6.2
   */
  public int length() {
    return data.length;
  }

  /**
   * 把原始字节按 UTF-8 解码为字符串。
   * 
   * @return 文本形式；{@link SUDSProtocol#JSON_LINES} 下即那条 JSON 原文（可能带结尾 LF）
   * @since 0.6.2
   */
  public String asString() {
    return new String(data, StandardCharsets.UTF_8);
  }

  /**
   * 把原始字节解析为 JSON 对象视图。
   * <p>
   * 与 {@link #getValue()} 的区别在于：无论构造方式是 {@code T = MyPojo}、{@code T = byte[]}
   * 还是 {@code T = List}，本方法都尝试把字节本身当成 JSON 对象重新解析一次。
   * 
   * @return JSON 对象的键值视图
   * @throws IllegalArgumentException 如果原始字节不是合法的 JSON，或根节点不是对象
   * @since 0.6.2
   */
  public Map<String, Object> asMap() {
    SUDSPayload<Map<String, Object>> parsed = json(data, MAP_TYPE);
    return parsed.getValue();
  }

  /**
   * 把原始字节解析为 {@link SConfig}，便于复用 SConfig 的 {@code getString/getInt/...} 系列取值接口。
   * 
   * @return 内存模式下的 JSON 配置对象；原始字节不是 JSON 对象时其内容为空
   * @since 0.6.2
   */
  public SConfig asConfig() {
    return new SConfig(asString(), SConfig.TYPES.JSON, null);
  }

  /**
   * 把原始字节按 JSON 重新解析为另一种类型。
   * <p>
   * 用于「同一个载荷，不同消费者要看成不同东西」的场景，避免重新构造对象。
   * 
   * @param <R>  目标类型
   * @param type 目标类型
   * @return 重新解析的结果
   * @throws IllegalArgumentException 如果原始字节不是合法的 JSON，或无法映射为目标类型
   * @since 0.6.2
   */
  public <R> R getValue(Class<R> type) {
    // 必须先落到局部变量，否则泛型 R 会被推断为 Object
    SUDSPayload<R> parsed = json(data, type);
    return parsed.getValue();
  }

  /**
   * 把原始字节按 JSON 重新解析为另一种类型，支持泛型。
   * 
   * @param <R>  目标类型
   * @param type 目标类型，可为带泛型的 {@link Type}
   * @return 重新解析的结果
   * @throws IllegalArgumentException 如果原始字节不是合法的 JSON，或无法映射为目标类型
   * @since 0.6.2
   */
  public <R> R getValue(Type type) {
    // 必须先落到局部变量，否则泛型 R 会被推断为 Object
    SUDSPayload<R> parsed = json(data, type);
    return parsed.getValue();
  }

  // ----------------------------------------------------------------
  // 包内接口
  // ----------------------------------------------------------------

  /**
   * 取得内部字节数组的引用（<b>不拷贝</b>），仅供同包的链路写入使用。
   * 
   * @return 内部字节数组本身，调用方<b>不得</b>修改
   */
  byte[] rawForWrite() {
    return data;
  }

  @Override
  public String toString() {
    String preview;
    try {
      preview = value == null ? "null" : String.valueOf(value);
    } catch (Exception e) {
      preview = "<unprintable>";
    }
    if (preview.length() > 512)
      preview = preview.substring(0, 512) + "...";
    return "SUDSPayload{raw=" + raw + ", bytes=" + data.length + ", value=" + preview + "}";
  }

  // ----------------------------------------------------------------
  // 内部工具
  // ----------------------------------------------------------------

  private static String rootKind(JsonElement element) {
    if (element.isJsonNull())
      return "null";
    if (element.isJsonArray())
      return "array";
    if (element.isJsonObject())
      return "object";
    if (element.isJsonPrimitive())
      return element.getAsJsonPrimitive().isString() ? "string" : "primitive";
    return "unknown";
  }
}
