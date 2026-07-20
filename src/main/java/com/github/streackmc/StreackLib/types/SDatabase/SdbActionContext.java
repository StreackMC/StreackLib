package com.github.streackmc.StreackLib.types.SDatabase;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SdbActionContext</h2>
 * 数据库操作上下文，用于在各部分间传递操作要干什么。通常情况下由 StreackLib 自动创建与继承，所有数据都不会是 Null 。
 * 
 * @since 0.6.0
 * @author kdxiaoyi
 */
public class SdbActionContext extends StreackLibNewable {
  /** 操作类型 */
  @Nonnull
  public final SdbEnums.ACTION_TYPE type;
  /** 操作的数据库对象 */
  @Nonnull
  public final SdbDatabase database;
  /** 操作的主要表 */
  public final String table;
  /** 表的别名 */
  public final Map<String, String> alias;
  /** 操作参数 */
  public final SConfig param;
  /** 操作的断言限制 */
  public final SdbStatement filter;

  /**
   * 新建一个操作上下文
   * 
   * @since 0.6.0
   * @param at     操作类型，不可为 null
   * @param db     操作数据库，不可为 null
   * @param table  操作主要表，默认 "default"
   * @param param  操作参数，默认没有
   * @param filter 断言限制，默认空
   * @throws NullPointerException 有些参数为 Null
   */
  public SdbActionContext(SdbEnums.ACTION_TYPE at, SdbDatabase db, String table, SConfig param, SdbStatement filter) {
    this.type = Objects.requireNonNull(at, "不可接受的操作类型 Null");
    this.database = Objects.requireNonNull(db, "不可接受的操作类型 Null");
    this.table = Objects.requireNonNullElse(table, "default");
    this.param = Objects.requireNonNullElse(param, new SConfig("", SConfig.TYPES.JSON, null));
    this.filter = Objects.requireNonNullElse(filter, new SdbStatement());
    this.alias = new HashMap<>();
  }

  /**
   * 新建一个带有别名设置的操作上下文
   * 
   * @since 0.6.0
   * @param at     操作类型，不可为 null
   * @param db     操作数据库，不可为 null
   * @param table  操作主要表，默认 "default"
   * @param param  操作参数，默认没有
   * @param filter 断言限制，默认空
   * @throws NullPointerException 有些参数为 Null
   */
  public SdbActionContext(SdbEnums.ACTION_TYPE at, SdbDatabase db, String table, Map<String,String> alias, SConfig param, SdbStatement filter) {
    this.type = Objects.requireNonNull(at, "不可接受的操作类型 Null");
    this.database = Objects.requireNonNull(db, "不可接受的操作类型 Null");
    this.table = Objects.requireNonNullElse(table, "default");
    this.param = Objects.requireNonNullElse(param, new SConfig("", SConfig.TYPES.JSON, null));
    this.filter = Objects.requireNonNullElse(filter, new SdbStatement());
    this.alias = Objects.requireNonNullElse(alias, new HashMap<>());
  }
}
