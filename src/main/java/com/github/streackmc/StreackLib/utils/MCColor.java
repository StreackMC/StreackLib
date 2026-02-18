package com.github.streackmc.StreackLib.utils;

import java.util.regex.Pattern;

/**
 * 提供格式化代码相关功能支持
 * 
 * @see {@link https://zh.minecraft.wiki/w/%E6%A0%BC%E5%BC%8F%E5%8C%96%E4%BB%A3%E7%A0%81}
 * @author kdxiaoyi
 * @since 0.4.5
 */
public class MCColor {
  /**
   * MC格式化代码颜色对照关系表
   * <p>
   * 其中也含有基岩版的颜色
   * 
   * @since 0.4.5
   * @see {@link https://zh.minecraft.wiki/w/%E6%A0%BC%E5%BC%8F%E5%8C%96%E4%BB%A3%E7%A0%81#%E9%A2%9C%E8%89%B2%E4%BB%A3%E7%A0%81}
   */
  public static final java.util.Map<Character, String> COLORS = new java.util.HashMap<Character, String>();
  static {
    COLORS.put('0', "#000000");
    COLORS.put('1', "#0000AA");
    COLORS.put('2', "#00AA00");
    COLORS.put('3', "#00AAAA");
    COLORS.put('4', "#AA0000");
    COLORS.put('5', "#AA00AA");
    COLORS.put('6', "#FFAA00");
    COLORS.put('7', "#AAAAAA");
    COLORS.put('8', "#555555");
    COLORS.put('9', "#5555FF");
    COLORS.put('a', "#55FF55");
    COLORS.put('b', "#55FFFF");
    COLORS.put('c', "#FF5555");
    COLORS.put('d', "#FF55FF");
    COLORS.put('e', "#FFFF55");
    COLORS.put('f', "#FFFFFF");
    COLORS.put('g', "#DDD605");
    COLORS.put('h', "#E3D4D1");
    COLORS.put('i', "#CECACA");
    COLORS.put('j', "#443A3B");
    COLORS.put('m', "#971607");
    COLORS.put('n', "#B4684D");
    COLORS.put('p', "#DEB12D");
    COLORS.put('q', "#47A036");
    COLORS.put('s', "#2CBAA8");
    COLORS.put('t', "#21497B");
    COLORS.put('u', "#9A5CC6");
    COLORS.put('v', "#EB7114");
  }

  /**
   * 将MC格式化代码代码(§)转换为HTML
   * <p>
   * 支持：颜色代码（包含基岩版）、粗体(§l)、斜体(§o)、下划线(§n)、删除线(§m)、随机(§k)、重置(§r)
   * <p>
   * 0.4.6起也支持新版的 RGB 颜色格式：§#RRGGBB 和 §x§R§R§G§G§B§B
   * 
   * @apiNote 需要手动处理其它字符(&等)，该方法只处理§。
   * @param text 要处理的文本
   * @return 处理后的文本
   * @since 0.4.5
   */
  public static String toHtml(String text) {
    if (text == null || text.isEmpty())
      return "<span></span>";

    StringBuilder html = new StringBuilder("<span>");
    StringBuilder currentText = new StringBuilder();

    boolean bold = false, italic = false, underline = false, strikethrough = false, obfuscated = false;
    String color = null;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);

      if (c == '§' && i + 1 < text.length()) {
        char next = text.charAt(i + 1);
        boolean handled = false;

        // 尝试解析 RGB 短格式：§#RRGGBB
        if (next == '#') {
          if (i + 7 < text.length()) {
            String hexPart = text.substring(i + 2, i + 8);
            if (isHex(hexPart)) {
              // 先输出当前累积的文本
              if (currentText.length() > 0) {
                html.append(wrapWithHtmlSpan(currentText.toString(), color, bold, italic, underline, strikethrough,
                    obfuscated));
                currentText = new StringBuilder();
              }
              color = "#" + hexPart;
              i += 7; // 跳过 § # 和 6 个数字
              handled = true;
            }
          }
        }
        // 尝试解析 RGB 长格式：§x§R§R§G§G§B§B
        else if (next == 'x' || next == 'X') {
          if (i + 2 + 6 * 2 <= text.length()) { // 最少需要 14 个字符
            StringBuilder hex = new StringBuilder(6);
            boolean valid = true;
            int pos = i + 2; // 指向 §x 后的第一个字符
            for (int j = 0; j < 6; j++) {
              if (pos >= text.length() || text.charAt(pos) != '§') {
                valid = false;
                break;
              }
              pos++;
              if (pos >= text.length()) {
                valid = false;
                break;
              }
              char digit = text.charAt(pos);
              if (!isHexChar(digit)) {
                valid = false;
                break;
              }
              hex.append(Character.toLowerCase(digit));
              pos++;
            }
            if (valid) {
              // 先输出当前累积的文本
              if (currentText.length() > 0) {
                html.append(wrapWithHtmlSpan(currentText.toString(), color, bold, italic, underline, strikethrough,
                    obfuscated));
                currentText = new StringBuilder();
              }
              color = "#" + hex.toString();
              i = pos - 1; // pos 指向最后一个 hex 之后，循环结束后 i++ 会指向正确位置
              handled = true;
            }
          }
        }

        if (handled) {
          continue; // 已经移动了 i，跳过循环尾部的 i++
        }

        // 不是 RGB 格式，尝试旧版单个字符代码
        char code = Character.toLowerCase(next);
        if (COLORS.containsKey(code) || code == 'l' || code == 'o' || code == 'n' || code == 'm' || code == 'k'
            || code == 'r') {
          // 先输出当前累积的文本
          if (currentText.length() > 0) {
            html.append(
                wrapWithHtmlSpan(currentText.toString(), color, bold, italic, underline, strikethrough, obfuscated));
            currentText = new StringBuilder();
          }

          if (code == 'r') {
            bold = italic = underline = strikethrough = obfuscated = false;
            color = null;
          } else if (COLORS.containsKey(code)) {
            color = COLORS.get(code);
          } else if (code == 'l')
            bold = true;
          else if (code == 'o')
            italic = true;
          else if (code == 'n')
            underline = true;
          else if (code == 'm')
            strikethrough = true;
          else if (code == 'k')
            obfuscated = true;

          i++; // 跳过代码字符
        } else {
          // 无效代码，忽略这两个字符
          i++; // 跳过 next
          // 不输出任何内容，也不改变样式
        }
      } else if (c == '\n') {
        if (currentText.length() > 0) {
          html.append(
              wrapWithHtmlSpan(currentText.toString(), color, bold, italic, underline, strikethrough, obfuscated));
          currentText = new StringBuilder();
        }
        html.append("<br>");
      } else {
        currentText.append(c);
      }
    }

    if (currentText.length() > 0) {
      html.append(wrapWithHtmlSpan(currentText.toString(), color, bold, italic, underline, strikethrough, obfuscated));
    }

    html.append("</span>");
    return html.toString();
  }

  /**
   * 包装文本为带样式的span标签
   * 
   * @param text          要包装的文本
   * @param color         颜色代码（如#RRGGBB），null表示默认颜色
   * @param bold          是否加粗
   * @param italic        是否斜体
   * @param underline     是否下划线
   * @param strikethrough 是否删除线
   * @param obfuscated    是否乱码
   * @return 包装好的span标签
   * @since 0.4.5
   */
  public static String wrapWithHtmlSpan(String text, String color, boolean bold, boolean italic,
      boolean underline, boolean strikethrough, boolean obfuscated) {
    // HTML特殊字符转义（必须先转义&）
    text = text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");

    StringBuilder attrs = new StringBuilder();
    StringBuilder style = new StringBuilder();

    if (obfuscated) {
      attrs.append("class=\"MC-format-obfuscated\" ");
    }
    if (color != null) {
      style.append("color: ").append(color).append(";");
    }
    if (bold) {
      style.append("font-weight: bold;");
    }
    if (italic) {
      style.append("font-style: italic;");
    }

    if (strikethrough && underline) {
      style.append("text-decoration: line-through underline;");
    } else if (strikethrough) {
      style.append("text-decoration: line-through;");
    } else if (underline) {
      style.append("text-decoration: underline;");
    }

    if (style.length() > 0) {
      attrs.append("style=\"").append(style).append("\"");
    }

    return String.format("<span %s>%s</span>", attrs.toString().trim(), text);
  }

  /**
   * 清除所有Minecraft格式化代码，不含 &
   * <p>
   * 含 & 请使用 {@link #remove(String)}
   * <p>
   * 0.4.6起也支持新版的 RGB 颜色格式：§#RRGGBB 和 §x§R§R§G§G§B§B
   * 
   * @param text 要处理的文本
   * @return 处理后的文本
   * @since 0.4.5
   */
  public static String strip(String text) {
    if (text == null)
      return "";
    // 匹配所有 § 开头的代码：旧版单字符、RGB短格式、RGB长格式
    return text.replaceAll("§(?:#[0-9a-fA-F]{6}|x(?:§[0-9a-fA-F]){6}|[0-9a-vA-V])", "");
  }

  /**
   * 清除所有Minecraft格式化代码，含有 &
   * <p>
   * 不含 & 请使用 {@link #strip(String)}
   * <p>
   * 0.4.6起也支持新版的 RGB 颜色格式：§#RRGGBB 和 §x§R§R§G§G§B§B
   * 
   * @param text 要处理的文本
   * @return 处理后的文本
   * @since 0.4.5
   */
  public static String remove(String text) {
    if (text == null)
      return "";
    // 匹配所有 § 或 & 开头的代码：旧版单字符、RGB短格式、RGB长格式
    return text.replaceAll("(§|&)(?:#[0-9a-fA-F]{6}|x(?:[§&][0-9a-fA-F]){6}|[0-9a-vA-V])", "");
  }

  /**
   * 替换全部的 & 为 §（仅当 & 后跟有效的格式化代码时）
   * <p>
   * 0.4.6起也支持新版的 RGB 颜色格式：§#RRGGBB 和 §x§R§R§G§G§B§B
   * 
   * @param text 源文本
   * @return 处理后的文本
   */
  public static String parse(String text) {
    if (text == null)
      return "";
    Pattern pattern = Pattern.compile("&(#[0-9a-fA-F]{6}|x(&[0-9a-fA-F]){6}|[0-9a-vA-V])");
    java.util.regex.Matcher m = pattern.matcher(text);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String code = m.group(); // 如 "&x&F&F&0&0&F&F"
      String converted = code.replace('&', '§'); // 将所有 & 替换为 §
      m.appendReplacement(sb, converted);
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /**
   * 替换指定的前缀为 §（仅当前缀后跟有效的格式化代码时）
   * <p>
   * 0.4.6起也支持新版的 RGB 颜色格式：§#RRGGBB 和 §x§R§R§G§G§B§B
   * 
   * @param text   源文本
   * @param prefix 要替换的前缀，不能为 null，可以为空字符串（此时不进行替换）
   * @return 处理后的文本
   * @throws NullPointerException 如果 prefix 为 null
   */
  public static String parse(String text, String prefix) throws NullPointerException {
    if (prefix == null) {
      throw new NullPointerException("参数 prefix 为 null");
    }
    if (prefix.isEmpty()) {
      return text == null ? "" : text;
    }
    if (text == null) {
      return "";
    }
    // 构建匹配模式：前缀后跟旧版单字符或 RGB 格式
    String escapedPrefix = Pattern.quote(prefix);
    Pattern pattern = Pattern
        .compile(escapedPrefix + "(#[0-9a-fA-F]{6}|x(" + escapedPrefix + "[0-9a-fA-F]){6}|[0-9a-vA-V])");
    java.util.regex.Matcher m = pattern.matcher(text);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String code = m.group(); // 整个匹配到的代码
      String converted = code.replace(prefix, "§"); // 将所有前缀替换为 §
      m.appendReplacement(sb, converted);
    }
    m.appendTail(sb);
    return sb.toString();
  }

  // 辅助方法：判断字符是否为有效的十六进制字符
  private static boolean isHexChar(char c) {
    return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
  }

  // 辅助方法：判断字符串是否为有效的6位十六进制数
  private static boolean isHex(String s) {
    if (s.length() != 6)
      return false;
    for (int i = 0; i < 6; i++) {
      if (!isHexChar(s.charAt(i)))
        return false;
    }
    return true;
  }
}