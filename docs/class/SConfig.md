# `SConf`
## 前言
这是一个自动配置文件处理器，支持：

* 多种类型的支持
  * json/jsonc
  * yaml
  * INI
  * prop
  * Toml
  * SNBT
* 自动重载
* 自动管理配置项
* 缺省值配置

**注意**：目前还不支持以下这种根数组的JSON,强行初始化会等效为一个空的JSON文件。

```json
[
  {
    "data": "hello"
  },
  {
    "data": "world",
  }
]
```

## 初始化
要想使用：

```java
import java.io.File;
import com.github.streackmc.StreackLib.utils.SConf;
import com.github.streackmc.StreackLib.StreackLib;

SConf conf = StreackLib.initConf(File 文件对象, String "文件类型");
// 借助静态类 SConfig.TYPES 获取可用类型
```

这样就获取了一个`SConf`对象。

## 重载
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

## 获取配置文件对象
你可以用这个获取存储文件对象：

```java
File confFile = conf.getFile();
```

## 增删查改
作为一个配置管理器，最重要的是增删查改：

```java
// 增/改
conf.put(String "key", <T> "value");
// 删
conf.remove(String "key");
// 查
T v = (T) conf.get(String "key", <T> fallback);
```

### 严格类型
Java是一门严格类型的语言，使用刚才示例中的弃用方法是危险的。~~如果你是Java高手你可以无视风险、继续使用。~~
你可以在`put()`或者`get()`的括号前面插入类型，比如：

```java
conf.putString(String "key", "value");
conf.putString(String "section.key", "value"); // 分节也是可以的
String v = conf.getString(String "key", "fallback");
```

我们支持以下类型：

* String
* Int
* Float
* Boolen
* List