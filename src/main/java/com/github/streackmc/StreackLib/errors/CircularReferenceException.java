package com.github.streackmc.StreackLib.errors;

import com.github.streackmc.StreackLib.errors.raw.StreackLibNewableRuntimeException;

/** 发现循环引用 */
public class CircularReferenceException extends StreackLibNewableRuntimeException {
  public CircularReferenceException() {
    super();
  }

  public CircularReferenceException(String message) {
    super(message);
  }

  public CircularReferenceException(String message, Throwable cause) {
    super(message, cause);
  }

  public CircularReferenceException(Throwable cause) {
    super(cause);
  }
}
