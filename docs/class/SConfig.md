# `SConf`
## 前言
这是一个自动配置文件处理器，支持：

* 多种文件类型的支持：JSON / JSON with Comment / YAML / Properties / TOML / INI / NBT / SNBT
* 自动重载
* 自动管理配置项
* 缺省值配置

有关于 JSON 中的 Array As Root 和 NBT 中根标签的名称 等特性，请参考[#特殊格式支持](#特殊格式支持)。

## 初始化
要想使用：

```java
import java.io.File;
import com.github.streackmc.StreackLib.utils.SConf;
import com.github.streackmc.StreackLib.StreackLib;

SConfig conf = new SConfig(File 文件对象, String "文件类型");
// 借助静态类 SConfig.TYPES 获取可用类型

// 你也可以传入原始数据，用作转换文件格式等地方
SConfig conf2 = new SConfig(Map<String, Object> conf.getRawData(), String "文件类型", String "临时文件修饰");
```

这样就获取了一个`SConf`对象。

## 加载与重载
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

### 解构
每次重载 SConfig 都会自动全量解构输入数据，并转储树状结构为树状 Map 结构，例如：

```json
{
  "node1": {
    "key1": "Castorice Forever",
    "key2": 26710
  },
  "key3": true
}
```

↓ 解构为

* `Map<String, Object>` #root
  * `Map<String, Object>` node1 = `[...]`
    * `String` key1 = `Castorice Forever`
    * `Int` key2 = `26710`
  * `Boolean` key3 = `true`

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
Java是一门严格类型的语言，使用刚才示例中的弃用方法是危险的，它会直接返回一个 Object 。~~如果你是Java高手你可以无视风险、继续使用。~~
你可以在`put()`或者`get()`的括号前面插入类型，比如：

```java
conf.putString(String "key", "value");
conf.putString(String "section.key", "value"); // 分节也是可以的
String v = conf.getString(String "key", "fallback");
```

目前支持以下类型：

* string
* int
* float
* long
* short
* double
* boolean/byte
  * `0 == false`, `1 == true`
* List

### 链式调用
`putXXX()`和`remove()`系列方法均支持链式调用：

```java
conf.putString(String "key", "value")
    .putString(String "section.key", "value");
    .remove(String "deprecated");
```

## 写入模式
SConfig支持四种写入模式，默认为[自动保存](#自动保存)模式。

* 自动保存 `SConfig.write`
* 手动保存
* 写保护
* 只读

## 特殊格式支持
### JSON 的 Array As Root
JSON中存在一类特殊格式：

```json
[
  "conf1": {"foo": "bar"},
  "conf2": {"foo": "bar"},
  "conf3": {"foo": "bar"}
]
```

这种格式的根是一个数组而非正常树结构。0.4.6版本前，SConfig会视作空文件；该版本及之后，SConfig 会将其等效视作：

```json
{
  "_root_array": [
    "conf1": {"foo": "bar"},
    "conf2": {"foo": "bar"},
    "conf3": {"foo": "bar"}
  ]
}
```

同时，如果一个被 SConfig 解构后的数据也符合上述格式（根 Map 仅包含一个 `key="_root_array", value=[...]`），其被保存为 JSON 格式时会自动以根数组的形式保存。

**请注意：**

1. 如果外部文件不是根数组但也符合该条件仍然会按根数组模式工作，因此写入时也会破坏文件结构；
2. 如果更糟，外部文件符合该结构但`_root_array`的值不是数组可能会引发未预料的其它错误。

#### Object As Root
诸如NBT格式可能存在另外一种相似的情况，即任何值作为根。
这种写法不标准、不推荐，即使 SConfig 会将其类似地放入 `_root_value` 中。

### RootName
诸如NBT等格式的根数据结构也会有一个 name ，若用JSON表达就是：

```json
{
  "data": "我是数据，这也是个正常的结构"
}
```

↓ 支持RootName时

```json
"所有数据外面还有一层，而我就是 RootName": "{
  "data": "我是数据，外面被包了一层"
}"
```

考虑到 RootName 可能为空，尽管 SConfig 支持解析空键名，仍然分离了其处理：

```java
conf.setRootName("name")// 文件格式不支持此特性时静默处理
    .putString("key", "value");// 支持链式调用

String rN = conf.getRootName();
// 只有不支持才会返回 null ,否则一律为字符串。
```