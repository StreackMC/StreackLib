# `MCColor`
这是一个提供[格式化代码](https://zh.minecraft.wiki/w/%E6%A0%BC%E5%BC%8F%E5%8C%96%E4%BB%A3%E7%A0%81)相关的工具类

## 方法清单
| 常量 | 概述 |
|:-:|:----|
| `String` `MCColorsToHtml(String text)` | 将MC格式化文本转为HTML |
| `String` `stripMCColors(String text)` | 移除文本中全部格式化代码 |
| `String` `wrapSpan(String text, String color, boolean bold, boolean italic, boolean underline, boolean strikethrough, boolean obfuscated)` | 将文本进行格式化处理并输出为HTML |

## 常量清单

| 常量 | 概述 |
|:-:|:----|
| `java.util.Map<Character, String>` `MC_FORMAT_COLORS` | 格式化字符中颜色的映射关系 |