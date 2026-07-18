package com.github.streackmc.StreackLib.types;

import com.github.streackmc.StreackLib.StreackLib;

/**
 * StreackLib 可实例化类的公共部分
 * 
 * @since 0.6.0
 */
public abstract class StreackLibNewable {
  /** 当前实例的唯一ID */
  public final Long INSTANCE_ID = StreackLib.getUniqueID();

  /** 当前实例初始化的时间节点 */
  public final Long TIME_STAMP = System.currentTimeMillis();
}