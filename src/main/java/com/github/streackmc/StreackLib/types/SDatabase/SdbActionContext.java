package com.github.streackmc.StreackLib.types.SDatabase;

import java.util.HashMap;
import java.util.Map;

import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SdbActionContext</h2>
 * 数据库操作上下文，用于链式构建并执行一次数据库操作。
 * <p>
 * 通过 {@link SdbDatabase#action(SdbEnums.ACTION_TYPE)} 创建，然后链式设置参数：
 * <pre><code>
 * db.action(SdbEnums.ACTION_TYPE.SELECT)
 *   .table("users")
 *   .alias("u", "users")
 *   .filter(new SdbStatement().equal("name", "abc"))
 *   .limit(10)
 *   .execute();
 * </code></pre>
 * 
 * @since 0.6.0
 * @author kdxiaoyi
 */
public class SdbActionContext extends StreackLibNewable {
  /** 操作类型 */
  public final SdbEnums.ACTION_TYPE type;
  /** 操作的数据库对象 */
  public final SdbDatabase database;
  /** 操作的主要表 */
  public String table;
  /** 表的别名映射：key=别名, value=真实表名 */
  public Map<String, String> alias;
  /** 操作参数 */
  public SConfig param;
  /** 操作的断言限制 */
  public SdbStatement filter;
  /** 操作的数量限制 */
  public int limit;

  /** 由 {@link SdbDatabase#action(SdbEnums.ACTION_TYPE)} 创建 */
  SdbActionContext(SdbEnums.ACTION_TYPE type, SdbDatabase database) {
    this.type = type;
    this.database = database;
    this.alias = new HashMap<>();
    this.filter = new SdbStatement();
    this.param = new SConfig("", SConfig.TYPES.JSON, null);
  }

  /** 指定操作的主要表 */
  public SdbActionContext table(String table) {
    this.table = table;
    return this;
  }

  /** 添加单个别名映射 */
  public SdbActionContext alias(String key, String value) {
    this.alias.put(key, value);
    return this;
  }

  /** 批量设置别名映射 */
  public SdbActionContext alias(Map<String, String> alias) {
    if (alias != null) this.alias.putAll(alias);
    return this;
  }

  /** 设置操作参数 */
  public SdbActionContext param(SConfig param) {
    this.param = param;
    return this;
  }

  /** 设置操作的断言限制 */
  public SdbActionContext filter(SdbStatement filter) {
    this.filter = filter;
    return this;
  }

  /** 设置操作的数量限制 */
  public SdbActionContext limit(int limit) {
    this.limit = limit;
    return this;
  }

  /**
   * 执行操作。
   * @return 操作结果（暂未实现）
   */
  public SdbDataEntry execute() {
    return null;
  }
}
