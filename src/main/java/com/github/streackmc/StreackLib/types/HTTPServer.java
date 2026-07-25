package com.github.streackmc.StreackLib.types;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.ServerSocket;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.http.request.Method;
import org.nanohttpd.protocols.http.response.Response;
import org.nanohttpd.protocols.http.response.Status;
import org.nanohttpd.protocols.http.threading.DefaultAsyncRunner;
import org.nanohttpd.util.IFactoryThrowing;
import org.shredzone.acme4j.Account;
import org.shredzone.acme4j.AccountBuilder;
import org.shredzone.acme4j.Authorization;
import org.shredzone.acme4j.Order;
import org.shredzone.acme4j.Session;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import com.github.streackmc.StreackLib.StreackLib;
import com.github.streackmc.StreackLib.self.logger;
import com.github.streackmc.StreackLib.self.manager;
import com.github.streackmc.StreackLib.utils.MCColor;
import com.github.streackmc.StreackLib.utils.SEventCentral;
import com.github.streackmc.StreackLib.utils.SFile;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * 基于 NanoHTTPd 的 HTTP/HTTPS 服务器。
 * 其它插件可通过 registerHandler(String path, Handler h) 注册自己的子路由。
 * <p>
 * 支持 SSL/TLS（JKS/PKCS12 密钥库）及 Let's Encrypt ACME 自动签发/续签。
 *
 * @apiNote 本实例化类不继承 {@link StreackLibNewable}，而是 {@link NanoHTTPD} 。
 * @author kdxiaoyi
 * @author KimiAI 亦有贡献
 * @since 0.1.0
 */
public class HTTPServer extends NanoHTTPD {

  public int MAX_URI = 2048;
  public long MAX_FILE_SIZE = 20L/* MB */ * 1024 * 1024;
  private final Map<String, Handler> handlerMap = new ConcurrentHashMap<>();
  private String listenAddress;
  private final SslConfig sslConfig;
  private final ConcurrentHashMap<String, String> acmeChallengeMap = new ConcurrentHashMap<>();
  private NanoHTTPD acmeHttpServer;
  private SSLContext sslContext;
  private final ScheduledExecutorService sslRenewScheduler = Executors.newSingleThreadScheduledExecutor(
      r -> { Thread t = new Thread(r, "StreackLib.HTTPServer/SSL-Renew"); t.setDaemon(true); return t; });

  /** 函数式接口，方便 Lambda 注册 */
  @FunctionalInterface
  public interface Handler {
    Response handle(IHTTPSession session) throws Exception;
  }

  public final Long INSTANCE_ID = StreackLib.getUniqueID();
  public final static class EVENTS {
    public static final String STARTED    = "streacklib.httpserver:started";
    public static final String STOPPED    = "streacklib.httpserver:stopped";
    public static final String ON_REQUEST = "streacklib.httpserver:on_request";
  }

  /**
   * 初始化一个 HTTPServer 对象。
   *
   * @param hostname 监听地址
   * @param port     监听端口
   */
  public HTTPServer(String hostname, int port) {
    super(hostname, port);
    putProxyProtocolSupport(StreackLib.ENV.conf.getString("http-server.proxy-protocol-version"));
    this.listenAddress = hostname + ":" + port;
    this.MAX_URI = StreackLib.ENV.conf.getInt("http-server.max-uri-length", 2048);
    this.MAX_FILE_SIZE = StreackLib.ENV.conf.getLong("http-server.max-file-size-kb", 20480L) * 1024;
    this.sslConfig = new SslConfig(StreackLib.ENV.conf);
  }

  // ==========================================
  // SSL 配置
  // ==========================================

  /** SSL/TLS 及 ACME 自动签发配置 */
  public static class SslConfig {
    public final boolean enabled;
    public final String  keyPath;
    public final String  passwd;
    public final String  type;        // JKS / PKCS12
    public final boolean autosignEnabled;
    public final String  acmeDomain;
    public final String  acmeEmail;
    public final int     acmeIntervalDays;

    SslConfig(SConfig conf) {
      this.enabled    = conf.getBoolean("http-server.ssl.enabled", false);
      this.keyPath    = conf.getString ("http-server.ssl.key-path", "key.p12");
      this.passwd     = conf.getString ("http-server.ssl.passwd", "");
      String rawType  = conf.getString ("http-server.ssl.type", "PKCS12").toUpperCase();
      this.type       = rawType.equals("PEM") ? "PKCS12" : rawType;
      this.autosignEnabled = conf.getBoolean("http-server.ssl.autosign.enabled", false);
      this.acmeDomain      = conf.getString ("http-server.ssl.autosign.domain", "");
      this.acmeEmail       = conf.getString ("http-server.ssl.autosign.email", "");
      this.acmeIntervalDays = conf.getInt   ("http-server.ssl.autosign.interval", 30);
    }

    boolean isEnabled()      { return enabled; }
    boolean isAutosign()     { return enabled && autosignEnabled && !acmeDomain.isBlank(); }
    String  keyStoreType()   { return type; }
    char[]  password()       { return passwd.toCharArray(); }
  }

  // ==========================================
  // 启动 / 停止
  // ==========================================

  /** 启动当前 HTTPServer（根据需要启用 SSL/ACME）。 */
  public void startServer() {
    if (isAlive()) return;

    try {
      setAsyncRunner(new DefaultAsyncRunner() {
        private final ThreadPoolExecutor exec = new ThreadPoolExecutor(
            8, 16, 60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadFactoryBuilder().setNameFormat("StreackLib.HTTPServer/Worker-%d")
                .setDaemon(true).build(),
            new ThreadPoolExecutor.AbortPolicy());
        public void exec(Runnable code) { exec.execute(code); }
        public void close() { exec.shutdownNow(); }
      });

      // 设置 SSL
      if (sslConfig.isEnabled()) {
        File keyFile = resolveKeyFile();
        if (!keyFile.exists() && sslConfig.isAutosign()) {
          logger.info(getServerFullName() + "未找到 SSL 证书，开始 ACME 自动签发…");
          performAcmeChallenge();
        }
        if (keyFile.exists()) {
          sslContext = createSSLContext(keyFile);
          SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
          setServerSocketFactory(new IFactoryThrowing<ServerSocket, IOException>() {
            @Override public ServerSocket create() throws IOException {
              return ssf.createServerSocket();
            }
          });
          logger.info(getServerFullName() + "已加载 SSL 证书: " + keyFile.getAbsolutePath());
        } else {
          logger.warning(getServerFullName() + "SSL 已启用但未找到证书文件，回退为 HTTP 模式");
        }
      }

      start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
      logger.info("已启动" + getServerFullName()
          + (sslContext != null ? " [SSL]" : " [Plain]"));
      SEventCentral.broadcastEvent(EVENTS.STARTED, INSTANCE_ID)
          .set("address", this.listenAddress).broadcast();

      // 启动 ACME 续签定时任务
      if (sslConfig.isAutosign() && sslContext != null) {
        scheduleAcmeRenewal();
      }
    } catch (Exception e) {
      logger.severe("无法启动" + getServerFullName() + "：" + e.getLocalizedMessage());
      e.printStackTrace();
    }
  }

  /** 停止当前 HTTPServer */
  public void stopServer() {
    if (isAlive()) {
      stop();
      logger.info("已停止" + getServerFullName() + ".\nfrom " + manager.getCaller(null).get(0));
      SEventCentral.broadcastEvent(EVENTS.STOPPED, INSTANCE_ID)
          .set("address", this.listenAddress).broadcast();
    }
    sslRenewScheduler.shutdownNow();
  }

  public boolean isStarted() { return isAlive(); }

  // ==========================================
  // 路由注册
  // ==========================================

  public void registerHandler(String path, Handler handler) throws Exception {
    if (handlerMap.containsKey(path)) {
      logger.warning(getServerFullName() + "无法注册在 " + path + "上的事件处理器：该路径已被占用\nfrom "
          + manager.getCaller(null).get(0));
      throw new Exception("在" + path + "上的事件处理器已被注册");
    }
    handlerMap.put(path, handler);
    logger.info(getServerFullName() + "注册在 " + path + "上的事件处理器.\nfrom " + manager.getCaller(null).get(0));
  }

  public void removeHandler(String path) {
    if (path == null) {
      logger.warning(getServerFullName() + "未能取消注册事件处理器，因为目标地址是 null .\nfrom "
          + manager.getCaller(null).get(0));
      return;
    }
    if (handlerMap.remove(path) != null) {
      logger.info(getServerFullName() + "取消注册在 " + path + "上的事件处理器.\nfrom " + manager.getCaller(null).get(0));
    } else {
      logger.warning(getServerFullName() + "未能取消注册在 " + path + "上的事件处理器：该路径未被注册.\nfrom "
          + manager.getCaller(null).get(0));
    }
  }

  public String getServerFullName() {
    return " HTTPServer[" + listenAddress + "] ";
  }

  // ==========================================
  // IP 封禁
  // ==========================================

  private static List<Map<String, Object>> banListCache;
  private static long banListLastModified;

  @Nullable
  @Deprecated
  public static String detailBannedIp(@NotNull String ip) throws IllegalArgumentException {
    Objects.requireNonNull(ip, "传入了一个 null");
    Path banIpList = StreackLib.ENV.dataPath.toPath().resolve("../../ban-ips.json");
    File file = banIpList.toFile();
    if (!file.exists()) return null;

    long lastMod = file.lastModified();
    if (banListCache == null || lastMod != banListLastModified) {
      try (Reader reader = Files.newBufferedReader(banIpList, StandardCharsets.UTF_8)) {
        Type listType = new TypeToken<List<Map<String, Object>>>() {}.getType();
        List<Map<String, Object>> list = new Gson().fromJson(reader, listType);
        banListCache = list != null ? list : Collections.emptyList();
        banListLastModified = lastMod;
      } catch (IOException e) {
        banListCache = Collections.emptyList();
        banListLastModified = 0L;
        return null;
      }
    }

    for (Map<String, Object> record : banListCache) {
      Object cachedIp = record.get("ip");
      if (ip.equals(cachedIp)) return record.get("reason") != null ? record.get("reason").toString() : null;
    }
    return null;
  }

  @Nullable
  protected Response checkBan(String ip, String sessionId, Method method) {
    List<String> bannedList = StreackLib.ENV.conf.getListOfString("http-server.banip.blacklist");
    if (bannedList.indexOf(ip) >= 0) {
      logger.info(getServerFullName() + String.format("拒绝了 %s 的连接：[ 内置黑名单 ]", ip));
      boolean use404 = StreackLib.ENV.conf.getBoolean("http-server.banip.use-404-as-403", false);
      return use404
          ? Response.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found")
          : Response.newFixedLengthResponse(Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT,
              "403 Your IP has been banned from this server.");
    }

    if (StreackLib.ENV.conf.getBoolean("http-server.banip.sync-game", true)) {
      SConfig potentialBanEntry = manager.backend.checkBan(ip);
      long expireTime = potentialBanEntry.getLong("expire", -1L);
      if (potentialBanEntry.getBoolean("banned", false)
          && (expireTime >= System.currentTimeMillis() || expireTime < 0L)) {
        logger.info(getServerFullName() + String.format("拒绝了 %s 的连接：[ %s ]", ip,
            potentialBanEntry.getString("reason", "")));
        boolean use404 = StreackLib.ENV.conf.getBoolean("http-server.use-404-as-403", false);
        if (use404) {
          return Response.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "404 Not Found");
        }
        String reasonStr = MCColor.toHtml(potentialBanEntry.getString("reason", ""));
        reasonStr = reasonStr.isBlank() ? "Your" : "Because [" + reasonStr + "], your";
        String expireStr = (expireTime < 0) ? "until forever."
            : "until " + StreackLib.formatTime(expireTime, "YYYY-MM-DD hh:mm:ss") + ".";
        return Response.newFixedLengthResponse(Status.FORBIDDEN, NanoHTTPD.MIME_HTML,
            String.format("403 Forbidden: %s IP has been banned from this server since %s , %s",
                reasonStr, StreackLib.formatTime(potentialBanEntry.getLong("create", 0L), "YYYY-MM-DD hh:mm:ss"),
                expireStr));
      }
    }
    return null;
  }

  // ==========================================
  // 请求处理
  // ==========================================

  @Override
  public Response serve(IHTTPSession session) {
    String id   = StreackLib.getUniqueID().toString();
    String ip   = session.getRemoteIpAddress();
    String uri  = session.getUri()
        .replaceAll("\\.\\./", "")
        .replaceAll("[\\p{Cntrl}&&[^\r\n]]+", "")
        .replaceAll("[\r\n]+", " ");
    Method method = session.getMethod();

    // --- ACME HTTP-01 挑战 ---
    if (uri.startsWith("/.well-known/acme-challenge/")) {
      String token = uri.substring("/.well-known/acme-challenge/".length());
      String keyAuth = acmeChallengeMap.get(token);
      if (keyAuth != null) {
        logger.debug(getServerFullName() + "ACME: 响应挑战 token=" + token);
        return Response.newFixedLengthResponse(Status.OK, "text/plain", keyAuth);
      }
      logger.debug(getServerFullName() + "ACME: 未知挑战 token=" + token);
      return Response.newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found");
    }

    // 封禁检查
    Response potentialBanRsp = checkBan(ip, id, method);
    if (potentialBanRsp != null) return potentialBanRsp;

    // 日志
    logger.info(getServerFullName() + "收到请求#" + id + "\n  来源 = " + ip
        + "\n  路径 = " + uri + "\n  方法 = " + method.toString());
    SEventCentral.broadcastEvent(EVENTS.ON_REQUEST, INSTANCE_ID)
        .set("address", this.listenAddress).set("uri", uri)
        .set("origin", ip).set("method", method.toString()).broadcast();

    // URI 过长
    if (uri.length() > MAX_URI) {
      logger.warn(getServerFullName() + "请求#" + id + " 的URI过长，已拒绝。");
      return Response.newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "414 Request-URI Too Long");
    }

    // 命中处理器
    Handler h = handlerMap.get(uri);
    if (h != null) {
      try {
        logger.debug(getServerFullName() + "请求#" + id + " 命中已注册的处理器。");
        return h.handle(session);
      } catch (Exception ex) {
        ex.printStackTrace();
        logger.severe(getServerFullName() + "请求#" + id + " 上的事件时发生异常：" + ex.getLocalizedMessage());
        return Response.newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Internal Server Error");
      }
    }

    // 文件传输
    if (!StreackLib.ENV.conf.getBoolean("http-server.allow-file-transport", false)) {
      logger.debug(getServerFullName() + "请求#" + id + " 没有命中处理器，且文件传输已禁用。");
      return Response.newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found");
    }

    try {
      SFile.mkdir(StreackLib.ENV.dataPath, "HTTPServer");
      File root = new File(StreackLib.ENV.dataPath, "HTTPServer");
      File reach = new File(root, uri).getCanonicalFile();
      logger.debug(getServerFullName() + "请求#" + id + " 正在获取文件 " + reach.getAbsolutePath());

      if (!reach.getPath().startsWith(root.getCanonicalPath())) {
        logger.warning(getServerFullName() + "请求#" + id + " 试图调用非法路径，已被拦截。");
        return Response.newFixedLengthResponse(Status.FORBIDDEN, MIME_PLAINTEXT, "403 Forbidden");
      }

      if (reach.exists() && reach.isFile()) {
        int size = (int) reach.length();
        String mime;
        try { mime = SFile.getMIME(reach); } catch (Exception ignore) { mime = "application/octet-stream"; }

        if (size > MAX_FILE_SIZE) {
          logger.warning(getServerFullName() + "请求#" + id + " 文件体积超出限制: " + size + " > " + MAX_FILE_SIZE);
          return Response.newFixedLengthResponse(Status.BAD_REQUEST, MIME_PLAINTEXT, "413 Payload Too Large");
        }

        FileChannel fc = FileChannel.open(reach.toPath(), StandardOpenOption.READ);
        return Response.newChunkedResponse(Status.OK, mime, Channels.newInputStream(fc));
      }
      logger.debug(getServerFullName() + "请求#" + id + " 请求的文件不存在。");
      return Response.newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found");
    } catch (IOException e) {
      logger.severe(getServerFullName() + "请求#" + id + " 的文件传输发生异常：" + e.getLocalizedMessage());
      e.printStackTrace();
      return Response.newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Internal Server Error");
    }
  }

  // ==========================================
  // SSL / ACME 实现
  // ==========================================

  /** 解析密钥文件路径（相对 dataPath） */
  private File resolveKeyFile() {
    String path = sslConfig.keyPath;
    File f = new File(path);
    if (f.isAbsolute()) return f;
    return new File(StreackLib.ENV.dataPath, path);
  }

  /** 从 KeyStore 文件创建 SSLContext */
  private SSLContext createSSLContext(File keyFile) throws Exception {
    KeyStore ks = KeyStore.getInstance(sslConfig.keyStoreType());
    try (FileInputStream fis = new FileInputStream(keyFile)) {
      ks.load(fis, sslConfig.password());
    }

    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, sslConfig.password());

    SSLContext ctx = SSLContext.getInstance("TLS");
    ctx.init(kmf.getKeyManagers(), null, null);
    return ctx;
  }

  /** 执行一次完整的 ACME HTTP-01 挑战并获取证书 */
  private void performAcmeChallenge() {
    logger.info(getServerFullName() + "ACME: 开始为 " + sslConfig.acmeDomain + " 申请证书…");

    // 1) 在 80 端口启动临时 HTTP 服务器
    try {
      acmeHttpServer = new NanoHTTPD("0.0.0.0", 80) {
        @Override public Response serve(IHTTPSession session) {
          String uri = session.getUri();
          if (uri.startsWith("/.well-known/acme-challenge/")) {
            String token = uri.substring("/.well-known/acme-challenge/".length());
            String keyAuth = acmeChallengeMap.get(token);
            if (keyAuth != null) {
              return Response.newFixedLengthResponse(Status.OK, "text/plain", keyAuth);
            }
          }
          return Response.newFixedLengthResponse(Status.NOT_FOUND, "text/plain", "Not Found");
        }
      };
      acmeHttpServer.start();
      logger.info(getServerFullName() + "ACME: 临时 HTTP 服务器已启动在 0.0.0.0:80");
    } catch (IOException e) {
      logger.warning(getServerFullName() + "ACME: 无法在 80 端口启动临时服务器: " + e.getMessage());
      return;
    }

    try {
      // 2) 创建 ACME 会话和账户
      Session session = new Session("acme://letsencrypt.org");
      KeyPair accountKey = KeyPairUtils.createKeyPair(2048);
      Account account = new AccountBuilder()
          .agreeToTermsOfService()
          .useKeyPair(accountKey)
          .addEmail(sslConfig.acmeEmail)
          .create(session);

      logger.info(getServerFullName() + "ACME: 账户已创建，URL=" + account.getLocation());

      // 3) 申请域名授权
      Order order = account.newOrder().domains(sslConfig.acmeDomain).create();
      logger.info(getServerFullName() + "ACME: 订单已创建");

      for (Authorization auth : order.getAuthorizations()) {
        Http01Challenge challenge = auth.findChallenge(Http01Challenge.class).orElse(null);
        if (challenge == null) {
          logger.warning(getServerFullName() + "ACME: 未找到 HTTP-01 挑战");
          continue;
        }

        String token = challenge.getToken();
        String keyAuthorization = challenge.getAuthorization();
        acmeChallengeMap.put(token, keyAuthorization);

        logger.info(getServerFullName() + "ACME: 触发 HTTP-01 验证 token=" + token);
        challenge.trigger();

        // 轮询等待验证
        int attempts = 0;
        while (auth.getStatus() == org.shredzone.acme4j.Status.PENDING && attempts < 30) {
          Thread.sleep(2000);
          auth.update();
          attempts++;
        }

        if (auth.getStatus() != org.shredzone.acme4j.Status.VALID) {
          logger.severe(getServerFullName() + "ACME: 域名验证失败: " + auth.getStatus());
          return;
        }
        logger.info(getServerFullName() + "ACME: 域名验证通过");
      }

      // 4) 生成 CSR 并获取证书
      KeyPair domainKey = KeyPairUtils.createKeyPair(2048);
      CSRBuilder csrb = new CSRBuilder();
      csrb.addDomain(sslConfig.acmeDomain);
      csrb.sign(domainKey);
      order.execute(csrb.getEncoded());

      // 轮询等待签发
      int attempts = 0;
      while (order.getStatus() != org.shredzone.acme4j.Status.VALID && attempts < 30) {
        Thread.sleep(2000);
        order.update();
        attempts++;
      }

      // 5) 下载并保存证书
      org.shredzone.acme4j.Certificate cert = order.getCertificate();
      X509Certificate x509 = cert.getCertificate();
      File keyFile = resolveKeyFile();

      // 保存为 PKCS12
      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(null, null);
      ks.setKeyEntry("streacklib",
          domainKey.getPrivate(),
          sslConfig.password(),
          new Certificate[] { x509 });
      try (java.io.FileOutputStream fos = new java.io.FileOutputStream(keyFile)) {
        ks.store(fos, sslConfig.password());
      }
      logger.info(getServerFullName() + "ACME: 证书已保存到 " + keyFile.getAbsolutePath());
    } catch (Exception e) {
      logger.severe(getServerFullName() + "ACME: 证书申请失败: " + e.getMessage());
      e.printStackTrace();
    } finally {
      // 清理
      if (acmeHttpServer != null) {
        acmeHttpServer.stop();
        acmeHttpServer = null;
      }
      acmeChallengeMap.clear();
    }
  }

  /** 定时检查证书到期并续签 */
  private void scheduleAcmeRenewal() {
    long intervalDays = sslConfig.acmeIntervalDays;
    sslRenewScheduler.scheduleAtFixedRate(() -> {
      try {
        File keyFile = resolveKeyFile();
        if (!keyFile.exists()) return;

        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(keyFile)) {
          ks.load(fis, sslConfig.password());
        }
        X509Certificate x509 = (X509Certificate) ks.getCertificate("streacklib");
        if (x509 == null) return;

        long daysLeft = (x509.getNotAfter().getTime() - System.currentTimeMillis())
            / (24L * 3600 * 1000);
        if (daysLeft > intervalDays) return; // 未到续签时间

        logger.info(getServerFullName() + "ACME: 证书距到期 " + daysLeft + " 天，开始续签…");
        performAcmeChallenge();

        // 热重载 SSLContext
        if (keyFile.exists()) {
          SSLContext newCtx = createSSLContext(keyFile);
          SSLServerSocketFactory ssf = newCtx.getServerSocketFactory();
          setServerSocketFactory(new IFactoryThrowing<ServerSocket, IOException>() {
            @Override public ServerSocket create() throws IOException {
              return ssf.createServerSocket();
            }
          });
          sslContext = newCtx;
          logger.info(getServerFullName() + "ACME: 证书已续签并热重载");
        }
      } catch (Exception e) {
        logger.warning(getServerFullName() + "ACME: 续签检查失败: " + e.getMessage());
      }
    }, 1, intervalDays, TimeUnit.DAYS);
  }
}
