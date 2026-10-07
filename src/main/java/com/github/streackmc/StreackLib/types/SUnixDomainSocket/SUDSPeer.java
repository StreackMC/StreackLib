package com.github.streackmc.StreackLib.types.SUnixDomainSocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SUDSPeer</h2>
 * 
 * 链路中<b>一条已建立的连接</b>。服务端每 {@code accept} 到一个客户端就产生一个实例，
 * 客户端则只有一条（指向服务端）。
 * 
 * <p>
 * 本类承担了链路里所有「逐字节」的脏活，因此 {@link SUDSAbsLink} 的收发逻辑可以保持干净：
 * <ul>
 * <li><b>写</b>：{@link #send(SUDSPayload)} 内部加写锁，保证多线程并发发送时字节不会互相穿插；</li>
 * <li><b>读</b>：一条连接对应一个读线程，按 {@link SUDSProtocol} 完成分帧后交给链路分发；</li>
 * <li><b>生命周期</b>：{@link #close()} 幂等，且会自然收束读线程与链路的连接列表。</li>
 * </ul>
 * 
 * <h3>关于「谁是谁」</h3>
 * UDS 与 TCP 不同，客户端侧通常<b>没有</b>可用的对端地址（连接是匿名的），因此无法靠地址识别对端。
 * 服务端如果需要区分客户端，应由上层协议约定：例如客户端连上后先发一条 {@code {"id": "..."}} 报文，
 * 服务端把它与 {@link #getId()} 关联起来。
 * <p>
 * 另外要注意 {@link #getId()} 是<b>本地</b>标识：同一条连接在服务端与客户端各自持有一个
 * {@code SUDSPeer}，两边生成的 ID 互不相同，<b>不能</b>跨进程比较，也不能拿来当协议里的会话 ID 用。
 * 需要在两端指向「同一条连接」时，请自行在协议层发放 ID。
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 * @see SUDSAbsLink
 * @see SUDSPayload
 */
public class SUDSPeer extends StreackLibNewable implements AutoCloseable {

  /** 原始字节模式下的单次读取缓冲大小 */
  private static final int READ_BUFFER_SIZE = 8192;

  /** 分帧用的终止符；内容恒定，可跨线程共享 */
  private static final byte[] LINE_FEED = { '\n' };

  /** 所属链路 */
  private final SUDSAbsLink owner;
  /** 底层通道 */
  private final SocketChannel channel;
  /** 链路协议 */
  private final SUDSProtocol protocol;
  /** 连接标识符，在链路内唯一 */
  private final String id;
  /** 写锁：同一连接的多次写入必须串行，否则字节会互相穿插 */
  private final ReentrantLock writeLock = new ReentrantLock();
  /** 连接是否仍然可用 */
  private final AtomicBoolean open = new AtomicBoolean(true);

  /**
   * 包装一条已建立的通道。仅供 {@link SUDSAbsLink#registerPeer(SocketChannel)} 调用。
   * 
   * @param owner    所属链路
   * @param channel  已建立、阻塞模式下的通道
   * @param protocol 链路协议
   */
  SUDSPeer(SUDSAbsLink owner, SocketChannel channel, SUDSProtocol protocol) {
    this.owner = owner;
    this.channel = channel;
    this.protocol = protocol;
    this.id = "peer-" + INSTANCE_ID;
  }

  // ----------------------------------------------------------------
  // 发送
  // ----------------------------------------------------------------

  /**
   * 向本连接写入一条报文。
   * <p>
   * 底层是阻塞写：如果对端迟迟不读取、发送缓冲区被填满，本方法会阻塞到写完为止，
   * 因此不要在回调线程里对着一个已经不读数据的对端做无节制的大报文发送。
   * 
   * @param payload 报文内容
   * @throws NullPointerException     如果 payload 为 null
   * @throws IllegalStateException    如果本连接已断开
   * @throws IllegalArgumentException 如果载荷类型与链路协议不匹配（例如拿
   *                                  {@link SUDSPayload#ofJson(Object)} 的产物往
   *                                  {@link SUDSProtocol#RAW} 链路上发）
   * @throws UncheckedIOException     如果写入过程中发生 IO 错误
   * @since 0.6.2
   */
  public void send(SUDSPayload<?> payload) {
    if (payload == null)
      throw new NullPointerException("Payload cannot be null.");
    if (!open.get())
      throw new IllegalStateException("Cannot send on a closed peer: [" + id + "].");
    if (payload.isRaw() != protocol.isRaw())
      throw new IllegalArgumentException(
          "Payload kind mismatches link protocol [" + protocol.getId() + "]: expected "
              + (protocol.isRaw() ? "SUDSPayload.raw(...)" : "SUDSPayload.ofJson(...)")
              + ", but got " + (payload.isRaw() ? "a raw payload" : "a JSON payload") + ".");

    ByteBuffer buffer = ByteBuffer.wrap(payload.rawForWrite());
    writeLock.lock();
    try {
      writeFully(buffer);
      // 分帧信息由链路追加，而不是由载荷自己带：JSON 行协议的报文必须以 LF 结束。
      // 这样「收到再转发」的载荷（字节里不含 LF）也能被正确地发出去。
      if (protocol.isFramed())
        writeFully(ByteBuffer.wrap(LINE_FEED));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to send payload on peer [" + id + "].", e);
    } finally {
      writeLock.unlock();
    }
  }

  /** 把整个缓冲区写完；阻塞通道上单次 write 可能只写一部分 */
  private void writeFully(ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining())
      channel.write(buffer);
  }

  // ----------------------------------------------------------------
  // 接收
  // ----------------------------------------------------------------

  /**
   * 在所属链路的线程池上启动本连接的读循环。由链路在建连后调用。
   */
  void startReadLoop() {
    owner.workerPool().submit(this::readLoop);
  }

  private void readLoop() {
    ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
    // JSON 行协议需要把「跨多次 read 的一条报文」攒起来；原始模式不需要缓冲
    ByteArrayOutputStream accumulator = protocol.isFramed() ? new ByteArrayOutputStream() : null;
    try {
      while (open.get()) {
        buffer.clear();
        int read = channel.read(buffer);
        if (read < 0)
          break; // 对端正常关闭
        if (read == 0)
          continue; // 阻塞模式下几乎不会发生，防御性处理

        buffer.flip();
        if (protocol.isFramed())
          consumeFramed(buffer, accumulator);
        else
          consumeRaw(buffer);
      }
    } catch (Exception e) {
      // 主动关闭时读线程必然收到异常，此时不应报错
      if (open.get())
        owner.fireError(this, e);
    } finally {
      close();
    }
  }

  /**
   * 原始模式：本次 read 拿到的字节块就是一个报文，原样交出去。
   * <p>
   * 注意「一个报文」在这里只表示「一次读取的产物」，其边界由操作系统与对端的写入时机决定，
   * 不保证与对端的一次 {@code send} 对齐——这正是自定义协议必须自己分帧的原因。
   */
  private void consumeRaw(ByteBuffer buffer) {
    byte[] chunk = new byte[buffer.remaining()];
    buffer.get(chunk);
    owner.dispatchPayload(this, SUDSPayload.raw(chunk));
  }

  /**
   * 内置模式：按 LF 切分，把每个非空片段当作一条 JSON 报文。
   * <p>
   * 顺带兼容 CRLF：裸 {@code \r} 会被丢弃。这在 JSON 里是安全的，因为字符串中的换行按规范必须写成
   * {@code \n} 转义，正文中不可能出现合法的裸 CR。
   */
  private void consumeFramed(ByteBuffer buffer, ByteArrayOutputStream accumulator) throws IOException {
    while (buffer.hasRemaining()) {
      byte b = buffer.get();
      if (b == '\n') {
        byte[] line = accumulator.toByteArray();
        accumulator.reset();
        if (line.length == 0)
          continue; // 空行忽略，便于对端用连续换行做心跳
        try {
          owner.dispatchPayload(this, SUDSPayload.json(line));
        } catch (Exception e) {
          // 单条报文有问题不应拖垮整条连接，报错后继续读下一条
          owner.fireError(this, e);
        }
      } else if (b != '\r') {
        accumulator.write(b);
        if (accumulator.size() > owner.MAX_MESSAGE_SIZE) {
          accumulator.reset();
          throw new IOException("Message exceeded MAX_MESSAGE_SIZE(" + owner.MAX_MESSAGE_SIZE
              + " bytes), closing peer [" + id + "].");
        }
      }
    }
  }

  // ----------------------------------------------------------------
  // 生命周期与访问器
  // ----------------------------------------------------------------

  /**
   * 关闭本连接。幂等，且不抛异常。
   * <p>
   * 关闭后：读线程会在读阻塞处被唤醒并退出、本连接从链路的连接列表里移除、
   * 链路触发一次 {@link SUDSAbsLink.EVENTS#ON_DISCONNECT}。
   * 
   * @since 0.6.2
   */
  @Override
  public void close() {
    if (!open.compareAndSet(true, false))
      return;
    try {
      channel.close();
    } catch (IOException ignored) {
    }
    owner.unregisterPeer(this);
    owner.fireDisconnect(this);
  }

  /**
   * @return 连接是否仍然可用（已关闭、或对端已断开时为 false）
   * @since 0.6.2
   */
  public boolean isOpen() {
    return open.get();
  }

  /**
   * @return 连接标识符，在所属链路内唯一
   * @since 0.6.2
   */
  public String getId() {
    return id;
  }

  /**
   * @return 本连接使用的协议模式
   * @since 0.6.2
   */
  public SUDSProtocol getProtocol() {
    return protocol;
  }

  /**
   * @return 本连接所属的链路
   * @since 0.6.2
   */
  public SUDSAbsLink getLink() {
    return owner;
  }

  @Override
  public String toString() {
    return "SUDSPeer{id=" + id + ", token=" + owner.getToken() + ", protocol=" + protocol.getId()
        + ", open=" + open.get() + "}";
  }
}
