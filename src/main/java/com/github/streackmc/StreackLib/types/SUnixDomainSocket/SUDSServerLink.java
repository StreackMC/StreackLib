package com.github.streackmc.StreackLib.types.SUnixDomainSocket;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.github.streackmc.StreackLib.self.logger;

/**
 * <h2>SUDSServerLink</h2>
 * 
 * UDS 链路的<b>服务端</b>：把一个 token 绑定成 socket 文件，然后持续接受客户端接入。
 * 每个接入的连接都会变成一个 {@link SUDSPeer}，可用 {@link #getPeers()} 遍历，
 * 每条连接收到的报文都会回调到 {@link #onMessage(MessageListener)} 注册的监听器上。
 * 
 * <p>
 * 与客户端的全部差异只有「谁发起连接」，其余收发、监听、关闭行为继承自 {@link SUDSAbsLink}。
 * 
 * <h3>示例</h3>
 * <pre>{@code
 * SUDSServerLink server = new SUDSServerLink("my-app", SUDSProtocol.JSON_LINES);
 * server.onMessage((peer, payload) -> {
 *   // 只回给发来这条报文的那个客户端
 *   peer.send(SUDSPayload.json(Map.of("echo", payload.json())));
 * });
 * // ...
 * server.close();
 * }</pre>
 * 
 * <h3>关于 socket 文件的清理</h3>
 * 服务端在 {@code bind} 前会检查目标文件：如果它存在且<b>连接不上</b>，说明是上次进程崩溃留下的僵尸文件，
 * 会被自动删除；如果<b>连得上</b>，说明另一个服务端正在使用它，此时直接抛
 * {@link IllegalStateException}，而不是把对方的 socket 顶掉。{@link #close()} 时会删除自己创建的
 * socket 文件（仅当本次确实 {@code bind} 成功过）。
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 * @see SUDSAbsLink
 * @see SUDSClientLink
 */
public class SUDSServerLink extends SUDSAbsLink {

  /** 监听通道 */
  private ServerSocketChannel server;
  /** 接受连接的线程池，单线程串行 accept */
  private ExecutorService acceptor;
  /** 本次是否成功绑定过 socket 文件，决定关闭时是否可以删除它 */
  private volatile boolean bound = false;

  /**
   * 绑定到 token 对应的 socket 文件并开始接受客户端接入。
   * 
   * @param token    链路标识符，会参与最终 socket 文件名的生成
   * @param protocol 链路协议模式，见 {@link SUDSProtocol}
   * @throws UnsupportedOperationException 如果当前运行环境不支持 UDS
   * @throws NullPointerException          如果 token 或 protocol 为 null
   * @throws IllegalArgumentException      如果 token 为空白或长度超过 64 个字符
   * @throws IllegalStateException         如果 socket 文件已被另一个正在运行的服务端占用
   * @throws IOException                   如果绑定失败，例如路径过长、目录不可写、文件系统不支持
   * @since 0.6.2
   */
  public SUDSServerLink(String token, SUDSProtocol protocol) throws Exception {
    super(token, protocol);
    try {
      prepareSocketFile(getSocketPath(), getAddress());
      server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      server.bind(getAddress());
      server.configureBlocking(true);
      bound = true;
      acceptor = Executors.newSingleThreadExecutor(daemonThreadFactory("SUDS-acceptor-" + token));
    } catch (Exception e) {
      // 构造失败，立刻回收已占用的资源，避免 socket 文件与线程池泄漏
      close();
      throw e;
    }
    acceptor.submit(this::acceptLoop);
  }

  // ----------------------------------------------------------------
  // 连接管理
  // ----------------------------------------------------------------

  /**
   * 断开指定客户端。
   * 
   * @param peer 目标连接；为 null 或不属于本链路时静默忽略
   * @since 0.6.2
   */
  public void disconnect(SUDSPeer peer) {
    if (peer == null)
      return;
    SUDSPeer known = getPeer(peer.getId());
    if (known != null)
      known.close();
  }

  /**
   * 按 ID 断开指定客户端。
   * 
   * @param peerId {@link SUDSPeer#getId()}
   * @since 0.6.2
   */
  public void disconnect(String peerId) {
    disconnect(getPeer(peerId));
  }

  /**
   * 断开当前所有客户端，服务端本身继续监听。
   * 
   * @since 0.6.2
   */
  public void disconnectAll() {
    for (SUDSPeer peer : getPeers()) {
      peer.close();
    }
  }

  // ----------------------------------------------------------------
  // 生命周期
  // ----------------------------------------------------------------

  @Override
  public boolean isRunning() {
    return !isClosed() && bound && server != null && server.isOpen();
  }

  @Override
  protected void shutdownTransport() {
    // 1. 关闭监听通道，唤醒阻塞中的 accept()
    closeQuietly(server);

    // 2. 回收 accept 线程池
    if (acceptor != null) {
      acceptor.shutdownNow();
      try {
        acceptor.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }

    // 3. 删除自己创建的 socket 文件，否则文件会一直留在 /tmp 里
    if (bound) {
      try {
        Files.deleteIfExists(getSocketPath());
      } catch (IOException e) {
        logger.warn("无法删除 SUDS socket 文件 [%s]，可手动清理：", getSocketPath(), e);
      }
    }
  }

  // ----------------------------------------------------------------
  // 内部实现
  // ----------------------------------------------------------------

  private void acceptLoop() {
    while (!isClosed()) {
      try {
        SocketChannel channel = server.accept();
        if (channel == null)
          continue;
        channel.configureBlocking(true);
        SUDSPeer peer = registerPeer(channel);
        peer.startReadLoop();
      } catch (ClosedChannelException e) {
        // 链路已关闭，accept 被唤醒，正常收工
        break;
      } catch (Exception e) {
        if (isClosed())
          break;
        fireError(null, e);
        // 持续性错误（例如 fd 耗尽）下不要空转烧 CPU
        try {
          Thread.sleep(50);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }
  }

  /**
   * 确保 socket 文件可以安全绑定。
   * <p>
   * 文件不存在时直接返回；存在时用一次探测连接区分「僵尸文件」与「活着的服务端」，
   * 只有前者才会被删除。
   */
  private static void prepareSocketFile(Path path, UnixDomainSocketAddress address) throws IOException {
    Path parent = path.getParent();
    if (parent != null)
      Files.createDirectories(parent);
    if (!Files.exists(path))
      return;

    try (SocketChannel probe = SocketChannel.open(StandardProtocolFamily.UNIX)) {
      probe.connect(address);
    } catch (IOException notAlive) {
      // 连不上，说明是上次异常退出留下的僵尸文件
      if (!Files.deleteIfExists(path))
        throw new IOException("Stale socket file cannot be removed: [" + path + "].");
      return;
    }
    throw new IllegalStateException("Another SUDS server is already listening on [" + path + "].");
  }
}
