# SMail
本模块用于提供统一便携、一次配置处处使用的发送邮件接口。

以下展示部分用法：

## 发送一封邮件
```java
SMail.builder("profile_smtp")
  .to("user@example.com")
  .subject("Hello World")
  .body("<h1>Hello</h1><p>这是一封测试邮件</p>", true)
  .build()
  .send();
```

## 使用附件
```java
SMail.builder("profile_smtp")
  .to("alice@example.com", "bob@example.com")
  .cc("cc@example.com")
  .bcc("bcc@example.com")
  .subject("报告与图片")
  .body("<h1>报告</h1><p>详见附件</p><img src='cid:chart.png'>", true)
  .alternative("报告内容，详见附件")
  .attachments(new File("/path/to/report.pdf"))
  .inline_images(new File("/path/to/chart.png"))
  .priority(1)
  .build()
  .send();
```

## 复用或自定义配置
```java
// 邮件配置本质上是 JSON 的 SConfig
SConfig reusedConf = mail.get();
SConfig newConf = new SConfig("", "json", null);
reusedConf.putString("subject", "复用配置");

// 传入配置就可以继承邮件，并重新发送
SMail.builder("profile_smtp", reusedConf)
  // 传入配置可以修改
  .to("Newuser@example.com")
  .body("正文", false)
  .build()
  .send();

// 链式 Builder 修改后原始配置也会被修改：
String to = reusedConf.getString("to", "<unset>");
// to = "Newuser@example.com"
}
```