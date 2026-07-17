package com.gzly.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 未上线地区白名单注册表。
 *
 * <p>本注册表只服务“尚未接入完整志愿推荐”的地区，<b>显式排除已上线 8 省</b>
 * （GZ/SC/AH/HB/GX/HI/YN/HA/CQ/GS/XJ）。已上线地区的志愿推荐 / 分数线 / 策略建议
 * 继续走 {@link ProvincePolicyService} 链路，本类不参与，也不复用其 normalize 逻辑。</p>
 *
 * <p>注意：河南 = HA（已上线），陕西 = SN，湖南 = HN，湖北 = HB（已上线），河北 = HE。</p>
 */
public final class AiQaRegionRegistry {

    private AiQaRegionRegistry() {
    }

    /** 已上线地区，必须排除未上线地区列表。 */
    public static final Set<String> LAUNCHED_REGIONS = Set.of(
            "GZ", "SC", "AH", "HB", "GX", "HI", "YN", "HA", "CQ", "GS", "XJ");

    /** 未上线地区代码 -> 名称（全国其余省/市/区，排除已上线 8 省与港澳台）。 */
    public static final Map<String, String> UNLAUNCHED_REGIONS;

    static {
        Map<String, String> regions = new LinkedHashMap<>();
        regions.put("BJ", "北京");
        regions.put("TJ", "天津");
        regions.put("HE", "河北");
        regions.put("SX", "山西");
        regions.put("NM", "内蒙古");
        regions.put("LN", "辽宁");
        regions.put("JL", "吉林");
        regions.put("HLJ", "黑龙江");
        regions.put("SH", "上海");
        regions.put("JS", "江苏");
        regions.put("ZJ", "浙江");
        regions.put("FJ", "福建");
        regions.put("JX", "江西");
        regions.put("SD", "山东");
        regions.put("HN", "湖南");
        regions.put("GD", "广东");
        regions.put("XZ", "西藏");
        regions.put("SN", "陕西");
        regions.put("QH", "青海");
        regions.put("NX", "宁夏");
        UNLAUNCHED_REGIONS = Collections.unmodifiableMap(regions);
    }

    /** 规范化地区代码：去空格、转大写。 */
    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /** 是否为已上线 8 省。 */
    public static boolean isLaunched(String code) {
        return LAUNCHED_REGIONS.contains(normalize(code));
    }

    /** 是否为受支持的未上线地区。 */
    public static boolean isUnlaunched(String code) {
        return UNLAUNCHED_REGIONS.containsKey(normalize(code));
    }

    /** 取地区名称；非未上线地区返回空串。 */
    public static String nameOf(String code) {
        return UNLAUNCHED_REGIONS.getOrDefault(normalize(code), "");
    }
}
