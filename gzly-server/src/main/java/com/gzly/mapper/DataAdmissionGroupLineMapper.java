package com.gzly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzly.entity.DataAdmissionGroupLine;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DataAdmissionGroupLineMapper extends BaseMapper<DataAdmissionGroupLine> {

    @Select("SELECT DISTINCT year FROM data_admission_group_line WHERE province_code = #{provinceCode} ORDER BY year DESC")
    List<Integer> selectDistinctYears(@Param("provinceCode") String provinceCode);

    @Select("""
            SELECT MAX(year)
            FROM data_admission_group_line
            WHERE province_code = #{provinceCode}
              AND subject_type = #{subjectType}
            """)
    Integer selectLatestYear(@Param("provinceCode") String provinceCode,
                             @Param("subjectType") String subjectType);

    @Select("""
            <script>
            SELECT id, province_code, province_name, year, school_id, university_name,
                   group_code, group_name, subject_type, first_subject_requirement,
                   resubject_requirement, min_score, min_rank, plan_count, batch,
                   rank_source_type, rank_source_note, rank_source_url, rank_source_page_url,
                   source_name, source_url, source_page_url, source_level, parse_method,
                   created_at, updated_at
            FROM data_admission_group_line
            WHERE province_code = #{provinceCode}
              AND subject_type = #{subjectType}
              AND min_rank IS NOT NULL
              AND min_rank &gt; 0
              AND min_rank BETWEEN #{rankLow} AND #{rankHigh}
              AND (
                batch LIKE CONCAT('%', #{batchKeyword}, '%')
                OR batch LIKE '%本科批%'
              )
              AND (
                (source_url IS NOT NULL AND source_url != '')
                OR (source_page_url IS NOT NULL AND source_page_url != '')
              )
              <if test="year != null">
                AND year = #{year}
              </if>
            ORDER BY year DESC, min_rank ASC, university_name ASC, group_code ASC
            LIMIT #{limit}
            </script>
            """)
    List<DataAdmissionGroupLine> selectCandidates(@Param("provinceCode") String provinceCode,
                                                  @Param("year") Integer year,
                                                  @Param("subjectType") String subjectType,
                                                  @Param("rankLow") int rankLow,
                                                  @Param("rankHigh") int rankHigh,
                                                  @Param("batchKeyword") String batchKeyword,
                                                  @Param("limit") int limit);

    @Select("""
            SELECT id, province_code, province_name, year, school_id, university_name,
                   group_code, group_name, subject_type, first_subject_requirement,
                   resubject_requirement, min_score, min_rank, plan_count, batch,
                   rank_source_type, rank_source_note, rank_source_url, rank_source_page_url,
                   source_name, source_url, source_page_url, source_level, parse_method,
                   created_at, updated_at
            FROM data_admission_group_line
            WHERE province_code = #{provinceCode}
              AND school_id = #{schoolId}
              AND group_code = #{groupCode}
              AND subject_type = #{subjectType}
              AND min_score IS NOT NULL
              AND min_score > 0
            ORDER BY year DESC
            LIMIT #{limit}
            """)
    List<DataAdmissionGroupLine> selectRecentHistory(@Param("provinceCode") String provinceCode,
                                                     @Param("schoolId") String schoolId,
                                                     @Param("groupCode") String groupCode,
                                                     @Param("subjectType") String subjectType,
                                                     @Param("limit") int limit);

    /**
     * selectRecentHistory 的批量版：一次取一组 (school_id, group_code) 对的近 N 年历史线，
     * 每组按年份降序限 perGroupLimit 条（窗口函数），替代生成链路里每条志愿一次的 N+1 查询。
     * pairs 元素为含 schoolId / groupCode 键的 Map。
     */
    @Select("""
            <script>
            SELECT * FROM (
              SELECT id, province_code, province_name, year, school_id, university_name,
                     group_code, group_name, subject_type, first_subject_requirement,
                     resubject_requirement, min_score, min_rank, plan_count, batch,
                     rank_source_type, rank_source_note, rank_source_url, rank_source_page_url,
                     source_name, source_url, source_page_url, source_level, parse_method,
                     created_at, updated_at,
                     ROW_NUMBER() OVER (PARTITION BY school_id, group_code ORDER BY year DESC) AS rn
              FROM data_admission_group_line
              WHERE province_code = #{provinceCode}
                AND subject_type = #{subjectType}
                AND min_score IS NOT NULL
                AND min_score &gt; 0
                AND (school_id, group_code) IN
                <foreach collection="pairs" item="p" open="(" separator="," close=")">(#{p.schoolId}, #{p.groupCode})</foreach>
            ) t
            WHERE t.rn &lt;= #{perGroupLimit}
            ORDER BY t.school_id, t.group_code, t.year DESC
            </script>
            """)
    List<DataAdmissionGroupLine> selectRecentHistoryBatch(@Param("provinceCode") String provinceCode,
                                                          @Param("subjectType") String subjectType,
                                                          @Param("pairs") List<java.util.Map<String, String>> pairs,
                                                          @Param("perGroupLimit") int perGroupLimit);

    @Select("""
            SELECT COUNT(DISTINCT CONCAT(school_id, '#', group_code, '#', subject_type))
            FROM data_admission_group_line
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND subject_type = #{subjectType}
              AND min_score IS NOT NULL
              AND min_score > 0
              AND min_rank IS NOT NULL
              AND min_rank > 0
              AND (
                batch LIKE CONCAT('%', #{batchKeyword}, '%')
                OR batch LIKE '%本科批%'
              )
            """)
    Long countDistinctGroups(@Param("provinceCode") String provinceCode,
                             @Param("year") int year,
                             @Param("subjectType") String subjectType,
                             @Param("batchKeyword") String batchKeyword);

    @Select("""
            SELECT COUNT(*)
            FROM data_admission_group_line
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND (
                batch LIKE CONCAT('%', #{batchKeyword}, '%')
                OR batch LIKE '%本科批%'
              )
            """)
    Long countRowsByBatch(@Param("provinceCode") String provinceCode,
                          @Param("year") int year,
                          @Param("batchKeyword") String batchKeyword);

    @Select("""
            SELECT COUNT(DISTINCT CONCAT(school_id, '#', group_code, '#', subject_type))
            FROM data_admission_group_line
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND subject_type = #{subjectType}
              AND min_score IS NOT NULL
              AND min_score > 0
              AND min_rank IS NOT NULL
              AND min_rank > 0
              AND rank_source_type = #{rankSourceType}
              AND (
                batch LIKE CONCAT('%', #{batchKeyword}, '%')
                OR batch LIKE '%本科批%'
              )
            """)
    Long countDistinctGroupsByRankSource(@Param("provinceCode") String provinceCode,
                                         @Param("year") int year,
                                         @Param("subjectType") String subjectType,
                                         @Param("batchKeyword") String batchKeyword,
                                         @Param("rankSourceType") String rankSourceType);

    @Select("""
            SELECT COUNT(DISTINCT CONCAT(school_id, '#', group_code, '#', subject_type))
            FROM data_admission_group_line
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND subject_type = #{subjectType}
              AND min_score IS NOT NULL
              AND min_score > 0
              AND source_level = #{sourceLevel}
              AND (
                batch LIKE CONCAT('%', #{batchKeyword}, '%')
                OR batch LIKE '%本科批%'
              )
            """)
    Long countDistinctGroupsBySourceLevel(@Param("provinceCode") String provinceCode,
                                          @Param("year") int year,
                                          @Param("subjectType") String subjectType,
                                          @Param("batchKeyword") String batchKeyword,
                                          @Param("sourceLevel") String sourceLevel);
}
