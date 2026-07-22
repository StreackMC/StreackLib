package com.github.streackmc.StreackLib.types.SDatabase;

import java.sql.Connection;

/**
 * <h2>Backend</h2>
 * 数据库后端接口，封装连接池/single-connection的借还操作。
 *
 * @since 0.6.0
 */
interface Backend {

  /**
   * 从池中借出一个连接。
   *
   * @return 一个可用的{@link Connection}。调用者的{@code close()}行为：
   *         <ul>
   *           <li><b>SqliteBackend</b>：被拦截，不真正关闭</li>
   *           <li><b>MysqlBackend</b>：归还给 HikariCP 池</li>
   *         </ul>
   */
  Connection borrowConnection() throws Exception;

  /** 销毁整个连接池/关闭底层连接 */
  void close();
}
