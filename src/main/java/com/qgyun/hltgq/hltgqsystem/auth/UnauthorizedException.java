package com.qgyun.hltgq.hltgqsystem.auth;

/**
 * 未登录 / 会话过期
 */
public class UnauthorizedException extends AuthException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
