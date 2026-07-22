package com.github.streackmc.StreackLib.errors;

import com.github.streackmc.StreackLib.errors.raw.StreackLibNewableRuntimeException;

/**
 * 没有任何含义的Exception，抛出它不会有任何意义，捕获它也没有任何意义。
 * 仅用于“跳出” try...catch 块
 * 
 * @author kdxiaoyi
 * @since 0.4.5
 */
public class IgnoredException extends StreackLibNewableRuntimeException {
  /**
   * 用法见下，不过这个是带消息的
   * <p>
   * 直接使用 return 会导致 SomeFunc3() 也不执行
   * 
   * <pre>
   * try {
   *   SomeFunc1(); // 一些代码
   *   if (checkSomeing()) {
   *     throw new IgnoredException("msg"); // 直接中断
   *   }
   *   SomeFunc2(); // 这里不会被执行
   * } catch (IgnoredException e1) {// 该异常不受检，你不写这个也可以，别忘了过滤
   *   IdkWhatDoYouWantToDo(e1.getLocallizedMessage());
   * } catch (Exception e2) {
   *   CatchExcepitonFunc(); // 可以处理其它错误
   * } finally {
   *   FinallyFunc(); // 此处无论如何都执行
   * }
   * SomeFunc3(); // 也会被执行
   * </pre>
   * 
   * @param msg 你为什么要这么做？
   * @see {@link #filterIgnoredException(Exception)} 过滤本异常
   * @see {@link #filterUncheckedException(Exception)} 过滤全部不受检异常
   */
  public IgnoredException(String msg) {
    super(msg);
  }

  /**
   * 用法见下
   * <p>
   * 直接使用 return 会导致 SomeFunc3() 也不执行
   * 
   * <pre>
   * try {
   *   SomeFunc1(); // 一些代码
   *   if (checkSomeing()) {
   *     throw new IgnoredException(); // 直接中断
   *   }
   *   SomeFunc2(); // 这里不会被执行
   * } catch (IgnoredException e1) {// 该异常不受检，你不写这个也可以，别忘了过滤
   * } catch (Exception e2) {
   *   CatchExcepitonFunc(); // 可以处理其它错误
   * } finally {
   *   FinallyFunc(); // 此处无论如何都执行
   * }
   * SomeFunc3(); // 也会被执行
   * </pre>
   * 
   * @see {@link #filterIgnoredException(Exception)} 过滤本异常
   * @see {@link #filterUncheckedException(Exception)} 过滤全部不受检异常
   */
  public IgnoredException() {
    super("");
  }

  /** 使用该方法可以直接过滤掉全部不受检异常 */
  public static void filterUncheckedException(Exception e) throws RuntimeException {
    if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    }
  }

  /** 使用该方法可以直接过滤掉本异常 */
  public static void filterIgnoredException(Exception e) throws IgnoredException {
    if (e instanceof IgnoredException) {
      throw (IgnoredException) e;
    }
  }
}
