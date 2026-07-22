package com.github.streackmc.StreackLib.errors.raw;

import org.apache.logging.log4j.util.InternalApi;

import com.github.streackmc.StreackLib.StreackLib;

/**
 * StreackLib 中间层可实例化异常，用于携带唯一实例 ID 和初始化时间戳。
 * 
 * @since 0.6.0
 */
@InternalApi
public class StreackLibNewableRuntimeException extends RuntimeException {
  /** 当前唯一异常实例 ID */
  public final Long INSTANCE_ID = StreackLib.getUniqueID();

  /** 异常实例创建时间戳 */
  public final Long TIME_STAMP = System.currentTimeMillis();

  /**
   * 构造一个新的异常，默认消息为 {@code null}。
   * 原因尚未初始化，可通过 {@link #initCause} 之后设置。
   */
  public StreackLibNewableRuntimeException() {
    super();
  }

  /**
   * 构造一个新的异常，指定详细消息。
   * 原因尚未初始化，可通过 {@link #initCause} 之后设置。
   *
   * @param message 异常详细消息，供 {@link #getMessage()} 获取。
   */
  public StreackLibNewableRuntimeException(String message) {
    super(message);
  }

  /**
   * 构造一个新的异常，指定详细消息和原因。
   *
   * @param message 异常详细消息，供 {@link #getMessage()} 获取。
   * @param cause   异常原因，供 {@link #getCause()} 获取。可为 {@code null}。
   * @since 1.4
   */
  public StreackLibNewableRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * 构造一个新的异常，指定原因，消息为 {@code (cause==null ? null : cause.toString())}。
   * 该构造器适用于对其他 Throwable 的包装。
   *
   * @param cause 异常原因，供 {@link #getCause()} 获取。可为 {@code null}。
   * @since 1.4
   */
  public StreackLibNewableRuntimeException(Throwable cause) {
    super(cause);
  }

  /**
   * 构造一个新的异常，指定详细消息、原因、是否启用抑制以及是否可写堆栈跟踪。
   *
   * @param message            异常详细消息。
   * @param cause              异常原因。可为 {@code null}。
   * @param enableSuppression  是否启用抑制。
   * @param writableStackTrace 是否允许写堆栈跟踪。
   * @since 1.7
   */
  protected StreackLibNewableRuntimeException(String message, Throwable cause,
      boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }
}
