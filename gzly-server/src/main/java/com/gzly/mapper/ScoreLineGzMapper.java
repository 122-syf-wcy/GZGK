package com.gzly.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzly.entity.ScoreLineGz;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ScoreLineGzMapper extends BaseMapper<ScoreLineGz> {

    @Select("SELECT DISTINCT year FROM data_score_line_gz ORDER BY year DESC")
    List<Integer> selectDistinctYears();
}
