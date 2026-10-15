package com.qgyun.hltgq.hltgqsystem.oplog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qgyun.hltgq.hltgqsystem.oplog.entity.OpLogSnapshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 操作日志行快照 Mapper：提供 before 快照读取、upsert 与删除对比用清单。
 */
@Mapper
public interface OpLogSnapshotMapper extends BaseMapper<OpLogSnapshot> {

    /** 读取某行的上一次快照 JSON（无则 null，用于 UPDATE 的 before） */
    @Select("SELECT row_json FROM \"qixiao-apaas\".\"t_auto_hltgq_op_log_snapshot\" " +
            "WHERE table_name = #{tableName} AND biz_id = #{bizId}")
    String selectRowJson(@Param("tableName") String tableName, @Param("bizId") String bizId);

    /** 某表全部已知行（biz_id + row_json），用于删除识别 */
    @Select("SELECT biz_id AS biz_id, row_json AS row_json FROM \"qixiao-apaas\".\"t_auto_hltgq_op_log_snapshot\" " +
            "WHERE table_name = #{tableName}")
    List<Map<String, Object>> selectByTable(@Param("tableName") String tableName);

    /** upsert 行快照（table_name + biz_id 唯一） */
    @Insert("INSERT INTO \"qixiao-apaas\".\"t_auto_hltgq_op_log_snapshot\"" +
            "(id, table_name, biz_id, row_json, seen_at) " +
            "VALUES(#{id}, #{tableName}, #{bizId}, #{rowJson}, now()) " +
            "ON CONFLICT (table_name, biz_id) " +
            "DO UPDATE SET row_json = EXCLUDED.row_json, seen_at = EXCLUDED.seen_at")
    int upsertSnapshot(@Param("id") String id, @Param("tableName") String tableName,
                       @Param("bizId") String bizId, @Param("rowJson") String rowJson);

    /** 删除某行快照（确认删除后） */
    @Delete("DELETE FROM \"qixiao-apaas\".\"t_auto_hltgq_op_log_snapshot\" " +
            "WHERE table_name = #{tableName} AND biz_id = #{bizId}")
    int deleteByBiz(@Param("tableName") String tableName, @Param("bizId") String bizId);
}
