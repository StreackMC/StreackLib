package com.github.streackmc.StreackLib.errors;

/** 无法找到指定的配置项 */
public class ConfigNotFoundException extends Exception {
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
