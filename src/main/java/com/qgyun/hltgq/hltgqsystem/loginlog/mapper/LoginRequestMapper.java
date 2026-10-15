package com.qgyun.hltgq.hltgqsystem.loginlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsystem.loginlog.entity.LoginRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 登录请求（nginx 采集）Mapper。
 */
@Mapper
public interface LoginRequestMapper extends BaseMapper<LoginRequest> {

    /** 未合并的登录请求（按时间升序） */
    @Select("SELECT * FROM \"qixiao-apaas\".\"t_auto_hltgq_login_request\" " +
            "WHERE matched = '0' ORDER BY login_at ASC LIMIT 500")
    List<LoginRequest> selectUnmatched();

    /** 指定时间窗口内未合并的登录请求（用于按每条登录记录就近匹配） */
    @Select("SELECT * FROM \"qixiao-apaas\".\"t_auto_hltgq_login_request\" " +
            "WHERE matched = '0' AND login_at >= #{from} AND login_at <= #{to} LIMIT 100")
    List<LoginRequest> selectNearUnmatched(@Param("from") java.time.LocalDateTime from,
                                           @Param("to") java.time.LocalDateTime to);

    /** 标记已合并 */
    @Update("UPDATE \"qixiao-apaas\".\"t_auto_hltgq_login_request\" SET matched = '1' WHERE id = #{id}")
    int markMatched(@Param("id") String id);
}
