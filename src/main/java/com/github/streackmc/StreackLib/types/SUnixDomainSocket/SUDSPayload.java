package com.github.streackmc.StreackLib.types.SUnixDomainSocket;

import java.util.Map;

import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SUDSConnect</h2>
 * 
 * 本类是用于包装 {@link SUDSLink} 的数据的类。
 * 
 * @since 0.6.2
 * @author kdxiaoyi
 */
public class SUDSPayload extends StreackLibNewable {

  /** 原始数据 */
  private final byte[] data;

  /** 封装数据 */
  private final SConfig config = new SConfig((Map<String, Object>)null, SConfig.TYPES.JSON, null);

  /**
   * 新建一个数据包
   * 
   * @param data 数据
   */
  public SUDSPayload(byte[] data) {
    this.data = data;
  }

  /**
   * 获取数据
   * 
   * @return 数据
   */
  public byte[] getData() {
    return data;
  }
  
}
