package com.qgyun.hltgq.hltgqsystem.loginlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsystem.loginlog.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 登录日志 Mapper。
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {

    /** 按会话标识统计（去重） */
    @Select("SELECT COUNT(*) FROM \"qixiao-apaas\".\"t_auto_hltgq_login_log\" WHERE session_id = #{sessionId}")
    int countBySessionId(@Param("sessionId") String sessionId);

    /** 按会话标识取一条 */
    @Select("SELECT * FROM \"qixiao-apaas\".\"t_auto_hltgq_login_log\" WHERE session_id = #{sessionId} LIMIT 1")
    LoginLog selectBySessionId(@Param("sessionId") String sessionId);

    /** 回填 IP/UA/终端/地点（仅当 ip 为空时） */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_login_log\" " +
            "SET ip = #{ip}, user_agent = #{userAgent}, " +
            "    terminal = COALESCE(NULLIF(terminal, ''), #{terminal}), " +
            "    region = COALESCE(NULLIF(region, ''), #{region}) " +
            "WHERE session_id = #{sessionId} AND (ip IS NULL OR ip = '')")
    int enrich(@Param("sessionId") String sessionId, @Param("ip") String ip,
               @Param("userAgent") String userAgent, @Param("terminal") String terminal,
               @Param("region") String region);

    /** 缺 IP 的登录记录（用于与登录请求按时间合并） */
    @Select("SELECT * FROM \"qixiao-apaas\".\"t_auto_hltgq_login_log\" " +
            "WHERE (ip IS NULL OR ip = '') AND login_at IS NOT NULL ORDER BY login_at ASC LIMIT 5000")
    java.util.List<LoginLog> selectMissingIp();

    /** 按主键回填 IP/UA/终端/地点 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_login_log\" " +
            "SET ip = #{ip}, " +
            "    user_agent = COALESCE(NULLIF(user_agent, ''), #{userAgent}), " +
            "    terminal = COALESCE(NULLIF(terminal, ''), #{terminal}), " +
            "    region = COALESCE(NULLIF(region, ''), #{region}) " +
            "WHERE id = #{id}")
    int updateIpById(@Param("id") String id, @Param("ip") String ip,
                     @Param("userAgent") String userAgent, @Param("terminal") String terminal,
                     @Param("region") String region);

    /** 缺终端但有 UA 的记录（用于按 UA 推断终端兜底） */
    @Select("SELECT * FROM \"qixiao-apaas\".\"t_auto_hltgq_login_log\" " +
            "WHERE (terminal IS NULL OR terminal = '') AND user_agent IS NOT NULL AND user_agent <> '' LIMIT 2000")
    java.util.List<LoginLog> selectMissingTerminal();

    /** 按主键回填终端 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_login_log\" SET terminal = #{terminal} WHERE id = #{id}")
    int updateTerminal(@Param("id") String id, @Param("terminal") String terminal);

    /** 按主键回填登录地点 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_login_log\" SET region = #{region} WHERE id = #{id}")
    int updateRegion(@Param("id") String id, @Param("region") String region);

    /** 认证日志事件补全（已存在同会话时补 operator/终端/方式/时间） */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_login_log\" " +
            "SET operator = COALESCE(NULLIF(operator, ''), #{operator}), " +
            "    operator_name = COALESCE(NULLIF(operator_name, ''), #{operatorName}), " +
            "    login_type = COALESCE(NULLIF(login_type, ''), #{loginType}), " +
            "    terminal = COALESCE(NULLIF(terminal, ''), #{terminal}), " +
            "    login_at = COALESCE(#{loginAt}, login_at) " +
            "WHERE session_id = #{sessionId}")
    int enrichAuth(@Param("sessionId") String sessionId, @Param("operator") String operator,
                   @Param("operatorName") String operatorName, @Param("loginType") String loginType,
                   @Param("terminal") String terminal, @Param("loginAt") java.time.LocalDateTime loginAt);
}
