package com.gzly.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VolunteerResultProfessionalFieldsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void professionalGroupItemSerializesProfessionalVolunteerTableFields() throws Exception {
        VolunteerService.ProfessionalMajorDetail major = new VolunteerService.ProfessionalMajorDetail();
        major.setMajorCode("080901");
        major.setMajorName("计算机科学与技术");
        major.setMajorDescription("选科：物理+化学；含专业目录备注");
        major.setDuration("4年");
        major.setTuition("5200元/年");
        major.setPlanCount(8);
        major.setSourceYear("2025");
        major.setSourceStatus("官方结构化数据");

        VolunteerService.VolunteerItem item = new VolunteerService.VolunteerItem();
        item.setIndex(12);
        item.setProvinceCode("HB");
        item.setSchoolCode("10486");
        item.setUniversityName("武汉示例大学");
        item.setGroupCode("C101");
        item.setMajorCode("C101");
        item.setMajorName("C101专业组");
        item.setMajorDescription("院校专业组；选科：物理+化学");
        item.setDuration("--");
        item.setTuition("--");
        item.setPlanCount(42);
        item.setLocked(Boolean.FALSE);
        item.setSourceYear("2025");
        item.setSourceStatus("历史院校专业组数据");
        item.setProfessionalMajors(List.of(major));

        JsonNode json = objectMapper.valueToTree(item);

        assertThat(json.get("schoolCode").asText()).isEqualTo("10486");
        assertThat(json.get("groupCode").asText()).isEqualTo("C101");
        assertThat(json.get("majorCode").asText()).isEqualTo("C101");
        assertThat(json.get("planCount").asInt()).isEqualTo(42);
        assertThat(json.get("locked").asBoolean()).isFalse();
        JsonNode majors = json.get("professionalMajors");
        assertThat(majors).hasSize(1);
        assertThat(majors.get(0).get("majorCode").asText()).isEqualTo("080901");
        assertThat(majors.get(0).get("duration").asText()).isEqualTo("4年");
        assertThat(majors.get(0).get("tuition").asText()).isEqualTo("5200元/年");
        assertThat(majors.get(0).get("planCount").asInt()).isEqualTo(8);
    }
}
