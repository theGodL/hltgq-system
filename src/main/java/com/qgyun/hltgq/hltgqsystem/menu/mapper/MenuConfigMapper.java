package com.qgyun.hltgq.hltgqsystem.menu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsystem.menu.entity.MenuConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜单展示配置 Mapper（自建表 t_auto_hltgq_menu_config）。
 */
@Mapper
public interface MenuConfigMapper extends BaseMapper<MenuConfig> {
}
