package com.github.streackmc.StreackLib.errors;

import com.github.streackmc.StreackLib.errors.raw.StreackLibNewableException;

/** 无法找到指定的配置项 */
public class ConfigNotFoundException extends StreackLibNewableException {
  public ConfigNotFoundException() {
    super();
  }

  public ConfigNotFoundException(String message) {
    super(message);
  }

  public ConfigNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConfigNotFoundException(Throwable cause) {
    super(cause);
  }
}
