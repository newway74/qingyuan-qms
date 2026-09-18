package com.qms.modules.npi.vo;

import com.qms.modules.npi.entity.NpiEval;
import com.qms.modules.npi.entity.NpiEvalItem;
import lombok.Data;

import java.util.List;

@Data
public class EvalDetailVO {

    private NpiEval eval;

    private String supplierName;

    private List<NpiEvalItem> items;
}
