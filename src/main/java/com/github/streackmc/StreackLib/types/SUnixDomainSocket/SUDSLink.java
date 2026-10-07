package com.github.streackmc.StreackLib.types.SUnixDomainSocket;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.streackmc.StreackLib.types.HTTPServer;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SUDSConnect</h2>
 * 
 * 本类封装了一种基于 UDS 的进程间通讯协议，并确保在不同平台上具有一致的行为。使用本类需要满足：
 * <ul>
 * <li>支持 POSIX 与 AF_UNIX Socket 的文件系统</li>
 * <li>对于 Windows: Windows 10 1803 / Windows Server 2019 及更高版本</li>
 * <li>JDK 16+</li>
 * </ul>
 * 你可以通过 {@link #isSupported()} 方法来检查当前运行环境是否支持 UDS。
 * 
 * <h3>效率与开销</h3>
 * 
 * UDS 的效率通常比 TCP/IP 更高，因为它避免了网络协议栈的开销。然而，UDS
 * 仅适用于同一台机器上的进程间通信，因此在分布式系统中可能不适用。此外，UDS 的实现可能会受到操作系统的限制，因此在使用时需要注意兼容性问题。
 * 
 * 如果你有上述需求，那么你应该使用基于 TCP/IP 的 {@link HTTPServer}。
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 */
public class SUDSLink extends StreackLibNewable implements AutoCloseable {

  /** UDS 地址 */
  private final UnixDomainSocketAddress address;
  /** UDS 通道 */
  private final ServerSocketChannel server;
  /** 连接是否已关闭 */
  private final AtomicBoolean closed = new AtomicBoolean(false);
  /** 活跃的连接 */
  private final Set<SocketChannel> connections = ConcurrentHashMap.newKeySet();
  private final ExecutorService acceptor; // 单线程，跑 accept 循环
  private final ExecutorService workers; // 多线程，处理每条连接

  // ----------------------------------------------------------------
  // 连接管理
  // ----------------------------------------------------------------

  /**
   * 新建一个连接，或者握手一个已有连接
   * 
   * @param token    连接标识符，会参与最终连接文件的生成
   * @param asClient 是否作为客户端去握手
   * @throws UnsupportedOperationException 如果当前运行环境不支持 UDS，则抛出此异常
   * @throws NullPointerException          如果 token 为 null，则抛出此异常
   * @throws IllegalArgumentException      如果 token 长度太长，则抛出此异常
   * @throws IOException                   如果在创建连接时发生了 IO
   *                                       错误，则抛出此异常：无法创建文件、文件系统不支持、构造路径过长……
   * @since 0.6.2
   */
  public SUDSLink(String token, boolean asClient) throws Exception {
    // 校验环境与参数
    if (!isSupported())
      throw new UnsupportedOperationException("Runtime environment does not support those features: [UDS].");
    if (token == null || token.isBlank())
      throw new NullPointerException("Token cannot be null or blank.");
    if (token.length() > 64)
      throw new IllegalArgumentException("Token cannot be longer than 64 characters.");

    // 构造 UDS 路径
    Path socketPath;
    if (isPosixSupported() && Files.isDirectory(Path.of("/tmp")) && Files.isWritable(Path.of("/tmp"))) {
      // POSX 标准且 /tmp 是可写目录
      // 因为 macOS 默认临时目录太长了
      socketPath = Path.of("/tmp", token + ".streacklib.sock");
    } else {
      // 否则回退系统临时目录
      socketPath = Path.of(System.getProperty("java.io.tmpdir", token + ".streacklib.sock"));
    }
    address = UnixDomainSocketAddress.of(socketPath);

    if (asClient) {
      // 客户端模式，尝试连接
      server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      server.connect(address);
    } else {
      // 绑定地址
      server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      server.bind(address);

      // 启动 accept 循环
      acceptor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SUDSLink-acceptor-" + token);
        t.setDaemon(true);
        return t;
      });
      workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SUDSLink-worker-" + token);
        t.setDaemon(true);
        return t;
      });

      acceptor.submit(this::acceptLoop);
    }
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true))
      return;

    // 1. 关闭 server，停止新连接，唤醒阻塞的 accept()
    closeQuietly(server);

    // 2. 关闭所有活跃连接，唤醒阻塞的读写
    for (SocketChannel c : connections) {
      closeQuietly(c);
    }
    connections.clear();

    // 3. 停止线程池
    acceptor.shutdownNow();
    workers.shutdownNow();
    try {
      acceptor.awaitTermination(5, TimeUnit.SECONDS);
      workers.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  // ----------------------------------------------------------------
  // 实例下工具方法
  // ----------------------------------------------------------------

  /**
   * 获取当前连接使用的 UDS 地址
   * 
   * @apiNote 返回的是拷贝，并非原始引用
   * @since 0.6.2
   */
  public UnixDomainSocketAddress getAddress() {
    return UnixDomainSocketAddress.of(address.getPath());
  }

  // ----------------------------------------------------------------
  // 静态工具方法
  // ----------------------------------------------------------------

  private static void closeQuietly(AutoCloseable c) {
    try {
      if (c != null)
        c.close();
    } catch (Exception ignored) {
    }
  }

  /** 对 UDS 探测的缓存 */
  static volatile private AtomicBoolean _isSupported = null;

  /**
   * 获取当前运行环境是否良好兼容 UDS
   * 
   * @implNote 该方法会缓存结果，首次调用时会进行实际的检测，后续调用将直接返回缓存的结果。
   * @since 0.6.2
   */
  static public boolean isSupported() {
    // 读取缓存
    if (_isSupported != null)
      return _isSupported.get();

    // JDK版本不足直接返回 false
    if (!(Runtime.version().feature() >= 16)) {
      _isSupported = new AtomicBoolean(false);
      return _isSupported.get();
    }

    // 尝试打开一个 UNIX 协议的 channel，失败则说明不支持
    try {
      try (ServerSocketChannel ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
        _isSupported = new AtomicBoolean(true);
      }
      return _isSupported.get();
    } catch (Exception e) {
      _isSupported = new AtomicBoolean(false);
      return _isSupported.get();
    }
  }

  /**
   * 获取当前运行环境是否使用 POSIX 路径
   * 
   * @since 0.6.2
   */
  static public boolean isPosixSupported() {
    return FileSystems.getDefault()
        .supportedFileAttributeViews()
        .contains("posix");
  }
}