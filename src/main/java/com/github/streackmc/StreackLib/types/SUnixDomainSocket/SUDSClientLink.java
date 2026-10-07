package com.github.streackmc.StreackLib.types.SUnixDomainSocket;

import java.nio.channels.SocketChannel;

import com.github.streackmc.StreackLib.types.HTTPServer;

/**
 * <h2>SUDSClientLink</h2>
 * 
 * UDS 链路的<b>客户端</b>：连接到一个已经由 {@link SUDSServerLink} 绑定好的 socket 文件。
 * 对端自始至终只有一个，因此 {@link #getServer()} 就是那条连接本身。
 * 
 * <p>
 * 与其它网络客户端一样，建连失败会直接在构造阶段抛出 {@link java.io.IOException}——
 * 本类<b>不会</b>返回一个「暂时没连上」的对象，也<b>不</b>自带重连，重连策略请由调用方决定
 * （典型做法：在循环里重试 {@code new SUDSClientLink(...)}，并在 {@link SUDSAbsLink.EVENTS#ON_DISCONNECT}
 * 上安排下一次重试）。
 * 
 * <h3>示例</h3>
 * <pre>{@code
 * SUDSClientLink client = new SUDSClientLink("my-app", SUDSProtocol.JSON_LINES);
 * client.onMessage(payload -> logger.info("来自服务端: " + payload.json()));
 * client.send(SUDSPayload.json(Map.of("hello", "world")));
 * // ...
 * client.close();
 * }</pre>
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 * @see SUDSAbsLink
 * @see SUDSServerLink
 * @see HTTPServer
 */
public class SUDSClientLink extends SUDSAbsLink {

  /** 与服务端之间的通道 */
  private SocketChannel client;
  /** 指向服务端的那条连接 */
  private SUDSPeer serverPeer;

  /**
   * 连接到 token 对应的 socket 文件。
   * 
   * @param token    链路标识符，需与服务端保持一致
   * @param protocol 链路协议模式，需与服务端保持一致，见 {@link SUDSProtocol}
   * @throws UnsupportedOperationException 如果当前运行环境不支持 UDS
   * @throws NullPointerException          如果 token 或 protocol 为 null
   * @throws IllegalArgumentException      如果 token 为空白或长度超过 64 个字符
   * @throws java.io.IOException           如果连接失败，例如服务端未启动、socket 文件不存在
   * @since 0.6.2
   */
  public SUDSClientLink(String token, SUDSProtocol protocol) throws Exception {
    super(token, protocol);
    try {
      client = openUnixChannel();
      client.connect(getAddress());
    } catch (Exception e) {
      // 连接失败时不要留下半开通道与线程池
      closeQuietly(client);
      close();
      throw e;
    }
    serverPeer = registerPeer(client);
    serverPeer.startReadLoop();
  }

  // ----------------------------------------------------------------
  // 收发
  // ----------------------------------------------------------------

  /**
   * 向服务端发送一条报文。
   * <p>
   * 与父类的广播语义不同，客户端是点对点的：一旦服务端断开，本方法会直接抛
   * {@link IllegalStateException}，而不是把报文静默丢掉——静默丢数据比抛异常更难排查。
   * 
   * @param payload 报文内容
   * @throws NullPointerException     如果 payload 为 null
   * @throws IllegalStateException    如果尚未连接或连接已断开
   * @throws IllegalArgumentException 如果载荷类型与链路协议不匹配
   * @throws java.io.UncheckedIOException 如果写入过程中发生 IO 错误
   * @since 0.6.2
   */
  @Override
  public void send(SUDSPayload payload) {
    SUDSPeer peer = serverPeer;
    if (peer == null || !peer.isOpen())
      throw new IllegalStateException("Client link is not connected to any server: token=[" + getToken() + "].");
    peer.send(payload);
  }

  /**
   * @return 指向服务端的那条连接
   * @since 0.6.2
   */
  public SUDSPeer getServer() {
    return serverPeer;
  }

  /**
   * @return 与服务端之间的连接是否仍然可用
   * @since 0.6.2
   */
  public boolean isConnected() {
    SUDSPeer peer = serverPeer;
    return peer != null && peer.isOpen();
  }

  // ----------------------------------------------------------------
  // 生命周期
  // ----------------------------------------------------------------

  /**
   * @return 是否正处于可用的连接状态；服务端一侧断开后立刻变为 false
   * @since 0.6.2
   */
  @Override
  public boolean isRunning() {
    return !isClosed() && isConnected();
  }

  @Override
  protected void shutdownTransport() {
    // 客户端没有 socket 文件（连接是匿名的），关掉通道即可
    closeQuietly(client);
  }
}
