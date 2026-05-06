package com.gzly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzly.entity.DataScoreRank;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DataScoreRankMapper extends BaseMapper<DataScoreRank> {

    @Select("""
            SELECT MAX(year)
            FROM data_score_rank
            WHERE province_code = #{provinceCode}
              AND subject_type = #{subjectType}
            """)
    Integer selectLatestYear(@Param("provinceCode") String provinceCode,
                             @Param("subjectType") String subjectType);

    @Select("""
            SELECT id, province_code, province_name, year, subject_type, score, score_label,
                   segment_count, cumulative_count, cumulative_rate,
                   rank_low, rank_high, source_name, source_url, source_page_url,
                   source_file, parse_method, created_at, updated_at
            FROM data_score_rank
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND subject_type = #{subjectType}
              AND score <= #{score}
            ORDER BY score DESC
            LIMIT 1
            """)
    DataScoreRank selectNearestAtOrBelow(@Param("provinceCode") String provinceCode,
                                         @Param("year") int year,
                                         @Param("subjectType") String subjectType,
                                         @Param("score") int score);
}
