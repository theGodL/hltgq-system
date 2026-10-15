package com.qgyun.hltgq.hltgqsystem.menu;

/**
 * 平台菜单接口调用异常（转发失败、响应无法解析）。
 * <p>由 GlobalExceptionHandler 映射为 503。
 */
public class PlatformApiException extends RuntimeException {

    public PlatformApiException(String message) {
        super(message);
    }

    public PlatformApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
