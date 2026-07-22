# 文档首页

## 面向开发者

> 本库还在快速迭代，每个大版本更新（`x.y.z`）都可能导致 API 变更。

### 快速开始
作为插件、模组的前置使用时，先引入此lib，以maven为例：

```xml
<dependency>
    <groupId>com.github.streackmc</groupId>
    <artifactId>StreackLib</artifactId>
    <!-- 记得修改版本号 -->
    <version>0.0.0</version>
    <scope>provided</scope>
    <!-- 如果你选择使用内嵌方式本库也可工作。参考后文 -->
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

#### 内嵌工作模式
如果你将本库内嵌到你的插件/模组内，甚至是非 Minceraft 插件/模组的 Java 项目内，本库也可工作，**但**：

* 不会进行任何初始化，例如 HTTPServer 不会自动启动
* 所有配置文件定义的内容都视作默认值

另外，如果你想修改这些默认值，有两个方向：

* 通过 `StreackLib.ENV.` 拿到配置文件对象并手动通过 SConfig 修改
* 直接使用其他 SConfig 覆盖上述引用

这是因为库被调用前，如果没有通过 `self.initializer.` 方法初始化，那么 `self.manager` 默认持有 `self.backend.StreackLibDefaultBackend` ，它会将 `StreackLib.ENV` 全部设为空 SConfig 。

### 通用属性
每个公开的类都有以下若干子属性：

* `final static class EVENTS`：该类可以触发的事件的名称定义集；没有此子类表述不触发事件；
* `final long INSTANCE_ID`：该类**可以实例化**才存在，表示全局唯一的实例ID；
* `final long TIME_STAMP`：该类**可以实例化**才存在，表示实例的初始化时间。

#### 继承关系

此外，从 0.6.0 版本起，可实例化的类的继承关系作出了调整：
* 除部分需要继承其它类和 Exception ，其他可以实例化的类都继承了 `StreackLibNewable` 类；
* 本库新增的 Exception 都继承了 `StreackLibNewableException` 或 `StreackLibNewableRuntimeException` 类；

### 工具类文档

#### 可实例化工具

* [SEvent](./class/SEventCentral.md#事件数据对象-sevent)
* [SMail](./types/SMail.md)
* [SConfig](./types/SConfig.md)
* [SDatabase](./types/SDatabase.md)
* [IgnoredException](./types/IgnoredException.md)
* [HTTPServer](./types/HTTPServer.md)

#### 静态工具

* [MCColor](./class/MCColor.md)
* [SEventCentral](./class/SEventCentral.md)
* [SFile](./class/SFile.md)

#### 其它

* [StreackLib](./class/StreackLib.md)

### 异常类文档