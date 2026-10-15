package com.qgyun.hltgq.hltgqsystem.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 企效平台 UC 用户表只读查询（t_apaas_uc_user）。
 * <p>用于：登录鉴权取 login_name；操作日志解析操作人姓名。
 */
@Mapper
public interface ApaasUcUserMapper {

    /**
     * 按用户主键查询登录名（corp_code 限定 hltgq）
     */
    @Select("SELECT login_name FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id = #{id} AND corp_code = 'hltgq'")
    String selectLoginNameById(@Param("id") String id);

    /**
     * 批量按用户主键查询 id / name / login_name（操作人姓名解析用，结果 Map key 为 id）。
     */
    @Select("<script>SELECT id, name, login_name FROM \"qixiao-apaas\".\"t_apaas_uc_user\" " +
            "WHERE id IN " +
            "<foreach collection='ids' item='uid' open='(' separator=',' close=')'>#{uid}</foreach>" +
            "</script>")
    List<Map<String, Object>> selectUsersByIds(@Param("ids") Collection<String> ids);
}
