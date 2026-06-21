package com.github.streackmc.StreackLib.utils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.errors.ConfigNotFoundException;
import com.github.streackmc.StreackLib.errors.InvaildConfigException;
import com.github.streackmc.StreackLib.self.logger;

import jakarta.mail.Authenticator;
import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * <h3>SMail</h3>
 * 便携的邮件发送 API 。用户只需一次配置就可享受处处调用、多方式调用的发件功能:
 * <p>
 * * 支持设置 Profile 控制多种发件方式，而调用方只需要一个输入 Profile 名称就可一键替换。
 * <p>
 * * 支持自定义域名 DKIM 签名发送。
 * <p>
 * * 封装已有库（Jakarta Mail），调用方只需要专注于邮件本身即可。
 * 
 * <h4>使用示例</h4>
 * <pre>{@code
 * // ========== 简单场景：使用 SMTP 发送一封 HTML 邮件 ==========
 * SMail.builder("profile_smtp")
 *     .to("user@example.com")
 *     .subject("Hello World")
 *     .body("<h1>Hello</h1><p>这是一封测试邮件</p>", true)
 *     .build()
 *     .send();
 *
 * // ========== 复杂场景：带附件和内嵌图片 ==========
 * SMail.builder("profile_smtp")
 *     .to("alice@example.com", "bob@example.com")
 *     .cc("cc@example.com")
 *     .bcc("bcc@example.com")
 *     .subject("报告与图片")
 *     .body("<h1>报告</h1><p>详见附件</p><img src='cid:chart.png'>", true)
 *     .alternative("报告内容，详见附件")
 *     .attachments(new File("/path/to/report.pdf"))
 *     .inline_images(new File("/path/to/chart.png"))
 *     .priority(1)
 *     .build()
 *     .send();
 *
 * // ========== 使用自签名 DKIM 发送 ==========
 * SMail.builder("profile_selfsign")
 *     .to("user@example.com")
 *     .subject("DKIM 签名邮件")
 *     .body("这封邮件经过了 DKIM 签名", false)
 *     .build()
 *     .send();
 *
 * // ========== 复用已存在的 SConfig ==========
 * SConfig reusedConf = new SConfig("", "json", null);
 * reusedConf.putString("subject", "复用配置");
 * SMail.builder("profile_smtp", reusedConf)
 *     .to("user@example.com")
 *     .body("正文", false)
 *     .build()
 *     .send();
 * }</pre>
 * 
 * @since 0.5.2
 * @author kdxiaoyi
 */
public class SMail {
  /** 邮件参数 */
  private final SConfig emailConf;
  /** 发件档案参数（来自 config.yml 的 emails.{profile} 节） */
  private final SConfig profileConf;
  /** 发件后端 */
  private final EmailService profileSrv;

  public static class AvailableProfileMode {
    public static final String SMTP = "smtp";
    public static final String SELFSIGN = "selfsign";
  }

  // ======================== 入口：静态工厂

  /**
   * 创建一个新的邮件 Builder。
   * 
   * @param profile 配置档案名（对应 config.yml 中 emails.{profile} 节）
   * @return Builder 实例
   */
  public static Builder builder(String profile) {
    return new Builder(profile);
  }

  /**
   * 创建一个新的邮件 Builder，复用已有 SConfig。
   * 
   * @param profile 配置档案名
   * @param conf    已有 SConfig
   * @return Builder 实例
   */
  public static Builder builder(String profile, SConfig conf) {
    return new Builder(profile, conf);
  }

  // ======================== 构造

  private SMail(String profile, Builder builder) throws Exception {
    Objects.requireNonNull(profile, "意外的 Null 被用于新建邮件");
    // 读取 config.yml 中 emails.{profile} 节作为发件档案配置
    Map<String, Object> profileRaw = StreackLib.ENV.conf.getSection("emails." + profile);
    if (profileRaw == null || profileRaw.isEmpty()) {
      throw new ConfigNotFoundException("找不到 Email Profile: " + profile);
    }
    profileConf = new SConfig(profileRaw, SConfig.TYPES.YAML, ".yml");

    String mode = profileConf.getString("mode", "").toLowerCase();
    switch (mode) {
      case AvailableProfileMode.SMTP:
        profileSrv = new EmailServiceSMTP();
        break;
      case AvailableProfileMode.SELFSIGN:
        profileSrv = new EmailServiceSelfsign();
        break;
      case "":
        throw new ConfigNotFoundException("Email Profile \"" + profile + "\" 未设置 mode");
      default:
        throw new InvaildConfigException("无效的 Email Profile 模式: " + mode + "（来自 profile: " + profile + "）");
    }
    emailConf = builder.emailConf;
  }

  /**
   * 将邮件发送出去
   * 
   * @throws InvaildConfigException Profile 无效
   * @throws RuntimeException       邮件发送时发生错误
   */
  public void send() throws Exception {
    profileSrv.send();
  }

  /**
   * 异步将邮件发送出去
   * 
   * @return 异步操作，你可以使用 {@link CompletableFuture#exceptionally(java.util.function.Function)} 捕获发送邮件时的错误
   * @throws InvaildConfigException Profile 无效
   * @throws RuntimeException       邮件发送时发生错误
   */
  public CompletableFuture<Void> sendAsync() {
    return CompletableFuture.runAsync(() -> {
      profileSrv.send();
    });
  }

  /** 获取邮件参数 */
  public SConfig get() {
    return emailConf;
  }

  // ======================== 邮件的参数处理

  public static class Builder {
    /** 原始档案名 */
    private final String profileRaw;
    /** 邮件参数 */
    public final SConfig emailConf;

    /**
     * 新建一封邮件（Builder模式）
     * 
     * @param profile 要使用的档案名
     */
    public Builder(String profile) {
      profileRaw = profile;
      emailConf = new SConfig("", SConfig.TYPES.JSON, null);
    }

    /**
     * 新建一封邮件，并且复用已有配置（Builder模式）
     * 
     * @param profile 要使用的档案名
     * @param conf    已有配置
     */
    public Builder(String profile, SConfig conf) {
      profileRaw = profile;
      emailConf = conf;
    }

    /**
     * 构建一封邮件
     * 
     * @apiNote 必填 {@link #to(String...)} 、{@link #subject(String)} 、{@link #body(String, boolean)}
     *          否则发送时可能抛出异常或产生无意义邮件。
     * @return 构建好的 SMail 实例
     * @throws Exception 配置读取或解析错误
     */
    public SMail build() throws Exception {
      return new SMail(profileRaw, this);
    }

    // ---------- 收件人相关

    /** 收件人 */
    public Builder to(String... who) {
      this.emailConf.putListOfString("to", Arrays.asList(who));
      return this;
    }

    /** 收件人 */
    public Builder to(List<String> who) {
      this.emailConf.putListOfString("to", who);
      return this;
    }

    /** 密送 */
    public Builder bcc(String... who) {
      this.emailConf.putListOfString("bcc", Arrays.asList(who));
      return this;
    }

    /** 密送 */
    public Builder bcc(List<String> who) {
      this.emailConf.putListOfString("bcc", who);
      return this;
    }

    /** 抄送 */
    public Builder cc(String... who) {
      this.emailConf.putListOfString("cc", Arrays.asList(who));
      return this;
    }

    /** 抄送 */
    public Builder cc(List<String> who) {
      this.emailConf.putListOfString("cc", who);
      return this;
    }

    // ---------- 内容相关

    /** 主题（标题） */
    public Builder subject(String what) {
      this.emailConf.putString("subject", what);
      return this;
    }

    /** 邮件正文。需要指定是否是 HTML 文本 */
    public Builder body(String what, boolean isHTML) {
      this.emailConf.putString("body.data", what);
      this.emailConf.putBoolean("body.html", isHTML);
      return this;
    }

    /** 正文是 HTML 但是当收件客户端不支持显示时会显示此文本 */
    public Builder alternative(String fallback) {
      this.emailConf.putString("body.fallback", fallback);
      return this;
    }

    /** 邮件字符集，默认 UTF-8 */
    public Builder charset(String what) {
      this.emailConf.putString("body.charset", what);
      return this;
    }

    /** 正文内嵌图片。文件会被解析为内容ID引用，在 HTML 中使用 {@code <img src='cid:文件名'>} 引用 */
    public Builder inline_images(File... what) {
      List<String> paths = new ArrayList<>();
      for (File f : what)
        paths.add(f.getAbsolutePath());
      this.emailConf.putListOfString("inline_images", paths);
      return this;
    }

    /** 正文内嵌图片 */
    public Builder inline_images(List<File> what) {
      List<String> paths = new ArrayList<>();
      for (File f : what)
        paths.add(f.getAbsolutePath());
      this.emailConf.putListOfString("inline_images", paths);
      return this;
    }

    /** 附件 */
    public Builder attachments(File... what) {
      List<String> paths = new ArrayList<>();
      for (File f : what)
        paths.add(f.getAbsolutePath());
      this.emailConf.putListOfString("attachments", paths);
      return this;
    }

    /** 附件 */
    public Builder attachments(List<File> what) {
      List<String> paths = new ArrayList<>();
      for (File f : what)
        paths.add(f.getAbsolutePath());
      this.emailConf.putListOfString("attachments", paths);
      return this;
    }

    // ---------- 其他

    /**
     * 定时发送。只能用于 SMTP 且由服务商处理，可能不受支持。
     * 当任意原因导致设置失败则静默处理。
     */
    public Builder schedule(long timestamp) {
      this.emailConf.putLong("scheduled_at", timestamp);
      return this;
    }

    /**
     * 优先级。正数为高，负数为低，0 为正常。数字的绝对值大小没有作用。默认 0
     */
    public Builder priority(int priority) {
      if (priority > 0) {
        this.emailConf.putString("priority", "high");
      } else if (priority < 0) {
        this.emailConf.putString("priority", "low");
      } else {
        this.emailConf.putString("priority", "normal");
      }
      return this;
    }

  }

  // ======================== 邮件发送具体实现

  /** 邮件发送服务接口 */
  private interface EmailService {
    void send();
  }

  // ---------- SMTP 模式 ----------

  private class EmailServiceSMTP implements EmailService {
    @Override
    public void send() {
      try {
        String server = profileConf.getString("server", "");
        int port = profileConf.getInt("port", 587);
        String username = profileConf.getString("username", "");
        String password = profileConf.getString("password", "");
        String oauthToken = profileConf.getString("oauth-token", "");

        // 参数校验
        if (server.isEmpty()) {
          throw new InvaildConfigException("SMTP 模式缺少必要配置: server");
        }

        // 构建 Session
        Properties props = new Properties();
        props.put("mail.smtp.host", server);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", String.valueOf(profileConf.getBoolean("tls", true)));
        props.put("mail.smtp.connectiontimeout", profileConf.getString("timeout", "10000"));
        props.put("mail.smtp.timeout", profileConf.getString("timeout", "10000"));
        props.put("mail.smtp.writetimeout", profileConf.getString("timeout", "10000"));

        // 如果配置了 OAuth Token，使用其代替密码
        final String authPassword;
        final String authUser;
        if (!oauthToken.isEmpty()) {
          authPassword = oauthToken;
          authUser = username;
        } else {
          authPassword = password;
          authUser = username;
        }

        Session session = Session.getInstance(props, new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(authUser, authPassword);
          }
        });

        // 构建并发送
        MimeMessage msg = buildMessage(session);
        Transport.send(msg);
        logger.info("SMail | 邮件已通过 SMTP 发送至 %s", String.join(", ", emailConf.getListOfString("to")));

      } catch (MessagingException e) {
        logger.err("SMail | SMTP 发送失败: %s", e.getLocalizedMessage());
        throw new RuntimeException("SMTP 邮件发送失败", e);
      } catch (Exception e) {
        logger.err("SMail | SMTP 发送异常: %s", e.getLocalizedMessage());
        throw new RuntimeException(e);
      }
    }
  }

  // ---------- 自签名 DKIM 模式 ----------

  private class EmailServiceSelfsign implements EmailService {
    @Override
    public void send() {
      try {
        String domain = profileConf.getString("domain", "");
        String selector = profileConf.getString("selector", "default");
        String keyPath = profileConf.getString("private_key", "");

        if (domain.isEmpty() || keyPath.isEmpty()) {
          throw new InvaildConfigException("SELFSIGN 模式缺少必要配置: domain / private_key");
        }

        // 读取私钥
        PrivateKey privateKey = loadPrivateKey(keyPath);

        // 构建 Session（无中继，直连 MX）
        Properties props = new Properties();
        props.put("mail.smtp.connectiontimeout", "15000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        Session session = Session.getInstance(props);

        // 构建 MimeMessage（尚未签名）
        MimeMessage msg = buildMessage(session);
        msg.saveChanges();

        // DKIM 签名
        dkimSign(msg, domain, selector, privateKey);

        // 获取收件人域名列表，解析 MX 并直投
        List<String> recipients = new ArrayList<>();
        Collections.addAll(recipients, emailConf.getListOfString("to").toArray(new String[0]));
        Collections.addAll(recipients, emailConf.getListOfString("cc").toArray(new String[0]));
        // BCC 不暴露给收件人，但仍需投递
        Collections.addAll(recipients, emailConf.getListOfString("bcc").toArray(new String[0]));

        if (recipients.isEmpty()) {
          throw new InvaildConfigException("没有收件人，无法发送");
        }

        // 按域名分组投递
        Map<String, List<String>> domainGroups = groupByDomain(recipients);
        for (Map.Entry<String, List<String>> entry : domainGroups.entrySet()) {
          String mxHost = resolveMX(entry.getKey());
          if (mxHost == null) {
            logger.warn("SMail | 无法解析域名 %s 的 MX 记录，跳过", entry.getKey());
            continue;
          }
          logger.info("SMail | 正在直投 %s -> MX: %s", entry.getKey(), mxHost);
          deliverDirect(msg, mxHost, 25, entry.getValue());
        }

        logger.info("SMail | 邮件已通过 DKIM 自签名发送至 %s",
            String.join(", ", emailConf.getListOfString("to")));

      } catch (Exception e) {
        logger.err("SMail | SELFSIGN 发送失败: %s", e.getLocalizedMessage());
        throw new RuntimeException("SELFSIGN 邮件发送失败", e);
      }
    }
  }

  // ======================== 共享：构建 MimeMessage

  /**
   * 根据 emailConf 和 profileConf 构建 MimeMessage
   */
  private MimeMessage buildMessage(Session session) throws Exception {
    MimeMessage msg = new MimeMessage(session);

    // --- 发件人 ---
    String from = profileConf.getString("from", "");
    String fromWho = profileConf.getString("from-who", "");
    String replyTo = profileConf.getString("reply-to", "");

    if (from.isEmpty()) {
      throw new InvaildConfigException("发件人配置缺失: from");
    }

    if (!fromWho.isEmpty()) {
      msg.setFrom(new InternetAddress(from, fromWho));
    } else {
      msg.setFrom(new InternetAddress(from));
    }

    if (!replyTo.isEmpty()) {
      msg.setReplyTo(InternetAddress.parse(replyTo));
    }

    // --- 收件人 ---
    addRecipients(msg, RecipientType.TO, emailConf.getListOfString("to"));
    addRecipients(msg, RecipientType.CC, emailConf.getListOfString("cc"));
    addRecipients(msg, RecipientType.BCC, emailConf.getListOfString("bcc"));

    // --- 主题 ---
    String subject = emailConf.getString("subject", "");
    String charset = emailConf.getString("body.charset", "UTF-8");
    if (subject.isEmpty()) {
      throw new InvaildConfigException("邮件主题缺失: subject");
    }
    msg.setSubject(subject, charset);

    // --- 正文与附件 ---
    String bodyData = emailConf.getString("body.data", "");
    boolean isHTML = emailConf.getBoolean("body.html");
    String fallback = emailConf.getString("body.fallback", "");
    List<String> inlineImagePaths = emailConf.getListOfString("inline_images");
    List<String> attachmentPaths = emailConf.getListOfString("attachments");

    boolean hasInlineImages = inlineImagePaths != null && !inlineImagePaths.isEmpty();
    boolean hasAttachments = attachmentPaths != null && !attachmentPaths.isEmpty();
    boolean hasFallback = !fallback.isEmpty();

    if (hasAttachments) {
      // multipart/mixed
      Multipart mixed = new MimeMultipart("mixed");

      // 正文部分
      MimeBodyPart bodyPart = new MimeBodyPart();
      bodyPart.setContent(buildBodyContent(bodyData, isHTML, fallback, hasFallback, hasInlineImages, inlineImagePaths, charset));
      mixed.addBodyPart(bodyPart);

      // 附件
      if (attachmentPaths != null) {
        for (String path : attachmentPaths) {
          MimeBodyPart attPart = new MimeBodyPart();
          attPart.attachFile(new File(path));
          mixed.addBodyPart(attPart);
        }
      }

      msg.setContent(mixed);
    } else {
      // 无附件，直接设置正文
      msg.setContent(buildBodyContent(bodyData, isHTML, fallback, hasFallback, hasInlineImages, inlineImagePaths, charset));
    }

    // --- 优先级 ---
    String priority = emailConf.getString("priority", "normal");
    switch (priority) {
      case "high":
        msg.setHeader("Priority", "urgent");
        msg.setHeader("X-Priority", "1");
        msg.setHeader("X-MSMail-Priority", "High");
        break;
      case "low":
        msg.setHeader("Priority", "non-urgent");
        msg.setHeader("X-Priority", "5");
        msg.setHeader("X-MSMail-Priority", "Low");
        break;
      default:
        msg.setHeader("X-Priority", "3");
        break;
    }

    // --- 定时发送 ---
    long scheduledAt = emailConf.getLong("scheduled_at", 0L);
    if (scheduledAt > 0L) {
      msg.setHeader("Date", String.valueOf(scheduledAt));
      // 部分服务商支持 Delay-Until / X-MC-Deliver-By
      msg.setHeader("X-MC-Deliver-By", String.valueOf(scheduledAt));
    }

    msg.saveChanges();
    return msg;
  }

  /**
   * 构建正文部分的 Multipart 或单体内容
   */
  private Multipart buildBodyContent(String bodyData, boolean isHTML, String fallback,
      boolean hasFallback, boolean hasInlineImages, List<String> inlineImagePaths,
      String charset) throws Exception {

    if (hasInlineImages) {
      // multipart/related: HTML + 内嵌图片
      Multipart related = new MimeMultipart("related");

      if (hasFallback) {
        // multipart/alternative: fallback + HTML
        Multipart alternative = new MimeMultipart("alternative");
        MimeBodyPart fallbackPart = new MimeBodyPart();
        fallbackPart.setText(fallback, charset);
        alternative.addBodyPart(fallbackPart);

        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(bodyData, "text/html; charset=" + charset);
        alternative.addBodyPart(htmlPart);

        MimeBodyPart wrapper = new MimeBodyPart();
        wrapper.setContent(alternative);
        related.addBodyPart(wrapper);
      } else {
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(bodyData, "text/html; charset=" + charset);
        related.addBodyPart(htmlPart);
      }

      // 内嵌图片
      for (String path : inlineImagePaths) {
        File imgFile = new File(path);
        if (!imgFile.exists() || !imgFile.isFile()) {
          logger.warn("SMail | 内嵌图片不存在: %s", path);
          continue;
        }
        MimeBodyPart imgPart = new MimeBodyPart();
        imgPart.attachFile(imgFile);
        imgPart.setDisposition(MimeBodyPart.INLINE);
        imgPart.setContentID("<" + imgFile.getName() + ">");
        // 根据扩展名设置 MIME 类型
        String fileName = imgFile.getName().toLowerCase();
        if (fileName.endsWith(".png")) {
          imgPart.setFileName(imgFile.getName());
        }
        related.addBodyPart(imgPart);
      }

      return related;

    } else if (hasFallback && isHTML) {
      // multipart/alternative: plaintext + HTML
      Multipart alternative = new MimeMultipart("alternative");
      MimeBodyPart fallbackPart = new MimeBodyPart();
      fallbackPart.setText(fallback, charset);
      alternative.addBodyPart(fallbackPart);

      MimeBodyPart htmlPart = new MimeBodyPart();
      htmlPart.setContent(bodyData, "text/html; charset=" + charset);
      alternative.addBodyPart(htmlPart);

      MimeBodyPart wrapper = new MimeBodyPart();
      wrapper.setContent(alternative);
      Multipart mixed = new MimeMultipart("mixed");
      mixed.addBodyPart(wrapper);
      return mixed;

    } else {
      // 简单内容：直接设置
      Multipart simple = new MimeMultipart("mixed");
      MimeBodyPart part = new MimeBodyPart();
      if (isHTML) {
        part.setContent(bodyData, "text/html; charset=" + charset);
      } else {
        part.setText(bodyData, charset);
      }
      simple.addBodyPart(part);
      return simple;
    }
  }

  // ======================== DKIM 签名

  /**
   * 对 MimeMessage 施加 DKIM 签名（relaxed/relaxed, rsa-sha256）
   */
  private void dkimSign(MimeMessage msg, String domain, String selector, PrivateKey privateKey) throws Exception {
    // 获取原始 headers + body
    msg.saveChanges();
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    msg.writeTo(bos);
    byte[] rawMessage = bos.toByteArray();

    // 解析原始邮件为 headers + body
    String raw = new String(rawMessage, StandardCharsets.UTF_8);
    int splitIdx = raw.indexOf("\r\n\r\n");
    String headerBlock = (splitIdx >= 0) ? raw.substring(0, splitIdx) : raw;
    String bodyBlock = (splitIdx >= 0) ? raw.substring(splitIdx + 4) : "";

    // --- 计算 body hash (relaxed) ---
    String relaxedBody = canonicalizeBodyRelaxed(bodyBlock);
    byte[] bodyHash = MessageDigest.getInstance("SHA-256").digest(
        relaxedBody.getBytes(StandardCharsets.US_ASCII));
    String bh = Base64.getEncoder().encodeToString(bodyHash);

    // 选入签名的 header 列表（按 DKIM 规范建议）
    String signedHeaders = "from:to:cc:subject:date:message-id:mime-version:content-type";

    // 构建 DKIM-Signature 头（不含签名值 b=）
    String dkimHeaderUnsigned = "v=1; a=rsa-sha256; c=relaxed/relaxed; d=" + domain
        + "; s=" + selector + "; t=" + (System.currentTimeMillis() / 1000)
        + "; h=" + signedHeaders + "; bh=" + bh + "; b=";

    // 待签名字符串 = 选入 header 的 relaxed 规范化 + 伪头
    String headersCanon = canonicalizeHeadersRelaxed(headerBlock, signedHeaders, dkimHeaderUnsigned);
    String signatureInput = headersCanon + "\r\n" + dkimHeaderUnsigned;

    // RSA-SHA256 签名
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(privateKey);
    signer.update(signatureInput.getBytes(StandardCharsets.US_ASCII));
    byte[] signatureBytes = signer.sign();
    String bValue = Base64.getEncoder().encodeToString(signatureBytes);

    // 组装完整 DKIM-Signature 头（折行，每行不超过 76 字符）
    String dkimHeader = dkimHeaderUnsigned + bValue;
    dkimHeader = foldDKIMHeader(dkimHeader);

    // 将 DKIM-Signature 插入到邮件头部（From 之前，符合 DKIM 建议）
    msg.setHeader("DKIM-Signature", dkimHeader);
    // 重新保存以更新头部
    msg.saveChanges();
  }

  /** Relaxed 规范化 body：尾部空白收缩、确保 \r\n 结尾 */
  private String canonicalizeBodyRelaxed(String body) {
    if (body == null || body.isEmpty()) return "\r\n";
    String[] lines = body.split("\r\n", -1);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      // 去除尾部空白
      String trimmed = lines[i].replaceAll("[ \\t]+$", "");
      sb.append(trimmed);
      if (i < lines.length - 1 || body.endsWith("\r\n")) {
        sb.append("\r\n");
      }
    }
    return sb.toString();
  }

  /** Relaxed 规范化选中的 headers */
  private String canonicalizeHeadersRelaxed(String headerBlock, String signedHeaders, String dkimPseudoHeader) {
    StringBuilder sb = new StringBuilder();
    String[] headerLines = headerBlock.split("\r\n");
    // 解析 header 到列表（key 小写，value 折叠续行 + 清理）
    List<Map.Entry<String, StringBuilder>> headerList = new ArrayList<>();
    for (String line : headerLines) {
      if (line.isEmpty()) continue;
      if (line.startsWith(" ") || line.startsWith("\t")) {
        // 续行
        if (!headerList.isEmpty()) {
          Map.Entry<String, StringBuilder> last = headerList.get(headerList.size() - 1);
          last.getValue().append(line.trim());
        }
      } else {
        int colonIdx = line.indexOf(':');
        if (colonIdx > 0) {
          String key = line.substring(0, colonIdx).trim().toLowerCase();
          String value = line.substring(colonIdx + 1).trim();
          // relax: 合并连续的 WSP 为一个空格
          value = value.replaceAll("[ \\t]+", " ");
          final String k = key;
          final String v = value;
          headerList.add(new Map.Entry<String, StringBuilder>() {
            @Override
            public String getKey() { return k; }
            @Override
            public StringBuilder getValue() { return new StringBuilder(v); }
            @Override
            public StringBuilder setValue(StringBuilder value) { return null; }
          });
        }
      }
    }

    // 按 signedHeaders 顺序输出
    String[] signedKeys = signedHeaders.split(":");
    for (String key : signedKeys) {
      key = key.trim().toLowerCase();
      for (Map.Entry<String, StringBuilder> entry : headerList) {
        if (entry.getKey().equals(key)) {
          // relaxed: 小写 key，压缩 value 空白
          String relaxedValue = entry.getValue().toString().replaceAll("[ \\t]+", " ");
          sb.append(key).append(":").append(relaxedValue).append("\r\n");
          break;
        }
      }
    }

    // 追加 DKIM 伪头
    String pseudoKey = "dkim-signature";
    String pseudoValue = dkimPseudoHeader;
    sb.append(pseudoKey).append(":").append(pseudoValue).append("\r\n");

    return sb.toString();
  }

  /** 将 DKIM-Signature 头折行（每行不超过 76 字符 + CRLF 续行） */
  private String foldDKIMHeader(String header) {
    StringBuilder sb = new StringBuilder();
    int maxLen = 72; // 留一点余量给续行前导空白
    int pos = 0;
    while (pos < header.length()) {
      if (pos == 0) {
        int end = Math.min(header.length(), maxLen);
        sb.append(header, pos, end);
        pos = end;
      } else {
        sb.append("\r\n\t"); // CRLF + TAB (WSP)
        int end = Math.min(header.length(), pos + maxLen);
        sb.append(header, pos, end);
        pos = end;
      }
    }
    return sb.toString();
  }

  // ======================== 工具：MX 直投

  /** 按 @域名 分组收件人 */
  private Map<String, List<String>> groupByDomain(List<String> recipients) {
    Map<String, List<String>> groups = new LinkedHashMap<>();
    for (String addr : recipients) {
      addr = addr.trim();
      int atIdx = addr.indexOf('@');
      if (atIdx < 0) continue;
      String domain = addr.substring(atIdx + 1).toLowerCase();
      groups.computeIfAbsent(domain, k -> new ArrayList<>()).add(addr);
    }
    return groups;
  }

  /** DNS MX 记录查询，返回优先级最高的 MX 主机名 */
  private String resolveMX(String domain) {
    try {
      InitialDirContext ctx = new InitialDirContext(new Hashtable<>());
      Attributes attrs = ctx.getAttributes("dns:/" + domain, new String[]{"MX"});
      if (attrs == null) return null;
      javax.naming.directory.Attribute mxAttr = attrs.get("MX");
      if (mxAttr == null) return null;

      // 解析 MX 记录，按优先级排序
      List<MXRecord> records = new ArrayList<>();
      for (int i = 0; i < mxAttr.size(); i++) {
        String raw = (String) mxAttr.get(i);
        // 格式: "10 mail.example.com."
        String[] parts = raw.trim().split("\\s+");
        if (parts.length >= 2) {
          try {
            int priority = Integer.parseInt(parts[0]);
            String host = parts[1];
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
            records.add(new MXRecord(priority, host));
          } catch (NumberFormatException ignored) {
          }
        }
      }

      if (records.isEmpty()) return null;
      records.sort(Comparator.comparingInt(r -> r.priority));
      return records.get(0).host;
    } catch (NamingException e) {
      logger.warn("SMail | DNS MX 查询失败 %s: %s", domain, e.getLocalizedMessage());
      return null;
    }
  }

  private static class MXRecord {
    final int priority;
    final String host;
    MXRecord(int priority, String host) {
      this.priority = priority;
      this.host = host;
    }
  }

  /** 通过原始 SMTP 协议直投（使用 Socket） */
  private void deliverDirect(MimeMessage msg, String mxHost, int port, List<String> recipients) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    msg.writeTo(bos);
    byte[] rawMessage = bos.toByteArray();

    String envelopeFrom = profileConf.getString("from", "");

    try (Socket socket = new Socket(mxHost, port)) {
      socket.setSoTimeout(15000);
      BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
      var writer = socket.getOutputStream();

      // 简单 SMTP 会话
      readResponse(reader); // 220

      writeCommand(writer, "EHLO " + mxHost);
      readResponse(reader); // 250

      writeCommand(writer, "MAIL FROM:<" + envelopeFrom + ">");
      String mailResp = readResponse(reader);
      if (!mailResp.startsWith("250")) {
        throw new MessagingException("MAIL FROM 被拒绝: " + mailResp);
      }

      for (String rcpt : recipients) {
        writeCommand(writer, "RCPT TO:<" + rcpt + ">");
        String rcptResp = readResponse(reader);
        if (!rcptResp.startsWith("250")) {
          logger.warn("SMail | RCPT TO <%s> 被拒绝: %s", rcpt, rcptResp);
        }
      }

      writeCommand(writer, "DATA");
      String dataResp = readResponse(reader);
      if (!dataResp.startsWith("354")) {
        throw new MessagingException("DATA 命令被拒绝: " + dataResp);
      }

      writer.write(rawMessage);
      writer.write("\r\n.\r\n".getBytes(StandardCharsets.UTF_8));
      String finalResp = readResponse(reader);
      if (!finalResp.startsWith("250")) {
        throw new MessagingException("邮件数据被拒绝: " + finalResp);
      }

      writeCommand(writer, "QUIT");
      readResponse(reader);
    }
  }

  private String readResponse(BufferedReader reader) throws java.io.IOException {
    StringBuilder sb = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      sb.append(line).append("\r\n");
      // 多行回复：第 4 位为空格表示最后一行
      if (line.length() >= 4 && line.charAt(3) == ' ') break;
    }
    return sb.toString().trim();
  }

  private void writeCommand(java.io.OutputStream writer, String cmd) throws java.io.IOException {
    writer.write((cmd + "\r\n").getBytes(StandardCharsets.UTF_8));
    writer.flush();
  }

  // ======================== 工具：私钥加载

  /** 加载 PEM/DER 格式的 RSA 私钥 */
  private PrivateKey loadPrivateKey(String path) throws Exception {
    File keyFile = new File(path);
    if (!keyFile.exists() || !keyFile.isFile()) {
      throw new InvaildConfigException("DKIM 私钥文件不存在: " + path);
    }

    byte[] keyBytes;
    try (FileInputStream fis = new FileInputStream(keyFile)) {
      keyBytes = fis.readAllBytes();
    }

    // 尝试 PEM 格式
    String pem = new String(keyBytes, StandardCharsets.US_ASCII).trim();
    if (pem.startsWith("-----BEGIN")) {
      // 提取 Base64 内容
      StringBuilder base64Data = new StringBuilder();
      try (BufferedReader br = new BufferedReader(new StringReader(pem))) {
        String line;
        while ((line = br.readLine()) != null) {
          if (line.startsWith("-----")) continue;
          base64Data.append(line.trim());
        }
      }
      keyBytes = Base64.getMimeDecoder().decode(base64Data.toString());
    }

    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    KeyFactory kf = KeyFactory.getInstance("RSA");
    return kf.generatePrivate(spec);
  }

  // ======================== 工具：添加收件人

  private void addRecipients(MimeMessage msg, RecipientType type, List<String> addrs) throws MessagingException {
    if (addrs == null || addrs.isEmpty()) return;
    List<InternetAddress> parsed = new ArrayList<>();
    for (String addr : addrs) {
      if (addr == null || addr.trim().isEmpty()) continue;
      try {
        parsed.add(new InternetAddress(addr.trim()));
      } catch (AddressException e) {
        logger.warn("SMail | 忽略无效地址: %s", addr);
      }
    }
    if (!parsed.isEmpty()) {
      msg.addRecipients(type, parsed.toArray(new InternetAddress[0]));
    }
  }

}
