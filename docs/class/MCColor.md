# `MCColor`
这是一个提供[格式化代码](https://zh.minecraft.wiki/w/%E6%A0%BC%E5%BC%8F%E5%8C%96%E4%BB%A3%E7%A0%81)相关的工具类

> 本文由AI生成

## 方法清单
|    返回值   | 方法签名                                                                                                                                      | 概述                                                   |
| :------: | :---------------------------------------------------------------------------------------------------------------------------------------- | :--------------------------------------------------- |
| `String` | `toHtml(String text)`                                                                                                                     | 将 MC 格式化文本（含 `§`）转换为 HTML，支持颜色、粗体、斜体、下划线、删除线、随机效果及换行 |
| `String` | `strip(String text)`                                                                                                                      | 清除文本中所有 `§` 开头的格式化代码                                 |
| `String` | `remove(String text)`                                                                                                                     | 清除文本中所有 `§` 和 `&` 开头的格式化代码                           |
| `String` | `parse(String text)`                                                                                                                      | 将文本中全部的 `&` 替换为 `§`（仅替换后接有效格式代码的）                    |
| `String` | `parse(String text, String prefix)`                                                                                                       | 将指定前缀替换为 `§`，自动转义正则特殊字符                              |
| `String` | `wrapWithHtmlSpan(String text, String color, boolean bold, boolean italic, boolean underline, boolean strikethrough, boolean obfuscated)` | 将文本包装为带样式的 HTML `<span>` 标签，自动转义 HTML 特殊字符           |


## 常量清单

|                 类型                 | 常量名      | 概述                                                  |
| :--------------------------------: | :------- | :-------------------------------------------------- |
| `java.util.Map<Character, String>` | `COLORS` | MC 格式化代码颜色对照表，包含 Java 版（`0-f`）与基岩版（`g-v`）共 22 种颜色映射 |
