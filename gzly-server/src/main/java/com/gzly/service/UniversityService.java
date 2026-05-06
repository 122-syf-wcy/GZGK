package com.gzly.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzly.common.PageResult;
import com.gzly.entity.University;
import com.gzly.mapper.UniversityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UniversityService {

    private final UniversityMapper universityMapper;

    private static final Map<String, List<String>> REGION_PROVINCES = Map.of(
            "华北", List.of("北京", "天津", "河北", "山西", "内蒙古"),
            "东北", List.of("辽宁", "吉林", "黑龙江"),
            "华东", List.of("上海", "江苏", "浙江", "安徽", "福建", "江西", "山东"),
            "华中", List.of("河南", "湖北", "湖南"),
            "华南", List.of("广东", "广西", "海南"),
            "西南", List.of("重庆", "四川", "贵州", "云南", "西藏"),
            "西北", List.of("陕西", "甘肃", "青海", "宁夏", "新疆"),
            "港澳台", List.of("香港", "澳门", "台湾")
    );

    @Cacheable(value = "universities", key = "#keyword + '_' + #province + '_' + #region + '_' + #tag + '_' + #page + '_' + #pageSize",
               unless = "#result.items.isEmpty()")
    public PageResult<University> list(String keyword, String province, String region, String tag, int page, int pageSize) {
        LambdaQueryWrapper<University> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.isNotBlank(keyword)) {
            wrapper.like(University::getName, keyword);
        }
        if (StringUtils.isNotBlank(province)) {
            wrapper.eq(University::getProvince, province);
        }
        if (StringUtils.isNotBlank(region)) {
            List<String> provinces = REGION_PROVINCES.get(region);
            if (provinces != null && !provinces.isEmpty()) {
                wrapper.in(University::getProvince, provinces);
            }
        }
        if (StringUtils.isNotBlank(tag)) {
            switch (tag) {
                case "985" -> wrapper.eq(University::getF985, 1);
                case "211" -> wrapper.eq(University::getF211, 1);
                case "双一流" -> wrapper.eq(University::getDualClass, 1);
                case "公办" -> wrapper.eq(University::getNatureName, "公办");
                case "民办" -> wrapper.eq(University::getNatureName, "民办");
            }
        }
        wrapper.orderByAsc(University::getId);

        Page<University> p = universityMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return PageResult.of(p.getRecords(), p.getTotal(), page, pageSize);
    }

    @Cacheable(value = "universityDetail", key = "#id", unless = "#result == null")
    public University getById(Long id) {
        return universityMapper.selectById(id);
    }

    @Cacheable(value = "universityBySchoolId", key = "#schoolId", unless = "#result == null")
    public University getBySchoolId(String schoolId) {
        return universityMapper.selectOne(
                new LambdaQueryWrapper<University>().eq(University::getSchoolId, schoolId));
    }
}
