# StreackLib
为StreackPlugin提供一些有用的前置API，最低需要JDK17，最低兼容Minecraft 1.14

# 文档
## 前言
欢迎使用本前置库！

## 快速开始
先引入此lib，以maven为例：

```xml
<dependency>
    <groupId>com.github.streackmc</groupId>
    <artifactId>StreackLib</artifactId>
    <!-- 记得修改版本号 -->
    <version>0.0.0</version>
    <scope>provided</scope>
</dependency>
```

再在`plugin.yml`里声明依赖：

```yml
depend: ["StreackLib"]

# 如果你的插件缺失本lib也可运行请改用
softdepend: ["StreackLib"]
```

最后引入StreackLib类即可：

```java
import com.github.streackmc.StreackLib.StreackLib;
// ...

// 如果你未打包StreackLib,你需要检验是否可用，不过Paper一般会自动处理
if (!Bukkit.getPluginManager().isPluginEnabled("StreackLib")) {
  // 未检测到时……
  getServer().getPluginManager().disablePlugin(this);
  return;
}
```

## `FileHandler`
目前模块正在开发，仅内部使用。

## `ConfHandler`
### 初始化
这是一个自动配置文件处理器，可以：

* JSON和YAML
* 自动重载
* 自动管理配置项
* 缺省值配置

要想使用：

```java
import java.io.File;
import com.github.streackmc.StreackLib.utils.ConfHandler;
import com.github.streackmc.StreackLib.StreackLib;

ConfHandler conf = StreackLib.initConf(File 文件对象, String "文件类型");
```

这样就获取了一个ConfHandler对象。

### 重载
在对象初始化后，其会被自动重载一次。
你也可以使用自动重载：

```java
// 启用自动重载
conf.startAutoReload;
// 获取自动重载状态
Boolen status = conf.isAutoReloading();
// 禁用自动重载
conf.stopAutoReload();
```

> 自动重载被重复启用时会自动忽略且不抛错误

或者手动重载：

```java
conf.reload();
```

### 获取配置文件对象
你可以用这个存储文件对象：

```java
File confFile = conf.getFile();
```

### 增删查改
作为一个配置管理器，最重要的是增删查改：

```java
// 增/改
conf.put(String "key", <T> "value");
// 删
conf.remove(String "key");
// 查
T v = (T) conf.get(String "key", <T> fallback);
```

## `HTTPServer`
### 获取HTTP Server
首先，你需要额外引入以处理HTTP Response：

```java
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.github.streackmc.StreackLib.StreackLib;
```

### 监听事件并处理
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

```

在上例中，你创建了对`/api/player/count`的监听，格式化了一个JSON并以`200`返回。
一个路径上同时最多只能存在一个处理器
**注意**：由于用户可以配置是否启用HTTPServer功能，你应始终校验获取到的`HTTPServer`对象是否为`null`——如是则未启用。

### 高级
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