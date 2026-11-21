package com.qingcheng.ordering;

/** Stable wire identifiers and presentation metadata; type never changes date calculations. */
enum AnniversaryType {
    MEETING("meeting", "相识纪念", Marker.HEART),
    RELATIONSHIP("relationship", "恋爱纪念", Marker.HEART),
    WEDDING("wedding", "结婚纪念", Marker.HEART),
    BIRTHDAY("birthday", "生日", Marker.CAKE),
    OTHER("other", "其他日子", Marker.FLAG);

    enum Marker { HEART, CAKE, FLAG }

    private final String id;
    private final String label;
    private final Marker marker;

    AnniversaryType(String id, String label, Marker marker) {
        this.id = id;
        this.label = label;
        this.marker = marker;
    }

    String id() {
        return id;
    }

    String label() {
        return label;
    }

    Marker marker() {
        return marker;
    }

    /** User input must use a known, exact wire identifier. */
    static AnniversaryType fromId(String id) {
        for (AnniversaryType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("请选择有效的纪念日类型");
    }

    /** Older servers omit type; future server types remain readable without guessing from titles. */
    static AnniversaryType fromResponseId(String id) {
        for (AnniversaryType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return OTHER;
    }
}
