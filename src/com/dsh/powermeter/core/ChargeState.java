package com.dsh.powermeter.core;

/** PRD 2.2 状态枚举。 */
public enum ChargeState {
    UNPLUGGED("未充电"),
    CHARGING("充电中"),
    PAUSED("已暂停"),
    FULL("已充满"),
    ABNORMAL("充电异常"),
    UNAVAILABLE("读数不可用");

    public final String label;

    ChargeState(String label) {
        this.label = label;
    }
}
