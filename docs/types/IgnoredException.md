# `SFile`
这是一个已封装的`Throwable`，没有任何象征意义，没有任何特殊功能。
最大的用处是“在`try...catch`里面插入「`break`」”

## 开始使用

```java
import com.github.streackmc.StreackLib.types.IgnoredException;

try {
  SomeFunc1(); // 一些代码
  if (checkSomeing()) {
    throw new IgnoredException(); // 直接中断
  }
  SomeFunc2(); // 这里不会被执行
} catch (IgnoredException e1) {
} catch (Exception e2) {
  CatchExcepitonFunc(); // 可以处理其它错误
} finally {
  FinallyFunc(); // 此处无论如何都执行
}
SomeFunc3(); // 也会被执行
```

本类型的开发初衷是为了解决`return`会导致`SomeFunc3()`也无法执行的问题，相当于“跳出`try...catch`块”。