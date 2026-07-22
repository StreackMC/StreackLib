package com.github.streackmc.StreackLib.errors.raw;

import org.apache.logging.log4j.util.InternalApi;

import com.github.streackmc.StreackLib.StreackLib;

/**
 * 当前中间层可创建异常的基类，包含唯一实例 ID 与时间戳信息。
 * 
 * @since 0.6.0
 */
@InternalApi
public class StreackLibNewableException extends Exception {
  /** 当前 Exception 的唯一 ID */
  public final Long INSTANCE_ID = StreackLib.getUniqueID();

  /** 当前 Exception 实例初始化的时间戳 */
  public final Long TIME_STAMP = System.currentTimeMillis();

  /**
   * 构造一个新的异常，默认 detail message 为 {@code null}，cause 未初始化，
   * 可在后续通过 {@link #initCause} 初始化。
   */
  public StreackLibNewableException() {
    super();
  }

  /**
   * 构造一个指定详细消息的新异常。
   *
   * @param message 详细消息，将在 {@link #getMessage()} 中返回
   */
  public StreackLibNewableException(String message) {
    super(message);
  }

  /**
   * 构造一个指定详细消息和原因的新异常。
   * <p>
   * 注意：{@code cause} 的详细消息不会自动并入本异常的详细消息。
   *
   * @param message 详细消息，将在 {@link #getMessage()} 中返回
   * @param cause   原因，将在 {@link #getCause()} 中返回。可为 {@code null}
   */
  public StreackLibNewableException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 构造一个指定原因的新异常，详细消息为
   * {@code (cause==null ? null : cause.toString())}。
   * 适用于仅作为其他可抛出对象包装器的情况。
   *
   * @param cause 原因，将在 {@link #getCause()} 中返回。可为 {@code null}
   */
  public StreackLibNewableException(Throwable cause) {
    super(cause);
  }

  /**
   * 构造一个指定详细消息、原因，以及是否启用抑制和可写堆栈跟踪的新异常。
   *
   * @param message            详细消息
   * @param cause              原因。可为 {@code null}
   * @param enableSuppression  是否启用抑制
   * @param writableStackTrace 是否可写堆栈跟踪
   */
  protected StreackLibNewableException(String message, Throwable cause,
      boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
