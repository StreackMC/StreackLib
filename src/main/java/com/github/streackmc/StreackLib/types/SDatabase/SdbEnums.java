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
    /** 作为查询 */
    SELECT,
    /** 作为静态值 */
    VALUE,
    /** 作为静态值，且设置了回退值 */
    VALUE_WITH_FALLBACK;
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
    SQLITE("db"), MYSQL(null), SLDB("sldb");

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
        case "sldb":
        case "streacklibdb":
        case "streacklibdatabase":
        case "sldatabase":
          return SLDB;

        default:
          return null;
      }
    }
  }
}
