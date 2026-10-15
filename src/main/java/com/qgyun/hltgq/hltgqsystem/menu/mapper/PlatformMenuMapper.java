package com.qgyun.hltgq.hltgqsystem.menu.mapper;

import com.qgyun.hltgq.hltgqsystem.menu.entity.PlatformMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 平台菜单只读 Mapper（t_apaas_sys_menu，corp_code='hltgq'）。
 * <p>注意：项目全局 map-underscore-to-camel-case=false，列别名须带双引号
 * 精确匹配属性名（否则被打回小写导致映射失败）。
 */
@Mapper
public interface PlatformMenuMapper {

    @Select("SELECT id, name, type, category, "
            + "corp_code AS \"corpCode\", app_parent AS \"appParent\", show_order AS \"showOrder\" "
            + "FROM \"qixiao-apaas\".\"t_apaas_sys_menu\" WHERE corp_code = 'hltgq'")
    List<PlatformMenu> selectAll();
}
