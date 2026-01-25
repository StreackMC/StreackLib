# `StreackLib`
## 前言
`StreackLib`类提供了其它类的入口点，和部分分类较模糊的方法。
除了部比如`HTTPServer`的类入口，大部分类入口和直接使用`new Class()`的效果一致。

[方法](#方法清单) | [常量](#常量清单)

## 方法清单
本文档只列出非类入口的方法清单，详情用法需参考对应JavaDoc。
类入口方法请参考对应类。

| 方法 | 概述 |
|:-:|:----|
| `boolean` `isDebugMode()` |  |
| `String` `MCColorsToHtml(String text)` | 将MC格式化文本转为HTML |
| `String` `stripMCColors(String text)` | 移除文本中全部格式化代码 |
| `String` `wrapSpan(String text, String color, boolean bold, boolean italic, boolean underline, boolean strikethrough, boolean obfuscated)` | 将文本进行格式化处理并输出为HTML |

## 常量清单

| 常量 | 概述 |
|:-:|:----|
| `java.util.Map<Character, String>` `MC_FORMAT_COLORS` | 格式化字符中颜色的映射关系 |