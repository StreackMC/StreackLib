package com.github.streackmc.StreackLib.errors;

/** 配置项无效 */
public class InvaildConfigException extends Exception {
    public InvaildConfigException() {
        super();
    }

    public InvaildConfigException(String message) {
        super(message);
    }

    public InvaildConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvaildConfigException(Throwable cause) {
        super(cause);
    }
}
