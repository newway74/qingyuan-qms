package com.qms.modules.npi.vo;

import lombok.Data;

/** 上市/阶段闸门检查结果。 */
@Data
public class GateStatus {

    /** 是否满足 */
    private boolean ok;

    /** 闸门名称 */
    private String name;

    /** 说明（未满足时给出原因） */
    private String detail;

    public GateStatus() {
    }

    public GateStatus(String name, boolean ok, String detail) {
        this.name = name;
        this.ok = ok;
        this.detail = detail;
    }
}
