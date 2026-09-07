package com.gzly.algorithm.core;

/** 选科模式。3+1+2 按首选科目分物理/历史两轨；3+3（海南）无轨道，按选科集合匹配。 */
public enum SubjectMode {

    THREE_ONE_TWO("3+1+2"),
    THREE_THREE("3+3");

    private final String label;

    SubjectMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static SubjectMode fromLabel(String label) {
        if (THREE_THREE.label.equals(label == null ? null : label.trim())) {
            return THREE_THREE;
        }
        return THREE_ONE_TWO;
    }
}
