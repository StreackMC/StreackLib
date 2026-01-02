# `SFile`
这是一个已封装的模块，旨在将基础文件操作变得极其简单，就像在终端里一样。

## 开始使用
先引入此模块：

```java
import com.github.streackmc.StreackLib.utils.SFile
```

## 工具
基础文件操作种类较多，请参照IDE给出的JavaDoc查看具体用法。

简单来说：

* 每个操作都有自己的本名、Linux别名、MSDOS别名，具体请看下表；
* 除少数操作外，一般按以下规则返回：
  * 操作成功返回`true`，否则返回`false`
  * 若权限不足、源文件不存在等严重错误抛`IOException`

注意：JavaDoc由AI编写、人工粗审，可能存在风格不统一的问题。

### 别名表

| 操作 | 方法 | Linux别名 | MSDOS别名 |
|:-:|:-:|:-:|:-:|
|新建文件夹|`mkdir`|`-`|`md`|
|创建空文件|`touch`|`-`|`-`|
|获取MIME类型|`getMIME`|`fileMimeType`|`AddType`|
|删除|`remove`|`rm`|`eraser` / `delete` / `del`|
|复制（覆盖模式）|`copy`|`cp`|`cp`|
|复制（拼接模式）|`copyJoin`|`-`|`-`|
|移动（覆盖模式）|`move`|`mv`|`-`|
|移动（拼接模式）|`moveJoin`|`-`|`-`|
|重命名|`rename`|`-`|`ren`|
|列出文件|`ls` / `lsStr`|`-`|`dir` / `dirStr`|
|新建符号链接|`sn`|`-`|`-`|