package com.github.streackmc.StreackLib.types.SUnixDomainSocket;

import java.util.Locale;

/**
 * <h2>SUDSProtocol</h2>
 * 
 * {@link SUDSAbsLink} 及其子类所支持的链路协议模式。该模式在创建链路时确定且<b>不可变更</b>，
 * 它唯一的职责是回答一个问题：链路上「一条报文」的边界在哪里？
 * 
 * <ul>
 * <li>{@link #JSON_LINES} —— 内置协议。报文是一段 JSON 文本，以单个 LF（{@code \n}）结束。
 * 链路自动分帧并把每一段 JSON 文本解析为 {@link SUDSPayload}，调用方拿到的永远是完整的、
 * 已经解析好的报文，不需要关心粘包与拆包。<b>没有历史包袱时请一律使用它。</b></li>
 * <li>{@link #RAW} —— 自定义协议。链路不做任何分帧，{@link SUDSPayload} 直接给出原始字节流，
 * 报文边界由调用方自己的协议负责。用于对接既有的二进制协议（自定义帧头、protobuf 直连等）。</li>
 * </ul>
 * 
 * <h3>两种模式的代价</h3>
 * 
 * {@link #JSON_LINES} 用「JSON 文本 + LF」换取零成本的分帧，代价是每一条报文都需要是合法 JSON 对象，
 * 且不能包含裸 LF（字符串内的换行必须写成 {@code \n} 转义，这在 JSON 规范里本来就是必须的）。
 * {@link #RAW} 用「自己实现分帧」换取对每一个字节的完全掌控，代价是必须自行处理粘包与拆包——
 * UDS 与 TCP 同样是<b>字节流</b>，不是消息流。
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 * @see SUDSPayload
 * @see SUDSAbsLink
 */
public enum SUDSProtocol {

  /**
   * 内置 JSON 行协议：报文为一段 JSON 文本并以 LF（{@code \n}）结束。
   * <p>
   * 接收时按 LF 分帧、忽略空行、兼容 CRLF 换行；发送时由链路在写入后自动补上 LF，
   * 因此 {@link SUDSPayload#bytes()} 里永远只有 JSON 文本本身。
   * <p>
   * 报文由 Gson 以紧凑模式序列化，本身不含裸换行，因此用 LF 做分隔符是安全的。
   */
  JSON_LINES(true),

  /**
   * 自定义协议：链路只提供字节流，不做任何分帧与解析。
   * <p>
   * 接收时每次 {@code read} 得到的字节块就是一个 {@link SUDSPayload}；发送时
   * {@link SUDSPayload#bytes(byte[])} 的字节被原样写入。<b>调用方必须自行实现分帧</b>，
   * 否则无法区分报文边界。
   */
  RAW(false);

  /** 本协议是否由链路自动完成报文分帧 */
  private final boolean framed;

  SUDSProtocol(boolean framed) {
    this.framed = framed;
  }

  /**
   * @return 该协议是否由链路自动完成报文分帧
   * @since 0.6.2
   */
  public boolean isFramed() {
    return framed;
  }

  /**
   * @return 是否为不做分帧的自定义协议，等价于 {@code !isFramed()}
   * @since 0.6.2
   */
  public boolean isRaw() {
    return !framed;
  }

  /**
   * @return 该协议的小写标识符，可被 {@link #fromId(String)} 还原
   * @since 0.6.2
   */
  public String getId() {
    return name().toLowerCase(Locale.ROOT);
  }

  /**
   * 按标识符还原协议，大小写不敏感。
   * <p>
   * 便于把协议模式写进配置文件后再读回来。
   * 
   * @param id 标识符，例如 {@code "json_lines"}、{@code "raw"}
   * @return 匹配的协议
   * @throws NullPointerException     如果 id 为 null
   * @throws IllegalArgumentException 如果没有匹配的协议
   * @since 0.6.2
   */
  public static SUDSProtocol fromId(String id) {
    if (id == null)
      throw new NullPointerException("Protocol id cannot be null.");
    String normalized = id.trim().toLowerCase(Locale.ROOT);
    for (SUDSProtocol p : values()) {
      if (p.getId().equals(normalized))
        return p;
    }
    throw new IllegalArgumentException("Unknown SUDS protocol id: [" + id + "]. Expected: [json_lines, raw].");
  }
}
