package com.gzly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzly.entity.DataAdmissionGroupPlan;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface DataAdmissionGroupPlanMapper extends BaseMapper<DataAdmissionGroupPlan> {

    @Select("""
            SELECT id, province_code, province_name, year, school_id, university_name,
                   group_code, group_name, major_code, major_name, subject_type,
                   first_subject_requirement, resubject_requirement, plan_count, tuition,
                   study_years, batch, source_name, source_url, source_page_url,
                   source_level, parse_method, created_at, updated_at
            FROM data_admission_group_plan
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND school_id = #{schoolId}
              AND group_code = #{groupCode}
              AND subject_type = #{subjectType}
            ORDER BY major_code ASC, id ASC
            LIMIT #{limit}
            """)
    List<DataAdmissionGroupPlan> selectGroupMajors(@Param("provinceCode") String provinceCode,
                                                   @Param("year") int year,
                                                   @Param("schoolId") String schoolId,
                                                   @Param("groupCode") String groupCode,
                                                   @Param("subjectType") String subjectType,
                                                   @Param("limit") int limit);

    /**
     * selectGroupMajors 的批量版：一次取一组 (school_id, group_code) 对的组内专业，
     * 每组展示上限由调用方在内存截断。pairs 元素为含 schoolId / groupCode 键的 Map。
     */
    @Select("""
            <script>
            SELECT id, province_code, province_name, year, school_id, university_name,
                   group_code, group_name, major_code, major_name, subject_type,
                   first_subject_requirement, resubject_requirement, plan_count, tuition,
                   study_years, batch, source_name, source_url, source_page_url,
                   source_level, parse_method, created_at, updated_at
            FROM data_admission_group_plan
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND subject_type = #{subjectType}
              AND (school_id, group_code) IN
              <foreach collection="pairs" item="p" open="(" separator="," close=")">(#{p.schoolId}, #{p.groupCode})</foreach>
            ORDER BY school_id, group_code, major_code ASC, id ASC
            </script>
            """)
    List<DataAdmissionGroupPlan> selectGroupMajorsBatch(@Param("provinceCode") String provinceCode,
                                                        @Param("year") int year,
                                                        @Param("subjectType") String subjectType,
                                                        @Param("pairs") List<java.util.Map<String, String>> pairs);

    @Select("""
            SELECT COUNT(DISTINCT CONCAT(school_id, '#', group_code, '#', subject_type))
            FROM data_admission_group_plan
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND subject_type = #{subjectType}
              AND batch LIKE CONCAT('%', #{batchKeyword}, '%')
              AND major_name IS NOT NULL
              AND major_name != ''
            """)
    Long countDistinctGroups(@Param("provinceCode") String provinceCode,
                             @Param("year") int year,
                             @Param("subjectType") String subjectType,
                             @Param("batchKeyword") String batchKeyword);

    @Select("""
            SELECT COUNT(*)
            FROM data_admission_group_plan
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND batch LIKE CONCAT('%', #{batchKeyword}, '%')
              AND major_name IS NOT NULL
              AND major_name != ''
            """)
    Long countRowsByBatch(@Param("provinceCode") String provinceCode,
                          @Param("year") int year,
                          @Param("batchKeyword") String batchKeyword);

    @Select("""
            SELECT COUNT(DISTINCT CONCAT(school_id, '#', group_code, '#', subject_type))
            FROM data_admission_group_plan
            WHERE province_code = #{provinceCode}
              AND year = #{year}
              AND subject_type = #{subjectType}
              AND source_level = #{sourceLevel}
              AND batch LIKE CONCAT('%', #{batchKeyword}, '%')
              AND major_name IS NOT NULL
              AND major_name != ''
            """)
    Long countDistinctGroupsBySourceLevel(@Param("provinceCode") String provinceCode,
                                          @Param("year") int year,
                                          @Param("subjectType") String subjectType,
                                          @Param("batchKeyword") String batchKeyword,
                                          @Param("sourceLevel") String sourceLevel);
}
