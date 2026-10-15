package com.qgyun.hltgq.hltgqsystem.controller;

import com.qgyun.hltgq.hltgqsystem.auth.RequireAdmin;
import com.qgyun.hltgq.hltgqsystem.menu.service.MenuAdminService;
import com.qgyun.hltgq.hltgqsystem.menu.vo.AdminTreeVO;
import com.qgyun.hltgq.hltgqsystem.menu.vo.MenuSaveRequest;
import com.qgyun.hltgq.hltgqsystem.menu.vo.SaveResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 菜单管理接口（menu-admin.html 手搓管理页调用）：整树查询 / 保存 / 还原默认。
 * <p>类级 @RequireAdmin：仅管理员会话可访问（非管理员 403）。
 */
@RestController
@RequestMapping("/menu/admin")
@RequireAdmin
public class MenuAdminController {

    @Autowired
    private MenuAdminService menuAdminService;

    /**
     * 生效结构整树（全量节点，含隐藏/孤儿/锁定标记）。
     */
    @GetMapping("/tree")
    public AdminTreeVO tree() {
        return menuAdminService.getAdminTree();
    }

    /**
     * 整树保存（提交顺序即展示顺序；与平台现状无差异的项不落配置）。
     */
    @PostMapping("/save")
    public SaveResultVO save(@RequestBody MenuSaveRequest request) {
        return menuAdminService.save(request);
    }

    /**
     * 还原默认：清空 hltgq 全部展示配置（回到平台原样）。
     */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        int removed = menuAdminService.reset();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("message", "ok");
        resp.put("data", removed);
        return resp;
    }
}
