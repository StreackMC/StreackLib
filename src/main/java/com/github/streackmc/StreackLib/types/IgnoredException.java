package com.github.streackmc.StreackLib.types;

/**
 * 没有任何含义的Exception，抛出它不会有任何意义，捕获它也没有任何意义。
 * 仅用于“跳出” try...catch 块
 * 
 * @author kdxiaoyi
 * @since 0.4.5
 */
public class IgnoredException extends Exception {
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
   * } catch (IgnoredException e1) {
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
   * } catch (IgnoredException e1) {
   * } catch (Exception e2) {
   *   CatchExcepitonFunc(); // 可以处理其它错误
   * } finally {
   *   FinallyFunc(); // 此处无论如何都执行
   * }
   * SomeFunc3(); // 也会被执行
   */
  public IgnoredException() {
    super("");
  }
}
