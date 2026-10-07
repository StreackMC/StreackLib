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
 * {@link SUDSAbsLink} 链路上一条报文的数据载体，设计上对齐 JavaScript 的
 * {@code Response}/{@code MessageEvent.data}：<b>它只是 body，不带头部</b>。
 * 
 * <h3>发送侧与消费侧是对称的</h3>
 * 
 * 构造载荷（发送）与读取载荷（消费）用的是同一组名字，读到哪一侧都猜得到对面怎么写：
 * 
 * <table border="1">
 * <caption>对称的 API</caption>
 * <tr><th>构造（发送）</th><th>消费（接收）</th></tr>
 * <tr><td>{@link #json(Object) json(Object)}</td><td>{@link #json() json()} → {@code Map}</td></tr>
 * <tr><td>{@link #text(String) text(String)}</td><td>{@link #text() text()} → {@code String}</td></tr>
 * <tr><td>{@link #bytes(byte[]) bytes(byte[])}</td><td>{@link #bytes() bytes()} → {@code byte[]}</td></tr>
 * <tr><td>—</td><td>{@link #config() config()} → {@code SConfig}</td></tr>
 * <tr><td>—</td><td>{@link #as(Class) as(Class)} → 你自己的类型</td></tr>
 * </table>
 * 
 * 用法上就是 JavaScript 的手感：
 * 
 * <pre>{@code
 * server.onMessage((peer, payload) -> {
 *   String type = (String) payload.json().get("type");   // 收到什么才决定怎么看
 *   peer.send(SUDSPayload.json(Map.of("ok", true)));
 * });
 * }</pre>
 * 
 * <h3>视图是按需的，不是构造时定死的</h3>
 * 
 * 一条载荷同时是字节、文本、JSON 对象——具体看哪一面由消费方在<b>读取时</b>决定，
 * 不需要在还没解析前就先声明类型。同一份 JSON 只会被解析一次，结果会被缓存，
 * {@link #json()} 反复调用不会重复解析。
 * 
 * <h3>线程安全</h3>
 * 
 * 字节数组不可变（{@link #bytes()} 返回拷贝），可安全跨线程传递；
 * {@link #json()} 返回的是<b>同一个缓存实例</b>，多个监听器会看到同一个 Map，
 * 改动它等于改动这条载荷。
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 * @see SUDSProtocol
 * @see SUDSAbsLink
 */
public class SUDSPayload extends StreackLibNewable {

  /** 共享的 Gson 实例，Gson 本身是线程安全的无状态对象 */
  private static final Gson GSON = new Gson();

  /** {@code Map<String, Object>} 的类型字面量，避免每次解析都重新构造 */
  private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
  }.getType();

  /** 链路上的原始字节 */
  private final byte[] data;
  /** 是否属于自定义协议（{@link SUDSProtocol#RAW}） */
  private final boolean raw;
  /** JSON 视图的缓存；为 null 表示尚未解析过 */
  private volatile Map<String, Object> mapCache;

  /**
   * @param data       链路上的原始字节
   * @param raw        是否属于自定义协议
   * @param preParsed  已经解析好的 JSON 对象视图，可为 null（表示等 {@link #json()} 时再解析）
   */
  private SUDSPayload(byte[] data, boolean raw, Map<String, Object> preParsed) {
    this.data = data == null ? new byte[0] : data;
    this.raw = raw;
    this.mapCache = preParsed;
  }

  // ----------------------------------------------------------------
  // 构造：发送侧
  // ----------------------------------------------------------------

  /**
   * 把一个对象序列化成一条 JSON 报文。
   * 
   * @param value 待序列化的对象，序列化结果必须是一个 JSON <b>对象</b>
   * @return 载荷；结尾的 LF 由链路在写入时追加，因此 {@link #bytes()} 里只有 JSON 文本本身
   * @throws NullPointerException     如果 value 为 null（空对象请传空 Map）
   * @throws IllegalArgumentException 如果序列化结果不是 JSON 对象
   * @since 0.6.2
   */
  public static SUDSPayload json(Object value) {
    if (value == null)
      throw new NullPointerException("载荷内容不能为 null，空对象请传一个空 Map。");
    String text = GSON.toJson(value);
    JsonElement element = JsonParser.parseString(text);
    if (!element.isJsonObject())
      throw new IllegalArgumentException(
          "内置 JSON 协议要求报文根节点是对象，但 [" + value.getClass().getName()
              + "] 序列化成了 [" + rootKind(element) + "]。若确需非对象根，请改用手工字节 + of(bytes, false)。");
    return new SUDSPayload(text.getBytes(StandardCharsets.UTF_8), false, null);
  }

  /**
   * 用一段文本构造载荷，字节为该文本的 UTF-8 编码。
   * <p>
   * 文本没有 JSON 语义，因此这是 {@link SUDSProtocol#RAW} 侧的构造方式；
   * 内置 JSON 协议下的报文必须是 JSON 对象，请用 {@link #json(Object)}。
   * 
   * @param value 文本内容
   * @return 载荷
   * @throws NullPointerException 如果 value 为 null
   * @since 0.6.2
   */
  public static SUDSPayload text(String value) {
    if (value == null)
      throw new NullPointerException("载荷内容不能为 null。");
    return new SUDSPayload(value.getBytes(StandardCharsets.UTF_8), true, null);
  }

  /**
   * 用一段原始字节构造载荷，用于 {@link SUDSProtocol#RAW}。
   * 
   * @param data 原始字节，会被拷贝；为 null 时视作空字节流
   * @return 载荷
   * @since 0.6.2
   */
  public static SUDSPayload bytes(byte[] data) {
    return of(data, true);
  }

  /**
   * 用一段原始字节的切片构造载荷，用于 {@link SUDSProtocol#RAW}。
   * 
   * @param data   原始字节数组
   * @param offset 起始下标
   * @param length 长度
   * @return 载荷
   * @throws IndexOutOfBoundsException 如果 offset / length 越界
   * @since 0.6.2
   */
  public static SUDSPayload bytes(byte[] data, int offset, int length) {
    return of(Arrays.copyOfRange(data, offset, offset + length), true);
  }

  /**
   * 直接用「原始字节 + 是否自定义协议」构造载荷。
   * <p>
   * 这是最底层的一个构造器，其余构造方法都建立在它之上。它<b>不做任何校验</b>：
   * 字节是什么就送什么出去，因此适合自定义协议，也适合手工序列化好的非对象根 JSON。
   * 
   * @param data 链路上的原始字节，会被拷贝
   * @param raw  是否属于自定义协议；只有为 true 的载荷才能在 {@link SUDSProtocol#RAW} 链路上发送，
   *             反之亦然
   * @return 载荷
   * @since 0.6.2
   */
  public static SUDSPayload of(byte[] data, boolean raw) {
    return new SUDSPayload(data == null ? new byte[0] : data.clone(), raw, null);
  }

  // ----------------------------------------------------------------
  // 构造：接收侧 / 离线解析
  // ----------------------------------------------------------------

  /**
   * 把一段 JSON 文本解析成载荷。链路的接收路径用它，你也可以离线拿它解析一段报文文本。
   * <p>
   * 与 {@link #of(byte[], boolean)} 的区别是<b>这里会严格校验</b>：语法必须合法，
   * 且根节点必须是 JSON 对象（这是内置 JSON 协议的约定）。校验在构造时完成，
   * 解析结果会被缓存，后续 {@link #json()} 不再重复解析。
   * 
   * @param line 一段 JSON 对象文本的字节，允许带结尾的 LF / CRLF（会被剥掉）
   * @return 载荷
   * @throws NullPointerException     如果 line 为 null
   * @throws IllegalArgumentException 如果内容为空、不是合法 JSON，或根节点不是对象
   * @since 0.6.2
   */
  public static SUDSPayload parse(byte[] line) {
    if (line == null)
      throw new NullPointerException("待解析的字节不能为 null。");
    String text = new String(line, StandardCharsets.UTF_8).trim();
    if (text.isEmpty())
      throw new IllegalArgumentException("空内容无法解析为 JSON 载荷。");
    Map<String, Object> parsed = parseObject(text);
    // 存归一化后的字节：去掉结尾 LF，使收到的载荷与要发出的载荷是同一种字节
    return new SUDSPayload(text.getBytes(StandardCharsets.UTF_8), false, parsed);
  }

  /**
   * 接管一段字节构造自定义协议载荷，<b>不拷贝</b>。
   * 
   * @apiNote 仅供同包的链路使用：调用方必须保证之后不再改动该数组，且不会再持有它的引用。
   * @param ownedData 已经属于载荷的字节
   * @return 载荷
   */
  static SUDSPayload wrapRaw(byte[] ownedData) {
    return new SUDSPayload(ownedData == null ? new byte[0] : ownedData, true, null);
  }

  // ----------------------------------------------------------------
  // 消费：视图
  // ----------------------------------------------------------------

  /**
   * 读取 JSON 对象视图。
   * <p>
   * 首次调用时解析并缓存，之后返回<b>同一个实例</b>。返回的是普通可变 Map，
   * 直接 {@code get}/{@code put} 都行；改动它会改动这条载荷，其它监听器也会看到。
   * <p>
   * 若一条报文只需要文本或字节，可以完全跳过解析——视图是按需的。
   * 
   * @return JSON 对象的键值视图，保持 JSON 原文中的键顺序
   * @throws IllegalStateException    如果该载荷来自自定义协议（RAW），没有 JSON 语义
   * @throws IllegalArgumentException 如果内容为空、不是合法 JSON，或根节点不是对象
   * @since 0.6.2
   */
  public Map<String, Object> json() {
    requireJsonSemantics();
    Map<String, Object> cached = mapCache;
    if (cached != null)
      return cached;
    String text = new String(data, StandardCharsets.UTF_8).trim();
    if (text.isEmpty())
      throw new IllegalArgumentException("空内容无法解析为 JSON 载荷。");
    Map<String, Object> parsed = parseObject(text);
    mapCache = parsed;
    return parsed;
  }

  /**
   * 读取 {@link SConfig} 视图，用于 {@code getString("a.b.c")} 这类路径取值。
   * <p>
   * 这是消费侧才用到的便利，{@link SUDSPayload} 自身的解析与序列化都不经过 SConfig：
   * 复用同一份已解析结果，不重复解析；返回的是内存模式的 SConfig，
   * 因此 {@code save()} 会直接抛异常，不会碰到磁盘。
   * 
   * @return SConfig 视图，可读可改（改动不会回写到本载荷，也不会影响 {@link #json()}）
   * @throws IllegalStateException    如果该载荷来自自定义协议（RAW）
   * @throws IllegalArgumentException 如果内容不是 JSON 对象
   * @apiNote 传入的是顶层拷贝：SConfig 会直接引用传入的 Map，不拷贝就会与本载荷的
   *          {@link #json()} 缓存互相串改；嵌套层仍是共享引用。
   *          另注意 SConfig 的 {@code getRawData()} 不接受 null 值，
   *          含顶层空值字段的载荷请改用 {@link #json()}。
   * @since 0.6.2
   */
  public SConfig config() {
    return new SConfig(new LinkedHashMap<>(json()), SConfig.TYPES.JSON, null);
  }

  /**
   * 把原始字节按 UTF-8 解码为文本。
   * 
   * @return 文本形式；内置 JSON 协议下即那条 JSON 原文（不含结尾 LF）
   * @since 0.6.2
   */
  public String text() {
    return new String(data, StandardCharsets.UTF_8);
  }

  /**
   * 获取链路上的原始字节流。
   * 
   * @apiNote 返回的是<b>拷贝</b>，调用方可以随意改动而不影响本对象。
   * @return 原始字节，永不为 null
   * @since 0.6.2
   */
  public byte[] bytes() {
    return data.clone();
  }

  /**
   * 把载荷映射为指定类。
   * <p>
   * 视图是按需的，所以同一个载荷可以给不同消费者看成不同的类型。
   * 与 {@link #json()} 不同，此处<b>不限制</b> JSON 根节点的种类。
   * 
   * @param <R>  目标类型
   * @param type 目标类型
   * @return 映射结果
   * @throws IllegalStateException    如果该载荷来自自定义协议（RAW）
   * @throws IllegalArgumentException 如果内容不是合法 JSON，或无法映射为目标类型
   * @since 0.6.2
   */
  public <R> R as(Class<R> type) {
    return as((Type) type);
  }

  /**
   * 把载荷映射为指定类型，支持泛型。
   * 
   * @param <R>  目标类型
   * @param type 目标类型，可为带泛型的 {@link Type}
   * @return 映射结果
   * @throws IllegalStateException    如果该载荷来自自定义协议（RAW）
   * @throws IllegalArgumentException 如果内容不是合法 JSON，或无法映射为目标类型
   * @since 0.6.2
   */
  public <R> R as(Type type) {
    requireJsonSemantics();
    if (type == null)
      throw new NullPointerException("目标类型不能为 null。");
    String text = new String(data, StandardCharsets.UTF_8).trim();
    if (text.isEmpty())
      throw new IllegalArgumentException("空内容无法映射为目标类型。");
    try {
      return GSON.fromJson(text, type);
    } catch (Exception e) {
      throw new IllegalArgumentException("无法把载荷映射为 [" + type + "]：" + e.getLocalizedMessage(), e);
    }
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
    if (raw) {
      preview = length() + " bytes";
    } else {
      try {
        preview = String.valueOf(json());
      } catch (Exception e) {
        preview = "<内容无法解析为 JSON>";
      }
    }
    if (preview.length() > 512)
      preview = preview.substring(0, 512) + "...";
    return "SUDSPayload{raw=" + raw + ", bytes=" + data.length + ", " + preview + "}";
  }

  // ----------------------------------------------------------------
  // 内部工具
  // ----------------------------------------------------------------

  private void requireJsonSemantics() {
    if (raw)
      throw new IllegalStateException(
          "该载荷属于自定义协议（RAW），没有 JSON 语义；请改用 bytes() 或 text() 读取。");
  }

  /** 把一段已去除首尾空白、且非空的 JSON 文本解析为对象视图 */
  private static Map<String, Object> parseObject(String text) {
    JsonElement element;
    try {
      element = JsonParser.parseString(text);
    } catch (Exception e) {
      throw new IllegalArgumentException("JSON 内容不合法：" + e.getLocalizedMessage(), e);
    }
    if (!element.isJsonObject())
      throw new IllegalArgumentException(
          "内置 JSON 协议要求报文根节点是对象，但实际是 [" + rootKind(element)
              + "]。若确需非对象根，请用 as(Class) 映射，或以 of(bytes, false) 手工构造。");
    return new LinkedHashMap<>(GSON.fromJson(element, MAP_TYPE));
  }

  private static String rootKind(JsonElement element) {
    if (element.isJsonNull())
      return "null";
    if (element.isJsonArray())
      return "数组";
    if (element.isJsonObject())
      return "对象";
    if (element.isJsonPrimitive())
      return element.getAsJsonPrimitive().isString() ? "字符串" : "基本值";
    return "未知";
  }
}
