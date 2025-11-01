# StreackLib
为StreackPlugin提供一些有用的前置API，最低需要JDK17，最低兼容Minecraft 1.14

# 文档
## 前言
所有公开类全部放在了`com.github.streackmc.StreackLib.utils`下面，可按需引用。

## 快速开始
先引入此lib，以maven为例：

```xml
<repository>
    <id>streackmc</id>
    https://repo.streackmc.io/releases
</repository>

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
import com.github.StreackMC.StreackLib.main;
// ...

StreackLib StreackLib = (StreackLib) getServer().getPluginManager().getPlugin("StreackLib");
if (lib == null || !lib.isEnabled()) {
  /* 如果没有检测到StreackLib则执行 */
  getLogger().severe("StreackLib 未安装或未启用，本插件即将关闭！");
  getServer().getPluginManager().disablePlugin(this);
  return;
}
```

## `HTTPServer`
首先，你需要额外引入以处理HTTP Response：

```java
import static fi.iki.elonen.NanoHTTPD.newFixedLengthResponse;
```

之后你可以注册一个HTTP监听事件：

```java
HTTPServer server = StreackLib.getHttpServer();
server.registerHandler("/api/player/count", session -> {
  int online = getServer().getOnlinePlayers().size();
  return newFixedLengthResponse(HTTPServer.Response.Status.OK, "application/json", "{\"online\":" + online + "}");
});
```

在上例中，你创建了对`/api/player/count`的监听，格式化了一个JSON并以`200`返回。
**注意**：由于用户可以配置是否启用HTTPServer功能，你应始终校验获取到的`HTTPServer`对象是否为`null`——如是则未启用。