package com.qms.modules.process.vo;

import com.qms.modules.process.entity.ProcessDef;
import com.qms.modules.process.entity.ProcessNode;
import lombok.Data;

import java.util.List;

@Data
public class ProcessDefDetailVO {

    private ProcessDef processDef;

    private List<ProcessNode> nodes;
}
