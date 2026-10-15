package com.qgyun.hltgq.hltgqsystem.controller;

import com.qgyun.hltgq.hltgqsystem.menu.service.MenuRenderService;
import com.qgyun.hltgq.hltgqsystem.menu.vo.MenuTreeVO;
import com.qgyun.hltgq.hltgqsystem.menu.vo.OverlayRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 菜单渲染接口（主题壳调用）：转发平台菜单接口 + 叠加展示配置。
 * <p>响应按约定包装：{"code":0,"message":"ok","data":{"version":"...","menus":[...]}}；
 * 未登录 401、会话服务异常/平台接口失败 503。
 */
@RestController
@RequestMapping("/menu")
public class MenuRenderController {

    @Autowired
    private MenuRenderService menuRenderService;

    /**
     * 主路径：服务端带会话转发平台菜单接口，权限过滤同源。
     */
    @GetMapping("/tree")
    public Map<String, Object> tree(HttpServletRequest request) {
        MenuTreeVO tree = menuRenderService.renderTree(request);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "ok");
        resp.put("data", tree);
        return resp;
    }

    /**
     * 兜底路径：前端自传基础树（如主题壳缓存的上一版快照），仅叠加配置。
     */
    @PostMapping("/overlay")
    public Map<String, Object> overlay(@RequestBody OverlayRequest request) {
        MenuTreeVO tree = menuRenderService.overlay(request);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "ok");
        resp.put("data", tree);
        return resp;
    }
}
