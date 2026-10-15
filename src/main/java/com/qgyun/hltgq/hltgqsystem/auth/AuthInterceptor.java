package com.qgyun.hltgq.hltgqsystem.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 登录验证拦截器：白名单放行 → 提取会话（Authorization/Cookie）→ 会话有效性校验 → 用户上下文写入 ThreadLocal。
 * <p>登录判定：authorization 头或 sessionId Cookie 携带 sessionId（形如 dev_hltgq_session:xxxx）
 * 且 Redis 中存在有效会话才算登录；无效/过期会话按未登录处理。
 * <p>未登录：浏览器导航（Accept 含 text/html）302 跳转平台登录页；AJAX/API 返回 401 JSON。
 * <p>会话服务不可用（Redis 故障）：503 JSON，禁止降级放行。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    /** 平台登录页地址（未登录跳转） */
    @Value("${auth.login-page-url:http://220.179.1.110:8081/login/user/login}")
    private String loginPageUrl;

    /** 可配置白名单（逗号分隔），与代码固定白名单合并 */
    @Value("${auth.white-list:}")
    private String whiteListConfig;

    @Autowired
    private SessionContextService sessionContextService;

    @Autowired
    private RolePermissionService rolePermissionService;

    @Autowired
    private com.qgyun.hltgq.hltgqsystem.loginlog.service.LoginLogService loginLogService;

    /** 代码固定白名单（不受配置影响） */
    private static final Set<String> FIXED_WHITE_LIST = new HashSet<>(Arrays.asList(
            "/auth/login",
            "/error"
    ));

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        String relativePath = path.substring(contextPath.length());
        if (isWhiteListed(relativePath)) {
            return true;
        }

        String sessionId = sessionContextService.extractSessionId(request);
        if (sessionId == null) {
            log.warn("未登录：{} {} 未携带会话 ID（Authorization/Cookie 均缺失）", request.getMethod(), relativePath);
            handleUnauthorized(request, response);
            return false;
        }
        try {
            UserContext user = sessionContextService.resolveUser(sessionId);
            // 登录日志：首次见到该会话则记一条（真实登录），并用请求 IP/UA 回填
            try {
                loginLogService.touch(sessionId, user, clientIp(request), request.getHeader("User-Agent"));
            } catch (Exception ignore) {
                // 登录日志失败不影响鉴权
            }
            // 静态资源（*.html、/lib 等无处理器方法）仅需会话有效，无需用户上下文
            if (!(handler instanceof HandlerMethod)) {
                return true;
            }
            UserContextHolder.set(user);
            return checkAdminPermission(request, response, handler, relativePath, user);
        } catch (UnauthorizedException e) {
            log.warn("未登录或会话无效：{} {} - {}", request.getMethod(), relativePath, e.getMessage());
            handleUnauthorized(request, response);
            return false;
        } catch (SessionUnavailableException e) {
            log.error("会话服务不可用：{} {} - {}", request.getMethod(), relativePath, e.getMessage());
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "{\"code\":503,\"message\":\"会话服务不可用，请稍后重试\"}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }

    /**
     * 系统管理员权限校验：方法/类标注 @RequireAdmin 的敏感接口，校验当前登录人是否拥有系统管理员角色，非管理员返回 403。
     * <p>权限判定服务异常时不降级放行，返回 503（与登录鉴权同策略）。
     */
    private boolean checkAdminPermission(HttpServletRequest request, HttpServletResponse response,
                                         Object handler, String relativePath, UserContext user) throws Exception {
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RequireAdmin requireAdmin = handlerMethod.getMethodAnnotation(RequireAdmin.class);
        if (requireAdmin == null) {
            requireAdmin = handlerMethod.getBeanType().getAnnotation(RequireAdmin.class);
        }
        if (requireAdmin == null) {
            return true;
        }
        String userId = user == null ? null : user.getUserId();
        try {
            if (rolePermissionService.isAdmin(user)) {
                return true;
            }
            log.warn("无操作权限：{} {} userId={}", request.getMethod(), relativePath, userId);
            writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                    "{\"code\":403,\"message\":\"无操作权限，仅系统管理员可操作\"}");
            return false;
        } catch (Exception e) {
            log.error("权限判定服务不可用：{} {} - {}", request.getMethod(), relativePath, e.getMessage());
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "{\"code\":503,\"message\":\"权限服务不可用，请稍后重试\"}");
            return false;
        }
    }

    /**
     * 白名单判定：配置白名单 + 代码固定白名单，均按前缀匹配。
     */
    private boolean isWhiteListed(String path) {
        for (String item : FIXED_WHITE_LIST) {
            if (path.startsWith(item)) {
                return true;
            }
        }
        if (StringUtils.hasText(whiteListConfig)) {
            for (String item : whiteListConfig.split(",")) {
                String trimmed = item.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (path.startsWith(trimmed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 未登录处理：页面导航 302 跳转登录页；AJAX/API 返回 401 JSON（携带 redirectUrl 供前端跳转）
     */
    private void handleUnauthorized(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/html")) {
            response.sendRedirect(loginPageUrl);
            return;
        }
        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                "{\"code\":401,\"message\":\"未登录\",\"redirectUrl\":\"" + loginPageUrl + "\"}");
    }

    /**
     * 取真实客户端 IP（与平台 RequestHelper.readIp 一致）：
     * 依次尝试常见代理头，取第一个非 unknown 的有效 IP，兜底 getRemoteAddr。
     */
    private static final String[] IP_HEADERS = {
            "x-forwarded-for", "X-Forwarded-For", "Proxy-Client-IP",
            "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR", "X-Real-IP"
    };

    private static String clientIp(HttpServletRequest request) {
        String ip = null;
        for (String header : IP_HEADERS) {
            String v = request.getHeader(header);
            if (StringUtils.hasText(v) && !"unknown".equalsIgnoreCase(v.trim())) {
                ip = v.trim();
                break;
            }
        }
        if (!StringUtils.hasText(ip)) {
            return request.getRemoteAddr();
        }
        if (ip.indexOf(',') >= 0) {
            for (String s : ip.split(",")) {
                if (StringUtils.hasText(s) && !"unknown".equalsIgnoreCase(s.trim())) {
                    return s.trim();
                }
            }
        }
        return ip;
    }

    /**
     * 输出 JSON 响应
     */
    private void writeJson(HttpServletResponse response, int status, String body) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(body);
    }
}
