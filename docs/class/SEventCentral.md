# `SEventCentral`
本模块是仿照 JavaScript 中事件系统设计的一个类，用来提供监听和广播事件机制。适合解耦模块间通信、处理异步通知场景。

本文档也包含对 `SEvent` 事件对象和 `EventBuilder` 构建器的说明。

> 本文档有AI参与编写的部分。

## 前言
`SEventCentral` 可以：

* 强引用和弱引用监听（自动清理）
* 链式构建事件数据
* 事件广播与异常隔离
* 带时间戳和调用者溯源的事件对象
* 类型安全的数据存取

## 基本使用

### 注册监听器
要想监听某个事件：

```java
import com.github.streackmc.StreackLib.utils.SEventCentral;

// 强引用监听（需手动移除）
int listenerId = SEventCentral.addEventListener("user:login"/* 此处所有事件名都是AI乱编的，请查询模块对应文档 */, event -> {
    String username = event.getData("username", String.class);
    logger.info("用户登录:", username);
});

// 移除监听
SEventCentral.removeEventListener(listenerId);

// 移除全部监听
SEventCentral.removeAllEventListener();
// 内部API

// 弱引用监听（适合临时组件，自动清理）
SEventCentral.addWeakEventListener("cache:expire", event -> {
  // 当此回调不再被外部持有时，自动移除
    cleanCache(event.getData("key", String.class));
});
// 弱引用监听器会在其对象不再被任何地方强引用时自动失效，无需手动移除，但也无法预测确切的回收时机。
```

### 广播事件
广播采用构建器模式，链式设置数据后触发：

```java
SEventCentral.broadcastEvent("user:login")
    .set("username", "Streack")
    .set("ip", "127.0.0.1")
    .set("timestamp", System.currentTimeMillis())
    .broadcast();
```

> 广播后事件对象进入只读状态，任何修改尝试都会抛出 `IllegalStateException` 。

### 异常处理
事件分发采用异常隔离策略，单个监听器抛异常**不会**中断其他监听器执行。
异常信息通过 `logger.serve` 自动记录，包含事件名和监听器 `ID`。
建议在监听器内部自行 `try-catch` 关键业务逻辑。

### 命名规范
建议使用常量命名以防低级拼写错误：

```java
public final class UserEvents {
    public static final String LOGIN = "user:login";
    public static final String KEY_USERNAME = "username";
    // ...
}

// 使用
SEventCentral.broadcastEvent(UserEvents.LOGIN)
    .set(UserEvents.KEY_USERNAME, name)
    .broadcast();
```

StreackLib 中的事件名也遵守此规范，存储在 `SEventCentral.internalEvents.[model].[name]` 中。

# 事件数据对象 `SEvent`
监听器接收的 `SEvent` 对象包含以下元数据：

```java
import com.github.streackmc.StreackLib.utils.SEvent;

// 获取事件构造时间戳（毫秒）
long ts = event.getTimestamp();

// 判断是否受信任（通过 SEventCentral 广播的为 true）
boolean trust = event.isTrust();

// 获取调用者标识（通常为发起广播的类名）
String caller = event.getCaller();

// 获取业务数据
String username = event.getData("username", String.class);
Object raw = event.getData("key"); // 返回 Object，需自行强转，见后文

// 获取所有数据（只读视图）
Map<String, Object> all = event.getAllData();
```

## 类型安全
除非你是Java高手，否则建议使用以下方法获取数据以免出错：

```java
// 推荐
String name = event.getData("name", String.class);
List<String> tags = event.getData("tags", List.class);

// 不推荐（可能抛出 ClassCastException）
String name = (String) event.getData("name");
```