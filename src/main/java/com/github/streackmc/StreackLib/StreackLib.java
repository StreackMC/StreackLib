package com.github.streackmc.StreackLib;

import java.io.File;

import javax.annotation.Nullable;

import org.apache.logging.log4j.util.InternalApi;

import com.github.streackmc.StreackLib.utils.HTTPServer;
import com.github.streackmc.StreackLib.utils.SConfig;

/**
 * 杂项工具类，也作为其它工具类的跳板。
 * 作跳板用时和new Sxxx()并没有什么区别（（
 * 
 * @author kdxiaoyi
 * @since 0.4.3
 */
public final class StreackLib {
  private StreackLib() {
  }

  @InternalApi
  public static SConfig conf;
  @InternalApi
  public static SConfig defaultConf;
  @InternalApi
  public static SConfig buildConf;
  @InternalApi
  public static File dataPath;

  // ===================== HTTP Server =====================

  /**
   * 获取内联HTTPServer对象
   * 该对象由StreackLib依据配置文件启动，可能受用户影响无效
   * @return 获取到的对象；若当前未启动服务器则为null
   */
  @Nullable
  public static HTTPServer getHttpServer() {
    return libinit.httpServer;
  }

  /**
   * 新建一个HTTPServer对象
   * @param hostname 监听地址
   * @param port 监听端口
   * @return 获取到的对象
   */
  public static HTTPServer newHttpServer(String hostname, int port) {
    return new HTTPServer(hostname, port, libinit.pluginSelf);
  }

  // ===================== Conf Handle =====================

  /**
   * 获取一个指向一个文件的配置文件对象。使用此对象方法可以更快捷地操作配置文件。建议使用前先使用Bukkit自带的释放配置文件以放出默认配置文件。
   * @param file 配置文件的对象
   * @param type 配置文件的类型
   * @return 一个配置文件对象
   */
  public static SConfig initConf(File file, String type) {
    return new SConfig(file, type);
  }

  // ===================== Other Utils =====================

  /**
   * 获取当前StreackLib的调试状态
   * 
   * @return
   * @since 0.4.3
   */
  public static boolean isDebugMode() {
    return conf.getBoolean("debug", false);
  }

  private static final java.util.Map<Character, String> COLORS = new java.util.HashMap<>();
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
   * 支持：颜色代码、粗体(§l)、斜体(§o)、下划线(§n)、删除线(§m)、随机(§k)、重置(§r)
   * 根据MCWIKI，添加了对基岩版的格式支持
   * 
   * @param text 要处理的文本
   * @return 处理后的文本
   * @since 0.4.3
   */
  public static String MCColorsToHtml(String text) {
    if (text == null || text.isEmpty())
      return "<span></span>";

    StringBuilder html = new StringBuilder("<span>");
    StringBuilder currentText = new StringBuilder();

    boolean bold = false, italic = false, underline = false, strikethrough = false, obfuscated = false;
    String color = null;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);

      if (c == '§' && i + 1 < text.length()) {
        char code = Character.toLowerCase(text.charAt(i + 1));

        if (currentText.length() > 0) {
          html.append(wrapSpan(currentText.toString(), color, bold, italic, underline, strikethrough, obfuscated));
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

        i++;
      } else if (c == '\n') {
        if (currentText.length() > 0) {
          html.append(wrapSpan(currentText.toString(), color, bold, italic, underline, strikethrough, obfuscated));
          currentText = new StringBuilder();
        }
        html.append("<br>");
      } else {
        currentText.append(c);
      }
    }

    if (currentText.length() > 0) {
      html.append(wrapSpan(currentText.toString(), color, bold, italic, underline, strikethrough, obfuscated));
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
   * @since 0.4.3
   */
  public static String wrapSpan(String text, String color, boolean bold, boolean italic,
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
   * 清除所有Minecraft格式化代码
   * 
   * @param text 要处理的文本
   * @return 处理后的文本
   * @since 0.4.3
   */
  public static String stripMCColors(String text) {
    return text == null ? "" : text.replaceAll("§[0-9a-fA-Fk-oK-OrR]", "");
  }

}