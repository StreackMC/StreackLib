# `HTTPServer`
## 前言
`HTTPServer`类提供了一个统一的内嵌简易HTTP服务器平台，其它插件可以自行监听此服务器上某特定地址（Path）上的所有请求（Post/Get/...）。
除此之外，你也可以自行创建一个独立的HTTP服务器，私有化使用。

## 获取HTTP Server
首先，你需要额外引入以处理HTTP Response：

```java
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.github.streackmc.StreackLib.StreackLib;
```

## 监听事件并处理
之后你可以注册一个HTTP监听事件：

```java
HTTPServer server = StreackLib.getHttpServer();
server.registerHandler("/api/player/count", session -> {
  int online = getServer().getOnlinePlayers().size();
  return newFixedLengthResponse(HTTPServer.Response.Status.OK, "application/json", "{\"online\":" + online + "}");
});
```

或是移除：

```java
server.registerHandler("/api/player/count");
```

在上例中，你创建了对`/api/player/count`的监听，格式化了一个JSON并以`200`返回。
一个路径上同时最多只能存在一个处理器，否则会抛出一个`Exception`。你可以在注册前先尝试注销处理器，或者使用`try...catch`捕获这个错误以提示用户。
**注意**：由于用户可以配置是否启用HTTPServer功能，你应始终校验获取到的`HTTPServer`对象是否为`null`——如是则未启用。

## 高级
如果你想，你也可以自己创建一个HTTPServer实例。
这么做可以避免依赖StreackLib的内建服务器状态，也不必考虑与其它插件的兼容性。

```java
// 创建一个实例，监听0.0.0.0:8080，插件继承自身
HTTPServer server = new HTTPServer("0.0.0.0", 8080, this);

// 启动该实例
server.startServer();

//获取实例状态
boolen status = server.isStarted();

// 结束此实例
server.stopServer();
```

> 请注意即使你新建了一个实例，其部分行为仍受StreackLib的配置文件控制。