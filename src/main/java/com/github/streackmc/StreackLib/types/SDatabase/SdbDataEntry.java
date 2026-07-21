package com.github.streackmc.StreackLib.types.SDatabase;

import java.util.List;

import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SdbDataEntry</h2>
 * 数据库操作结果封装
 * 
 * @see {@link SdbDatabase} 使用该类连接数据库并操作以获取本对象
 * @since 0.6.0
 * @author kdxiaoyi
 * @author Deepseek
 */
public class SdbDataEntry extends StreackLibNewable {
  /** 本次编辑/查询受影响的行数 */
  public final long InfluencedLines;
  /** 本次编辑后/查询到的行 */
  public final List<SConfig> ResultLines;
  
  SdbDataEntry(long il) {
    this(il, List.of());
  }

  SdbDataEntry(long il, SConfig d) {
    this(il, List.of(d));
  }

  SdbDataEntry(long il, List<SConfig> rl) {
    InfluencedLines = il;
    rl.forEach(data -> {
      if (!data.getWriteModeLocked()) {
        data.setWriteModeForever(SConfig.WRITE_MODE.READONLY);
      }
    });
    ResultLines = rl;
  }
}
