package com.qgyun.hltgq.hltgqsystem.controller;

import com.qgyun.hltgq.hltgqsystem.auth.RolePermissionService;
import com.qgyun.hltgq.hltgqsystem.auth.SessionContextService;
import com.qgyun.hltgq.hltgqsystem.auth.UserContext;
import com.qgyun.hltgq.hltgqsystem.auth.UserContextHolder;
import com.qgyun.hltgq.hltgqsystem.vo.CurrentUserVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 登录鉴权接口：供前端获取当前登录人与管理员标记。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private SessionContextService sessionContextService;

    @Autowired
    private RolePermissionService rolePermissionService;

    /**
     * 当前登录人：拦截器开启时优先复用 ThreadLocal，否则从请求重新解析。
     */
    @GetMapping("/current-user")
    public CurrentUserVO currentUser(HttpServletRequest request) {
        UserContext user = UserContextHolder.currentUser();
        if (user == null) {
            user = sessionContextService.resolveCurrentUser(request);
        }
        sessionContextService.resolveLoginName(user);
        CurrentUserVO vo = new CurrentUserVO();
        BeanUtils.copyProperties(user, vo);
        vo.setAdmin(rolePermissionService.isAdmin(user));
        return vo;
    }
}
