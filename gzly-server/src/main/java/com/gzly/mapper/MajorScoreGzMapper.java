package com.gzly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzly.entity.MajorScoreGz;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface MajorScoreGzMapper extends BaseMapper<MajorScoreGz> {

    @Select("SELECT DISTINCT year FROM data_major_score_gz ORDER BY year DESC")
    List<Integer> selectDistinctYears();

    @Select("SELECT DISTINCT batch FROM data_major_score_gz WHERE year = #{year} ORDER BY batch")
    List<String> selectDistinctBatches(Integer year);

    /**
     * 热门专业TOP N — 基于当前科类最新年份真实录取数据
     * 按大类归并，统计开设院校数、平均分、平均位次
     */
    @Select("""
        SELECT category AS name, school_count AS schoolCount,
               avg_score AS avgScore, avg_rank AS avgRank
        FROM (
          SELECT
            CASE
              WHEN major_name LIKE '%计算机%' THEN '计算机类'
              WHEN major_name LIKE '%软件%' THEN '软件工程'
              WHEN major_name LIKE '%人工智能%' THEN '人工智能'
              WHEN major_name LIKE '%数据科学%' OR major_name LIKE '%大数据%' THEN '数据科学与大数据'
              WHEN major_name LIKE '%电子信息%' THEN '电子信息类'
              WHEN major_name LIKE '%电气%' THEN '电气工程'
              WHEN major_name LIKE '%通信%' THEN '通信工程'
              WHEN major_name LIKE '%自动化%' THEN '自动化'
              WHEN major_name LIKE '%机械%' THEN '机械类'
              WHEN major_name LIKE '%临床医学%' THEN '临床医学'
              WHEN major_name LIKE '%口腔%' THEN '口腔医学'
              WHEN major_name LIKE '%护理%' THEN '护理学'
              WHEN major_name LIKE '%药学%' THEN '药学'
              WHEN major_name LIKE '%法学%' OR major_name LIKE '%法律%' THEN '法学'
              WHEN major_name LIKE '%金融%' THEN '金融学类'
              WHEN major_name LIKE '%会计%' OR major_name LIKE '%审计%' THEN '会计/审计'
              WHEN major_name LIKE '%经济%' AND major_name NOT LIKE '%金融%' THEN '经济学'
              WHEN major_name LIKE '%英语%' OR major_name LIKE '%翻译%' THEN '英语/翻译'
              WHEN major_name LIKE '%教育%' AND major_name NOT LIKE '%体育%' THEN '教育类'
              WHEN major_name LIKE '%汉语%' OR major_name LIKE '%中国语言%' THEN '汉语言文学'
              WHEN major_name LIKE '%新闻%' OR major_name LIKE '%传播%' THEN '新闻传播'
              WHEN major_name LIKE '%数学%' THEN '数学类'
              WHEN major_name LIKE '%土木%' OR major_name LIKE '%建筑%' THEN '土木/建筑'
              WHEN major_name LIKE '%工商管理%' OR major_name LIKE '%市场营销%' THEN '工商管理'
              ELSE NULL
            END AS category,
            COUNT(DISTINCT school_id) AS school_count,
            ROUND(AVG(min_score),0) AS avg_score,
            ROUND(AVG(min_rank),0) AS avg_rank
          FROM data_major_score_gz
          WHERE year = (
              SELECT MAX(year)
              FROM data_major_score_gz
              WHERE subject_type = #{subjectType}
                AND batch LIKE '%本科%'
                AND min_score > 0
                AND min_rank > 0
          )
            AND subject_type = #{subjectType}
            AND batch LIKE '%本科%' AND min_score > 0 AND min_rank > 0
          GROUP BY category
          HAVING category IS NOT NULL
        ) t
        ORDER BY school_count DESC
        LIMIT #{limit}
        """)
    List<Map<String, Object>> selectHotMajors(@Param("subjectType") String subjectType,
                                               @Param("limit") int limit);
}
