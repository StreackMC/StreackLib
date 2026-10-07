package com.github.streackmc.StreackLib.types.SUnixDomainSocket;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.types.HTTPServer;
import com.github.streackmc.StreackLib.types.StreackLibNewable;
import com.github.streackmc.StreackLib.utils.SEventCentral;

/**
 * <h2>SUDSAbsLink</h2>
 * 
 * 基于 UDS（Unix Domain Socket）的进程间通讯链路的<b>抽象公共部分</b>。
 * 服务端 {@link SUDSServerLink} 与客户端 {@link SUDSClientLink} 都继承本类，
 * 因此二者的收发、监听、关闭行为完全一致，区别只在于「谁来建连」：
 * 
 * <table border="1">
 * <caption>两种形态的差异</caption>
 * <tr><th></th><th>{@link SUDSServerLink}</th><th>{@link SUDSClientLink}</th></tr>
 * <tr><td>建连方式</td><td>{@code bind} 到 socket 文件并持续 {@code accept}</td><td>{@code connect} 到已有 socket 文件</td></tr>
 * <tr><td>对端数量</td><td>0..N，每个接入的连接是一个 {@link SUDSPeer}</td><td>固定 1 个（即服务端）</td></tr>
 * <tr><td>{@link #send(SUDSPayload)} 语义</td><td>向所有已接入的客户端<b>广播</b></td><td>发往服务端；未连接时抛异常</td></tr>
 * </table>
 * 
 * <h3>运行环境要求</h3>
 * <ul>
 * <li>支持 POSIX 与 AF_UNIX Socket 的文件系统（Windows 上需要 Windows 10 1803 / Windows Server 2019 及以上）</li>
 * <li>JDK 16+</li>
 * </ul>
 * 你可以通过 {@link #isSupported()} 在创建前检查当前环境是否可用，它不会抛异常、只返回布尔值。
 * 
 * <h3>效率与开销</h3>
 * UDS 的效率通常比 TCP/IP 更高，因为它避免了网络协议栈的开销；但它只适用于<b>同一台机器</b>上的
 * 进程间通信，跨机器场景请改用 {@link HTTPServer}。
 * 
 * <h3>快速上手</h3>
 * <pre>{@code
 * // 服务端
 * SUDSServerLink server = new SUDSServerLink("my-app", SUDSProtocol.JSON_LINES);
 * server.onMessage((peer, payload) -> {
 *   peer.send(SUDSPayload.ofJson(Map.of("echo", payload.asMap())));
 * });
 *
 * // 客户端
 * SUDSClientLink client = new SUDSClientLink("my-app", SUDSProtocol.JSON_LINES);
 * client.onMessage(payload -> logger.info("收到: " + payload.asMap()));
 * client.send(SUDSPayload.ofJson(Map.of("hello", "world")));
 * }</pre>
 * 
 * <h3>线程模型</h3>
 * <ul>
 * <li>每条连接有且只有一个读线程，读到的报文按顺序回调，因此<b>同一连接的报文不会并发回调</b>；</li>
 * <li>不同连接之间是并发的，回调里请注意共享状态的可见性；</li>
 * <li>回调运行在链路的读线程上，<b>不要</b>在回调里做长时间阻塞操作，否则会阻塞该连接后续报文的读取；</li>
 * <li>{@link #send(SUDSPayload)} 对同一连接是串行的（内部加写锁），可安全地被多线程调用。</li>
 * </ul>
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 * @see SUDSProtocol
 * @see SUDSPayload
 */
public abstract class SUDSAbsLink extends StreackLibNewable implements AutoCloseable {

  /**
   * 本类可以触发的事件名定义集。
   * 
   * @since 0.6.2
   */
  public final static class EVENTS {
    /** 收到一条报文。<br>数据：{@code peer}({@link SUDSPeer})、{@code payload}({@link SUDSPayload}) */
    public static final String ON_MESSAGE = "streacklib.suds:on_message";
    /** 建立了一条连接。<br>数据：{@code peer}({@link SUDSPeer}) */
    public static final String ON_CONNECT = "streacklib.suds:on_connect";
    /** 断开了一条连接。<br>数据：{@code peer}({@link SUDSPeer}) */
    public static final String ON_DISCONNECT = "streacklib.suds:on_disconnect";
    /** 链路上发生了错误。<br>数据：{@code peer}({@link SUDSPeer}，可能为 null)、{@code exception}({@link Exception}) */
    public static final String ON_ERROR = "streacklib.suds:on_error";
  }

  /**
   * 报文监听器，等价于 JavaScript 里直接给 {@code socket.onmessage} 赋值。
   * <p>
   * 与 {@link Consumer} 版本的区别是可以拿到 {@code peer}，从而知道「是谁发来的」并向其单独回复。
   * 
   * @since 0.6.2
   */
  @FunctionalInterface
  public interface MessageListener {
    /**
     * 收到一条完整报文时调用
     * 
     * @param peer    报文来源的那条连接；服务端可借此定位客户端，客户端则该值即对服务端的那条连接
     * @param payload 报文内容
     */
    void onMessage(SUDSPeer peer, SUDSPayload<?> payload);
  }

  /**
   * 单条报文的字节上限，超出即认为对端协议异常并断开该连接。
   * <p>
   * 该限制只对 {@link SUDSProtocol#JSON_LINES} 生效（它需要缓冲到换行符才能确认一条报文结束）；
   * {@link SUDSProtocol#RAW} 不做分帧，天然不受此限制。默认 1 MiB。
   */
  public long MAX_MESSAGE_SIZE = 1L * 1024 * 1024;

  /** 链路标识符，会参与最终 socket 文件名的生成 */
  private final String token;
  /** 链路协议模式 */
  private final SUDSProtocol protocol;
  /** socket 文件路径 */
  private final Path socketPath;
  /** socket 地址 */
  private final UnixDomainSocketAddress address;
  /** 链路是否已关闭 */
  private final AtomicBoolean closed = new AtomicBoolean(false);
  /** 当前活跃的连接，key 为 {@link SUDSPeer#getId()} */
  private final Map<String, SUDSPeer> peers = new ConcurrentHashMap<>();
  /** 报文监听器 */
  private final Map<Integer, MessageListener> listeners = new ConcurrentHashMap<>();
  private final AtomicInteger listenerIdSeq = new AtomicInteger(0);
  /** 处理各连接读循环的线程池 */
  private final ExecutorService workers;

  /**
   * 构造一条链路的公共部分。本构造器<b>不会</b>建连或监听，具体行为由子类完成。
   * 
   * @param token    链路标识符，会参与最终 socket 文件名的生成
   * @param protocol 链路协议模式，见 {@link SUDSProtocol}
   * @throws UnsupportedOperationException 如果当前运行环境不支持 UDS
   * @throws NullPointerException          如果 token 或 protocol 为 null
   * @throws IllegalArgumentException      如果 token 为空白、长度超过 64 个字符，或含有路径分隔符
   * @since 0.6.2
   */
  protected SUDSAbsLink(String token, SUDSProtocol protocol) {
    if (!isSupported())
      throw new UnsupportedOperationException("Runtime environment does not support those features: [UDS].");
    if (token == null || token.isBlank())
      throw new NullPointerException("Token cannot be null or blank.");
    if (token.length() > 64)
      throw new IllegalArgumentException("Token cannot be longer than 64 characters, but got " + token.length() + ".");
    if (protocol == null)
      throw new NullPointerException("Protocol cannot be null, see SUDSProtocol.");

    this.token = token;
    this.protocol = protocol;
    this.socketPath = resolveSocketPath(token);
    this.address = UnixDomainSocketAddress.of(socketPath);
    this.workers = Executors.newCachedThreadPool(daemonThreadFactory("SUDS-worker-" + token));
  }

  // ----------------------------------------------------------------
  // 生命周期
  // ----------------------------------------------------------------

  /**
   * 关闭链路：先停止自身对外的监听/连接通道，再断开所有连接，最后回收线程池。
   * <p>
   * 本方法是<b>幂等</b>的，重复调用不会产生副作用；它保证不抛异常（内部异常只记日志），
   * 因此可以安全地放在 {@code finally} 或 try-with-resources 里。
   * 
   * @since 0.6.2
   */
  @Override
  public final void close() {
    if (!closed.compareAndSet(false, true))
      return;

    // 1. 关闭子类自己的通道（服务端还要顺手删掉 socket 文件），唤醒阻塞中的 accept()
    try {
      shutdownTransport();
    } catch (Exception e) {
      logger.error("SUDS 链路关闭时发生异常, token=[%s]:", token, e);
    }

    // 2. 断开所有连接，唤醒阻塞中的 read()
    for (SUDSPeer peer : peers.values()) {
      try {
        peer.close();
      } catch (Exception ignored) {
      }
    }
    peers.clear();

    // 3. 回收线程池
    workers.shutdownNow();
    try {
      workers.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * @return 链路是否已关闭
   * @since 0.6.2
   */
  public boolean isClosed() {
    return closed.get();
  }

  /**
   * @return 链路自身是否处于可用状态（服务端在监听 / 客户端连着服务端）
   * @since 0.6.2
   */
  public abstract boolean isRunning();

  /**
   * 关闭自身持有的通道与线程池，由 {@link #close()} 调用。
   * <p>
   * 实现方只需关心「自己独有的」资源，连接的关闭与线程池的回收由父类统一处理。
   * 
   * @see SUDSServerLink#shutdownTransport()
   * @see SUDSClientLink#shutdownTransport()
   */
  protected abstract void shutdownTransport();

  // ----------------------------------------------------------------
  // 收发
  // ----------------------------------------------------------------

  /**
   * 向当前所有已建立的连接投递一条报文。
   * <p>
   * 在服务端上这是<b>广播</b>；在客户端上只有一个对端，等价于点对点发送（不过客户端已重写本方法，
   * 在未连接时会抛异常而不是静默丢弃）。服务端在没有任何客户端接入时静默返回，
   * 需要区分时可先查 {@link #getPeerCount()}。
   * 
   * @param payload 报文内容
   * @throws NullPointerException  如果 payload 为 null
   * @throws IllegalStateException 如果链路已关闭
   * @throws IllegalArgumentException 如果载荷类型与链路协议不匹配
   * @since 0.6.2
   */
  public void send(SUDSPayload<?> payload) {
    requireOpen();
    for (SUDSPeer peer : peers.values()) {
      peer.send(payload);
    }
  }

  /**
   * 向指定的那一条连接投递一条报文。
   * 
   * @param peer    目标连接，必须属于本链路
   * @param payload 报文内容
   * @throws NullPointerException     如果 peer 或 payload 为 null
   * @throws IllegalArgumentException 如果 peer 不属于本链路，或载荷类型与链路协议不匹配
   * @throws IllegalStateException    如果链路已关闭，或该连接已断开
   * @since 0.6.2
   */
  public void send(SUDSPeer peer, SUDSPayload<?> payload) {
    requireOpen();
    if (peer == null)
      throw new NullPointerException("Peer cannot be null.");
    if (!peers.containsKey(peer.getId()))
      throw new IllegalArgumentException("Peer [" + peer.getId() + "] does not belong to this link.");
    peer.send(payload);
  }

  // ----------------------------------------------------------------
  // 报文监听
  // ----------------------------------------------------------------

  /**
   * 注册一个报文监听器，被调用时可以拿到报文来源的连接。
   * 
   * @param listener 监听器
   * @return 监听器 ID，用于 {@link #removeMessageListener(int)} 注销
   * @throws NullPointerException 如果 listener 为 null
   * @since 0.6.2
   */
  public int onMessage(MessageListener listener) {
    if (listener == null)
      throw new NullPointerException("Listener cannot be null.");
    int id = listenerIdSeq.incrementAndGet();
    listeners.put(id, listener);
    return id;
  }

  /**
   * 注册一个报文监听器，不关心来源连接时写起来最短：
   * 
   * <pre>{@code link.onMessage(payload -> System.out.println(payload.asMap()));}</pre>
   * 
   * @param listener 监听器
   * @return 监听器 ID，用于 {@link #removeMessageListener(int)} 注销
   * @throws NullPointerException 如果 listener 为 null
   * @since 0.6.2
   */
  public int onMessage(Consumer<SUDSPayload<?>> listener) {
    if (listener == null)
      throw new NullPointerException("Listener cannot be null.");
    return onMessage((peer, payload) -> listener.accept(payload));
  }

  /**
   * 注销一个报文监听器
   * 
   * @param listenerId {@link #onMessage(MessageListener)} 返回的 ID
   * @since 0.6.2
   */
  public void removeMessageListener(int listenerId) {
    listeners.remove(listenerId);
  }

  /**
   * @return 当前已注册的报文监听器数量
   * @since 0.6.2
   */
  public int getMessageListenerCount() {
    return listeners.size();
  }

  // ----------------------------------------------------------------
  // 访问器
  // ----------------------------------------------------------------

  /**
   * @return 链路标识符
   * @since 0.6.2
   */
  public String getToken() {
    return token;
  }

  /**
   * @return 本链路使用的协议模式
   * @since 0.6.2
   */
  public SUDSProtocol getProtocol() {
    return protocol;
  }

  /**
   * @apiNote 返回的是拷贝，并非原始引用
   * @return 当前链路使用的 UDS 地址
   * @since 0.6.2
   */
  public UnixDomainSocketAddress getAddress() {
    return UnixDomainSocketAddress.of(address.getPath());
  }

  /**
   * @return 当前链路使用的 socket 文件路径
   * @since 0.6.2
   */
  public Path getSocketPath() {
    return socketPath;
  }

  /**
   * 获取当前所有活跃连接的快照。
   * 
   * @apiNote 返回的是不可变快照，遍历时新接入的连接不会出现在其中
   * @return 连接集合
   * @since 0.6.2
   */
  public Set<SUDSPeer> getPeers() {
    return Collections.unmodifiableSet(new LinkedHashSet<>(peers.values()));
  }

  /**
   * 按 ID 获取一条连接
   * 
   * @param peerId {@link SUDSPeer#getId()}
   * @return 对应的连接，不存在时为 null
   * @since 0.6.2
   */
  public SUDSPeer getPeer(String peerId) {
    return peerId == null ? null : peers.get(peerId);
  }

  /**
   * @return 当前活跃连接数
   * @since 0.6.2
   */
  public int getPeerCount() {
    return peers.size();
  }

  // ----------------------------------------------------------------
  // 子类接口
  // ----------------------------------------------------------------

  /**
   * 校验链路仍处于打开状态
   * 
   * @throws IllegalStateException 如果链路已关闭
   */
  protected final void requireOpen() {
    if (closed.get())
      throw new IllegalStateException("Link has been closed: token=[" + token + "].");
  }

  /**
   * @return 处理各连接读循环的线程池，子类提交读任务时使用
   */
  protected final ExecutorService workerPool() {
    return workers;
  }

  /**
   * 把一条由子类建立好的通道登记为活跃连接，并触发 {@link EVENTS#ON_CONNECT} 事件。
   * <p>
   * 登记之后由调用方负责提交读循环。
   * 
   * @param channel 已建立、阻塞模式下的通道
   * @return 新建的连接对象
   * @throws NullPointerException 如果 channel 为 null
   */
  protected final SUDSPeer registerPeer(SocketChannel channel) {
    if (channel == null)
      throw new NullPointerException("Channel cannot be null.");
    SUDSPeer peer = new SUDSPeer(this, channel, protocol);
    peers.put(peer.getId(), peer);
    SEventCentral.broadcastEvent(EVENTS.ON_CONNECT, this)
        .set("peer", peer)
        .broadcast();
    return peer;
  }

  /**
   * 把一条连接从活跃列表里摘掉
   * 
   * @param peer 待摘除的连接
   */
  final void unregisterPeer(SUDSPeer peer) {
    if (peer != null)
      peers.remove(peer.getId());
  }

  /**
   * 把一个报文分发给所有监听器，并触发 {@link EVENTS#ON_MESSAGE} 事件。
   * <p>
   * 由读线程调用。单个监听器抛出的异常不会影响其它监听器，只会被记为一条链路错误。
   * 
   * @param peer    报文来源
   * @param payload 报文内容
   */
  final void dispatchPayload(SUDSPeer peer, SUDSPayload<?> payload) {
    for (MessageListener listener : listeners.values()) {
      try {
        listener.onMessage(peer, payload);
      } catch (Throwable t) {
        fireError(peer, t);
      }
    }
    try {
      SEventCentral.broadcastEvent(EVENTS.ON_MESSAGE, this)
          .set("peer", peer)
          .set("payload", payload)
          .broadcast();
    } catch (Throwable t) {
      logger.error("SUDS 事件广播失败, token=[%s]:", token, t);
    }
  }

  /**
   * 记录并广播一条链路错误
   * 
   * @param peer  出错的连接，可能与链路本身有关而为 null
   * @param error 异常
   */
  final void fireError(SUDSPeer peer, Throwable error) {
    logger.error("SUDS 链路发生错误, token=[%s], peer=[%s]:", token, peer == null ? "<link>" : peer.getId(), error);
    try {
      SEventCentral.broadcastEvent(EVENTS.ON_ERROR, this)
          .set("peer", peer)
          .set("exception", error)
          .broadcast();
    } catch (Throwable ignored) {
    }
  }

  /**
   * 触发一条连接断开的事件
   * 
   * @param peer 已断开的连接
   */
  final void fireDisconnect(SUDSPeer peer) {
    try {
      SEventCentral.broadcastEvent(EVENTS.ON_DISCONNECT, this)
          .set("peer", peer)
          .broadcast();
    } catch (Throwable t) {
      logger.error("SUDS 断开事件广播失败, token=[%s]:", token, t);
    }
  }

  // ----------------------------------------------------------------
  // 静态工具方法
  // ----------------------------------------------------------------

  /**
   * 创建一个守护线程工厂，线程名带固定前缀与自增序号，便于在堆栈里辨认。
   * 
   * @param prefix 线程名前缀
   * @return 线程工厂
   */
  protected static ThreadFactory daemonThreadFactory(String prefix) {
    AtomicInteger seq = new AtomicInteger(0);
    return r -> {
      Thread t = new Thread(r, prefix + "-" + seq.incrementAndGet());
      t.setDaemon(true);
      return t;
    };
  }

  /**
   * 解析出某个 token 对应的 socket 文件路径。
   * <p>
   * POSIX 且 {@code /tmp} 可写时优先使用 {@code /tmp}（macOS 默认临时目录太长，
   * 容易撞上 UDS 路径长度上限），否则回退系统临时目录。
   * 
   * @param token 链路标识符
   * @return socket 文件路径
   * @throws NullPointerException     如果 token 为 null 或空白
   * @throws IllegalArgumentException 如果 token 含有路径分隔符（token 会直接成为文件名，
   *                                  出现分隔符就意味着能写到临时目录之外，而且客户端不会替它建目录）
   * @since 0.6.2
   */
  public static Path resolveSocketPath(String token) {
    if (token == null || token.isBlank())
      throw new NullPointerException("Token cannot be null or blank.");
    if (token.indexOf('/') >= 0 || token.indexOf('\\') >= 0 || token.indexOf('\0') >= 0)
      throw new IllegalArgumentException("Token cannot contain path separators, but got: [" + token + "].");
    String fileName = token + ".streacklib.sock";
    Path posixTmp = Path.of("/tmp");
    if (isPosixSupported() && Files.isDirectory(posixTmp) && Files.isWritable(posixTmp))
      return posixTmp.resolve(fileName);
    return Path.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve(fileName);
  }

  /**
   * 打开一个 UNIX 协议族的客户端通道。服务端与客户端子类共用，避免各处重复写
   * {@code StandardProtocolFamily.UNIX}。
   * 
   * @return 处于阻塞模式的新通道
   * @throws IOException 如果创建失败
   */
  static SocketChannel openUnixChannel() throws IOException {
    SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
    channel.configureBlocking(true);
    return channel;
  }

  /**
   * 关闭一个资源并且吞掉异常，用于清理路径上「失败也无所谓」的收尾动作。
   * 
   * @param closeable 目标资源，可为 null
   */
  protected static void closeQuietly(AutoCloseable closeable) {
    try {
      if (closeable != null)
        closeable.close();
    } catch (Exception ignored) {
    }
  }

  /** 对 UDS 探测结果的缓存 */
  private static volatile Boolean supportedCache = null;

  /**
   * 获取当前运行环境是否良好兼容 UDS。
   * <p>
   * 该方法只做检测、不抛异常，可在创建链路之前任意调用。
   * 
   * @implNote 该方法会缓存结果，首次调用时进行实际检测，后续调用直接返回缓存。
   * @return 是否支持
   * @since 0.6.2
   */
  public static boolean isSupported() {
    Boolean cached = supportedCache;
    if (cached != null)
      return cached;

    // JDK 版本不足直接返回 false
    if (Runtime.version().feature() < 16) {
      supportedCache = Boolean.FALSE;
      return false;
    }

    // 尝试打开一个 UNIX 协议的 channel，失败则说明不支持
    try (SocketChannel ignored = SocketChannel.open(StandardProtocolFamily.UNIX)) {
      supportedCache = Boolean.TRUE;
    } catch (Exception e) {
      supportedCache = Boolean.FALSE;
    }
    return supportedCache;
  }

  /**
   * 获取当前运行环境是否使用 POSIX 路径
   * 
   * @return 是否支持 POSIX 文件属性视图
   * @since 0.6.2
   */
  public static boolean isPosixSupported() {
    return FileSystems.getDefault()
        .supportedFileAttributeViews()
        .contains("posix");
  }
}
