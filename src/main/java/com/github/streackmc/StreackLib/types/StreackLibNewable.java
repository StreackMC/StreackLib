package com.github.streackmc.StreackLib.types;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.github.streackmc.StreackLib.self.logger;

/**
 * StreackLib 可实例化类的公共部分
 * 
 * @since 0.6.0
 */
public abstract class StreackLibNewable {
  /** 唯一ID生成器 */
  private static final AtomicLong uniqueIDCounter = new AtomicLong(0x6735L);
  static {
    uniqueIDCounter.updateAndGet((x) -> x + ((long) (Math.random() * 10000)));
  }

  /** 当前实例的唯一ID */
  public final long INSTANCE_ID = getUniqueID();

  /** 当前实例初始化的时间节点 */
  public final long TIME_STAMP = System.currentTimeMillis();

  /**
   * @return 获取一个全局唯一的ID
   * @since 0.6.0
   */
  public static long getUniqueID() {
    try {
      return uniqueIDCounter.getAndUpdate(Math::incrementExact);
    } catch (ArithmeticException e) {
      logger.warn("唯一 ID 计数器溢出（已超过 %s），将回绕到 %s", Long.MAX_VALUE, Long.MIN_VALUE);
      uniqueIDCounter.set(Long.MIN_VALUE);
      return uniqueIDCounter.getAndIncrement();
    }
  }

  /**
   * @return 获取一个全局唯一的UUID
   * @since 0.6.0
   */
  public static String getUUID() {
    return UUID.randomUUID().toString();
  }
}