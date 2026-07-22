package com.github.streackmc.StreackLib.types.SDatabase;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * <h2>SdbEnums</h2>
 * StreackLib的数据库模块的枚举常量声明
 * 
 * @since 0.6.0
 * @author kdxiaoyi
 */
public class SdbEnums {
  /** 禁止实例化 */
  private SdbEnums() {
  }
  
  /** 操作类型 */
  public enum ACTION_TYPE {
    /** 查询 */
    SELECT("SELECT"),
    /** 修改 */
    UPDATE("UPDATE"),
    /** 有则更新，无则插入 */
    MERGE("MERGE"),
    /** 删除 */
    DELETE("DELETE"),
    /** 建表 */
    CREATE("CREATE"),
    /** 修改结构 */
    ALTER("ALTER"),
    /** 删除表或库，<b>连表带数据全部消失</b>，<b>无法恢复</b> */
    DROP("DROP"),
    /** 删除表中全部数据，<b>无法恢复</b> */
    TRUNCATE("TRUNCATE"),
    /** 设置回滚的保存点 */
    SAVEPOINT("SAVEPOINT"),
    /** 回滚事务（撤销未提交的修改） */
    ROLLBACK("ROLLBACK"),
    /** 提交事务（使修改永久生效） */
    COMMIT("COMMIT"),
    /** 从其它数据来源创建临时表以供使用 */
    WITH("WITH");

    /** 操作名称 */
    public final String name;
    /** 获取操作名称 */
    @Override
    public String toString() { return this.name; }
    ACTION_TYPE(String name) { this.name = name; }
  }

  /** 查询类型 */
  public enum QUERY_TYPE {
    /** 作为查询 */
    SELECT,
    /** 作为静态值 */
    VALUE,
    /** 作为静态值，且设置了回退值 */
    VALUE_WITH_FALLBACK;
  }

  /** 数据库类型 */
  public enum DB_TYPE {
    SQLITE("db"), MYSQL(null);

    /** 文件拓展名 */
    public final String FILE_EXTENSION_NAME;

    DB_TYPE(String fe) {
      FILE_EXTENSION_NAME = fe;
    }

    /** 将字符串类型转为枚举类型 */
    @Nullable
    public static DB_TYPE parseType(String raw) {
      switch (Objects.requireNonNullElse(raw, "").toLowerCase()) {
        case "mysql":
          return MYSQL;
        case "sqlite":
        case "sqllite":
          return SQLITE;

        default:
          return null;
      }
    }
  }
}
