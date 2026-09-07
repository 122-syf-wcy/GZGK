package com.gzly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzly.entity.MajorRequirementGz;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MajorRequirementGzMapper extends BaseMapper<MajorRequirementGz> {

    /**
     * 官方选科要求单条模糊查询。主生成链路已改为按学校集合一次性预取 + 内存匹配
     * （VolunteerService.prefetchRequirements / pickOfficialRequirement），本方法保留给
     * 零散调用与预取失败时的逐条回退。
     */
    @Select("""
            SELECT id, year, school_id, university_name, major_id, major_name,
                   subject_type, first_subject_requirement, resubject_requirement,
                   requirement_text, source_name, source_url, source_file,
                   created_at, updated_at
            FROM data_major_requirement_gz
            WHERE school_id = #{schoolId}
              AND subject_type = #{subjectType}
              AND (
                    major_name = #{majorName}
                    OR (
                        CHAR_LENGTH(#{majorCore}) >= 2
                        AND (
                            major_name = #{majorCore}
                            OR major_name LIKE CONCAT(#{majorCore}, '（%')
                            OR major_name LIKE CONCAT(#{majorCore}, '(%')
                            OR #{majorName} LIKE CONCAT(major_name, '（%')
                            OR #{majorName} LIKE CONCAT(major_name, '(%')
                        )
                    )
                  )
            ORDER BY year DESC,
                     CASE
                         WHEN major_name = #{majorName} THEN 0
                         WHEN major_name = #{majorCore} THEN 1
                         ELSE 2
                     END,
                     CHAR_LENGTH(major_name) DESC
            LIMIT 1
            """)
    MajorRequirementGz selectLatestByMajor(@Param("schoolId") String schoolId,
                                           @Param("majorName") String majorName,
                                           @Param("subjectType") String subjectType,
                                           @Param("majorCore") String majorCore);
}
