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

### 目录
#### 公开类

* [HTTPServer](./class/HTTPServer.md)
* [SConf](./class/SConf.md)
* [SFile](./class/SFile.md)