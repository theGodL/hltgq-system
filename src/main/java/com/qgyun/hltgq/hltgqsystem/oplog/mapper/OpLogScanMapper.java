package com.qgyun.hltgq.hltgqsystem.oplog.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 被监控业务表的只读扫描 Mapper。
 * <p>表名来自受信配置（{@code oplog-tables.json}），使用 {@code ${}} 拼接；
 * 行数据以 {@code row_to_json} 输出为文本，避免 Kingbase 自有时间类型（OID 7954）经 JDBC 映射报错。
 * <p>水位线比较使用 {@code CAST(#{watermark} AS timestamp)}（可直接走 updated_at/created_at 索引），
 * 水位线参数为 "yyyy-MM-dd HH:mm:ss" 字符串。
 */
@Mapper
public interface OpLogScanMapper {

    /** 增量取自水位线之后有创建或更新的行（JSON 文本），支持租户过滤 */
    @Select("<script>SELECT row_to_json(t) AS j FROM \"qixiao-apaas\".${tableName} t " +
            "WHERE (t.created_at &gt; CAST(#{watermark} AS timestamp) " +
            "   OR t.updated_at &gt; CAST(#{watermark} AS timestamp)) " +
            "<if test='corpFilter != null'> AND t.corp_code = #{corpFilter} </if>" +
            "</script>")
    List<String> selectChangedRows(@Param("tableName") String tableName,
                                   @Param("watermark") String watermark,
                                   @Param("corpFilter") String corpFilter);

    /** 当前表全部业务 ID（删除识别用），支持租户过滤 */
    @Select("<script>SELECT id FROM \"qixiao-apaas\".${tableName} t " +
            "<if test='corpFilter != null'> WHERE t.corp_code = #{corpFilter} </if>" +
            "</script>")
    List<String> selectAllIds(@Param("tableName") String tableName,
                              @Param("corpFilter") String corpFilter);
}
