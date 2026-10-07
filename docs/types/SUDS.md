# `SUDS`（Unix Domain Socket 链路）

## 前言

`SUDS` 是一套基于 **UDS（Unix Domain Socket）** 的进程间通讯模块，用于让同一台机器上的两个进程互相收发报文。
它比 TCP/IP 少一层网络协议栈，因此在同机通讯场景下更快、更省；代价是**只能在同一台机器内使用**，
跨机器请改用 [HTTPServer](./HTTPServer.md)。

模块由四个类组成，职责边界很清晰：

| 类 | 角色 |
|:-:|------|
|`SUDSAbsLink`|抽象公共基类，`SUDSServerLink` 与 `SUDSClientLink` 都继承它，收发/监听/关闭行为都在这里|
|`SUDSServerLink`|服务端：绑定 socket 文件并持续接受客户端接入，可同时持有 N 条连接|
|`SUDSClientLink`|客户端：连接到已有 socket 文件，只持有 1 条连接（即服务端）|
|`SUDSPeer`|一条**已建立**的连接。服务端每个接入的客户端一个，客户端只有指向服务端的那一个|
|`SUDSPayload<T>`|一条报文的数据载体|

因为三者共享同一个基类，所以 `close()`、`send()`、`onMessage()` 这些方法在服务端和客户端上的用法完全一致。

### 运行环境要求

* 支持 POSIX 与 AF_UNIX Socket 的文件系统；
* 在 Windows 上需要 Windows 10 1803 / Windows Server 2019 及以上；
* JDK 16+。

创建前可以先检查，这个方法只返回布尔值、不抛异常：

```java
if (!SUDSAbsLink.isSupported()) {
  // 当前环境不支持 UDS
}
```

## 快速开始

### 服务端

```java
import com.github.streackmc.StreackLib.types.SUnixDomainSocket.*;

import java.util.Map;

SUDSServerLink server = new SUDSServerLink("my-app", SUDSProtocol.JSON_LINES);

// 收到报文就回一条原样回声
server.onMessage((peer, payload) -> {
  peer.send(SUDSPayload.ofJson(Map.of("echo", payload.asMap())));
});

// 广播给所有已接入的客户端
server.send(SUDSPayload.ofJson(Map.of("type", "broadcast")));

// ...

server.close();
```

`token` 是链路的标识符，会参与 socket 文件名的生成，收发两端必须一致。
UDS 只能同机通讯，所以不需要 IP 与端口。

### 客户端

```java
SUDSClientLink client = new SUDSClientLink("my-app", SUDSProtocol.JSON_LINES);

// 不关心来源连接时写起来最短
client.onMessage(payload -> logger.info("收到: ", payload.asMap()));

client.send(SUDSPayload.ofJson(Map.of("hello", "world")));

// ...

client.close();
```

> **客户端不会自动重连**，建连失败会直接在构造阶段抛 `IOException`。
> 重连策略请由调用方决定，典型做法是在 `ON_DISCONNECT` 事件上安排下一次重试。

## 两种链路协议

创建链路时必须指定协议模式，它决定了**一条报文的边界在哪里**：

| 协议 | 报文边界 | `SUDSPayload.getData()` | `SUDSPayload.getValue()` |
|:-:|------|------|------|
|`SUDSProtocol.JSON_LINES`|一段 JSON 文本 + 结尾 LF(`\n`)|JSON 文本的字节（**不含** LF）|解析出的 `Map<String, Object>`|
|`SUDSProtocol.RAW`|不做分帧，一次 `read` 就是一条|原始字节块|`byte[]`，与 `getData()` 一致|

### `JSON_LINES`：内置协议（推荐）

链路自动完成分帧与 JSON 解析，你拿到的永远是完整的、已经解析好的报文，**不需要关心粘包与拆包**。

* 接收时按 LF 切分，自动忽略空行（可以拿连续换行当心跳），并且兼容 CRLF 换行；
* 发送时由链路在写入后追加 LF，所以 `getData()` 里只有 JSON 文本本身——
  这样「收到再转发」的载荷也能被正确发出去；
* 报文由 Gson 以紧凑模式序列化，自身不含裸换行，所以用 LF 做分隔符是安全的。

**没有历史包袱时请一律使用它。**

### `RAW`：自定义协议

链路对内容完全不解读，`SUDSPayload` 直接给出原始字节流，用于对接既有二进制协议（自定义帧头、protobuf 直连等）。

> **UDS 是字节流，不是消息流**，和 TCP 一样会有粘包与拆包。选 `RAW` 就意味着
> **分帧必须由你的协议自己负责**——「一次 `read` = 一条报文」只是实现细节，
> 它取决于操作系统的读取时机，不能当作可靠的消息边界。

## 报文：`SUDSPayload<T>`

一条报文同时持有两样东西，二者总是配套的：`getData()` 是链路上真正跑着的**原始字节**，
`getValue()` 是**解析后的值**，其类型由泛型参数 `T` 决定。

| 工厂方法 | 用途 |
|:-:|------|
|`raw(byte[])` / `raw(byte[], off, len)`|构造 `RAW` 载荷|
|`json(byte[] line)`|解析一条 JSON 报文，`T` 为 `Map<String, Object>`，**要求根节点是 JSON 对象**|
|`json(byte[] line, Class<R> type)` / `json(byte[], Type type)`|解析并一步映射为你的类型，根节点不限|
|`ofJson(Object value)`|把对象序列化为一条 JSON 报文用于发送，同样要求序列化结果是对象|
|`of(byte[] data, T value, boolean raw)`|自定义协议的通用出口，直接给出字节与解析值|

| 实例方法 | 说明 |
|:-:|------|
|`getData()`|原始字节，返回的是**拷贝**，可以随意改动|
|`getValue()`|解析后的值|
|`getValue(Class<R>)` / `getValue(Type)`|把同一段字节按 JSON **重新解析**成另一种类型，不用重建对象|
|`asMap()`|JSON 对象视图，无论 `T` 是什么都会尝试按 JSON 对象解析一次|
|`asConfig()`|解析成 `SConfig`，从而复用 `getString/getInt/...` 系列取值接口|
|`asString()` / `length()` / `isRaw()`|文本视图 / 字节长度 / 是否为自定义协议载荷|

### 扩展性

泛型参数 `T` 就是扩展点。想让链路上的 JSON 一步变成自己的 POJO：

```java
public class Msg {
  String type;
  int seq;
}

client.onMessage((peer, payload) -> {
  Msg msg = payload.getValue(Msg.class);   // 同一段字节，换个视角看
  // ...
});
```

或者从一开始就按目标类型解析：

```java
SUDSPayload<Msg> out = SUDSPayload.json(bytes, Msg.class);
```

## 连接：`SUDSPeer`

| 方法 | 说明 |
|:-:|------|
|`send(SUDSPayload<?>)`|向这条连接写一条报文；同一连接的多次写入内部串行，可多线程并发调用|
|`close()`|断开这条连接，幂等|
|`isOpen()`|连接是否仍可用|
|`getId()`|连接标识符，在所属链路内唯一|
|`getLink()`|所属链路|

### 怎么知道「谁是谁」

UDS 与 TCP 不同，客户端侧**没有**可用的对端地址（连接是匿名的），所以无法靠地址识别对端。
服务端要区分客户端，只能由上层协议约定——客户端连上后先自报家门：

```java
Map<String, String> peerOf = new ConcurrentHashMap<>();

server.onMessage((peer, payload) -> {
  Object who = payload.asMap().get("who");
  if (who != null) peerOf.put(String.valueOf(who), peer.getId());   // 登记身份
});
```

> 注意 `getId()` 是**本地**标识：同一条连接在服务端与客户端各自持有一个 `SUDSPeer`，
> 两边生成的 ID 互不相同，**不能跨进程比较**，也不能拿来当协议里的会话 ID。
> 需要在两端指向同一条连接时，请在协议层自行发放 ID。

## 生命周期与事件

`close()` 是**幂等**的，保证不抛异常，可以安全地放在 `finally` 或 try-with-resources 里。
服务端的 `close()` 还会顺手删掉自己创建的 socket 文件。

`SUDSAbsLink.EVENTS` 定义了四个可监听的事件：

| 事件 | 触发时机 | 携带数据 |
|:-:|------|------|
|`ON_MESSAGE`|收到一条报文|`peer`、`payload`|
|`ON_CONNECT`|建立了一条连接|`peer`|
|`ON_DISCONNECT`|断开了一条连接|`peer`|
|`ON_ERROR`|链路上发生错误|`peer`（可能为 null）、`exception`|

```java
SEventCentral.addEventListener(SUDSAbsLink.EVENTS.ON_DISCONNECT, event -> {
  // 安排重连……
});
```

若只是想在收到报文时做点事，直接用 `onMessage` 即可；它是回调注册式的，可以注册多个，
返回的 ID 可用于注销：

```java
int id = link.onMessage(payload -> { /* ... */ });
link.removeMessageListener(id);
```

> 单个监听器抛出的异常会被收敛成一条链路错误，**不会**影响其它监听器，也不会拖垮读线程。

## 高级

### 报文长度上限

`SUDSAbsLink.MAX_MESSAGE_SIZE` 是**单条报文的字节上限**，默认 1 MiB。超出时链路会报错并断开该连接。

它只对 `JSON_LINES` 生效（该协议需要缓冲到换行符才能确认一条报文结束），`RAW` 不做分帧、天然不受限制。

```java
server.MAX_MESSAGE_SIZE = 64 * 1024;
```

### 广播与点对点

* `send(payload)`：在服务端上是**广播**，在客户端上是发给服务端。
  服务端没有任何客户端接入时静默返回，需要区分时可先查 `getPeerCount()`。
* `send(peer, payload)`：只发给指定的那一条连接。
* `disconnect(peer)` / `disconnect(id)` / `disconnectAll()`：服务端断开指定或全部客户端，自身继续监听。

> 客户端重写了 `send(payload)`：一旦服务端断开，它会直接抛 `IllegalStateException`，
> 而不是把报文静默丢掉。静默丢数据比抛异常更难排查。

### socket 文件管理

服务端在 `bind` 前会检查目标文件：

* 文件**不存在**：正常绑定；
* 文件存在但**连不上**：判定为上次进程崩溃留下的僵尸文件，自动删除后绑定；
* 文件存在且**连得上**：说明另一个服务端正在使用它，直接抛 `IllegalStateException`，绝不把对方的 socket 顶掉。

`close()` 会删除自己创建的 socket 文件（仅当本次确实绑定成功过），所以正常退出不会留下垃圾文件。

### 线程模型

* 每条连接有且只有一个读线程，读到的报文按顺序回调，因此**同一连接的报文不会并发回调**；
* 不同连接之间是并发的，回调里请注意共享状态的可见性；
* 回调运行在链路的读线程上，**不要**在回调里做长时间阻塞操作，否则会阻塞该连接后续报文的读取；
* `send()` 对同一连接是串行的（内部加写锁），可安全地被多线程调用；但它是阻塞写，
  对端迟迟不读取时会阻塞到写完为止。

## 注意事项

* **载荷类型必须与协议匹配**。在 `RAW` 链路上发 `ofJson(...)` 的产物（或反之）会抛 `IllegalArgumentException`，
  这是刻意设计的：错配的载荷发出去只会让对端解析失败，不如在本地立刻炸掉；
* `getData()` 返回的是拷贝，`RAW` 模式下频繁读取大报文会有额外开销；
* 客户端**不做自动重连**，也没有心跳，这些属于使用方的策略；
* 链路的 `token` 会直接成为文件名（`<token>.streacklib.sock`，默认放在 `/tmp`，不可写时回退系统临时目录），
  因此长度上限 64 字符，且**不允许**包含路径分隔符。
