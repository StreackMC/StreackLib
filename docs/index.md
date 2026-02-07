# 文档首页

## 面向开发者
### 快速开始
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

### 通用属性
每个公开的类都有以下若干子属性：

* `final static class EVENTS`：该类可以触发的事件的名称定义集；没有此子类表述不触发事件。
* `final long INSTANCE_ID`：该类**可以实例化**才存在，表示全局唯一的实例ID。

### 子文档目录
#### 通用

* [StreackLib](./class/StreackLib.md)
* [SEventCentral](./class/SEventCentral.md)
* [HTTPServer](./class/HTTPServer.md)
* [SConf](./class/SConf.md)
* [SFile](./class/SFile.md)

#### Bukkit/Spigot/Paper

* [SBukkit](./class/Bukkit/SBukkit.md)