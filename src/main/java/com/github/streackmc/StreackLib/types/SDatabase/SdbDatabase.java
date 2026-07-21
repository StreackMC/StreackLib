package com.github.streackmc.StreackLib.types.SDatabase;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.errors.ConfigNotFoundException;
import com.github.streackmc.StreackLib.errors.InvaildConfigException;
import com.github.streackmc.StreackLib.types.SConfig;
import com.github.streackmc.StreackLib.types.StreackLibNewable;

/**
 * <h2>SdbDatabase</h2>
 * 对 SQL 数据库的封装，使得数据库像 Excel 一样简单。
 * <p>
 * 基本用法是，先 new 本类获得可使用的数据库对象，再使用内置方法或 SQL 语句进行数据库操作。
 * 
 * <h3>操作上下文</h3>
 * 每个操作都会有一个操作上下文，每个操作上下文会承载一个操作及其参数与一个断言树，后者是由断言通过 {@link SdbStatement#and()} 等连接来组成的。
 * 
 * <h3>断言与断言树</h3><small>主条目：{@link SdbStatement}</small><p>
 * 断言可以对操作进行限制或过滤，比如通过断言可以限制查询只能返回<code>'username'列的值以'_'开头</code>的行，对删除等同理。
 * 
 * @see {@link SdbStatement} 对操作进行断言限制
 * @see {@link SdbEnums} 数据库模块的全部枚举常量
 * @see {@link SdbDataEntry} 对操作结果的封装
 * @since 0.6.0
 * @author kdxiaoyi
 * @author Deepseek 提供知识支持
 */
public class SdbDatabase extends StreackLibNewable {
  // ============================================
  // 变量与常量
  // ============================================

  private final SConfig profileConf;
  private final Backend backend;

  // ============================================
  // 连接数据库
  // ============================================

  /**
   * @param profile 要使用的档案名，为空或 Null 时视作 "default"
   * @throws IOException             读写错误
   * @throws InvaildConfigException  Profile 无效
   * @throws ConfigNotFoundException Profile 不存在
   */
  public SdbDatabase(@Nullable String profile) throws Exception {
    profileConf = StreackLib.ENV.conf.getSection("databases." + (profile == null || profile.isBlank() ? "default" : profile), SConfig.TYPES.JSON, null);
    String pendingMode = profileConf.getString("mode", "").toLowerCase();
    switch (pendingMode) {
      case "sqlite":
        this.backend = null;
        break;
      case "sldb":
        this.backend = null;
        break;
      case "mysql":
        this.backend = null;
        break;

      default:
        throw new ConfigNotFoundException("不存在的数据库 Profile：" + profile);
    }
  }
  
  // ============================================
  // 外部“傻瓜”接口
  // ============================================

  /**
   * 移动或删除一个表
   * 
   * @param existed 已存在的表，不存在会尝试新建
   * @param newname 新表名。若为空或 Null 则删除表
   * @throws NullPointerException 已存在的表是 Null
   */
  public SdbDatabase moveTable(@Nonnull String existed, @Nullable String newname) {
    return this;
  }

  /**
   * 新建一个表
   * 
   * @param name 新表名
   * @throws NullPointerException 传入 Null
   */
  public SdbDatabase moveTable(@Nonnull String name) {
    return this;
  }

  /**
   * 开始构建一次数据库操作。
   * 
   * @param type 操作类型
   * @return 操作上下文，可通过链式调用设置参数后 {@link SdbActionContext#execute()} 执行
   */
  public SdbActionContext act(SdbEnums.ACTION_TYPE type) {
    return new SdbActionContext(type, this);
  }

  /**
   * 直接执行 SQL 命令。<b>请注意 SQL 注入攻击。</b>
   * 
   * @param cmd SQL 命令
   * @return 操作结果
   */
  public SdbActionContext act(String cmd) {
    return null;
  }

  // ============================================
  // 数据库衔接实现
  // ============================================
  /** 数据库实现接口 */
  private interface Backend {
  }
}