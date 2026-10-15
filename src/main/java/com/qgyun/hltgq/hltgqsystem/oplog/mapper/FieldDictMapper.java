package com.qgyun.hltgq.hltgqsystem.oplog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 字段中文字典 Mapper：低代码平台元数据。
 * <p>字段中文名：t_lcode_model_field（field_key → field_title）；
 * 枚举选项：t_lcode_option_group（config_json.options = [{label,value}]）。
 */
@Mapper
public interface FieldDictMapper {

    @Select("<script>SELECT f.field_key AS k, f.field_title AS t, f.config_json AS c " +
            "FROM \"qixiao-apaas\".\"t_lcode_model_field\" f " +
            "WHERE f.corp_code = 'hltgq' AND f.fun_id IN (" +
            "  SELECT m.id FROM \"qixiao-apaas\".\"t_lcode_model\" m " +
            "  WHERE m.corp_code = 'hltgq' AND (" +
            "    m.id IN (SELECT model_id FROM \"qixiao-apaas\".\"t_lcode_model_table\" WHERE name = #{tableName}) " +
            "    OR m.fun_code IN <foreach collection='codes' item='c' open='(' separator=',' close=')'>#{c}</foreach>" +
            "  )" +
            ") AND f.field_title IS NOT NULL AND f.field_title &lt;&gt; ''" +
            "</script>")
    List<Map<String, Object>> selectFieldDict(@Param("tableName") String tableName,
                                              @Param("codes") Collection<String> codes);

    /** 枚举选项组（config_json.options） */
    @Select("SELECT config_json FROM \"qixiao-apaas\".\"t_lcode_option_group\" WHERE id = #{id}")
    String selectOptionGroup(@Param("id") String id);
}
