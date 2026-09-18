/** 阶段2 主数据/标准/流程的前端常量（与后端字典、枚举保持一致） */

export const SUPPLIER_STATUS = [
  { value: 'QUALIFIED', label: '合格', color: 'green' },
  { value: 'CONTROLLED', label: '受控', color: 'orange' },
  { value: 'DISABLED', label: '禁用', color: 'red' },
];

export const QUALITY_STATUS = [
  { value: 'NORMAL', label: '正常', color: 'green' },
  { value: 'CONTROLLED', label: '受控', color: 'orange' },
  { value: 'FROZEN', label: '暂停采购销售', color: 'gold' },
  { value: 'DISABLED', label: '淘汰', color: 'red' },
];

export const PACKAGE_FORMS = ['袋装', '罐装', '盒装'];

export const CONFIG_STATUS = [
  { value: 'DRAFT', label: '草稿', color: 'default' },
  { value: 'REVIEWING', label: '评审中', color: 'gold' },
  { value: 'PUBLISHED', label: '已发布', color: 'green' },
  { value: 'ARCHIVED', label: '已归档', color: 'gray' },
];

export const ITEM_GROUPS = [
  { value: 'SENSORY', label: '感官指标' },
  { value: 'PACKAGE_LABEL', label: '包装与标签标识' },
  { value: 'NET_CONTENT', label: '净含量' },
  { value: 'PHYSICO', label: '理化指标' },
  { value: 'MICRO', label: '微生物' },
  { value: 'CERT_DOC', label: '资质与外检报告' },
];

export const RESULT_TYPES = [
  { value: 'QUALITATIVE', label: '定性' },
  { value: 'QUANTITATIVE', label: '定量' },
  { value: 'DOCUMENT', label: '资料上传' },
];

export const DEFECT_LEVELS = [
  { value: 'A', label: '严重(A)', color: 'red' },
  { value: 'B', label: '主要(B)', color: 'orange' },
  { value: 'C', label: '次要(C)', color: 'blue' },
];

export const PROCESS_NODES = [
  { value: 'SAMPLING', label: 'SAMPLING 抽样登记' },
  { value: 'RECEIVE', label: 'RECEIVE 收样登记' },
  { value: 'ASSIGN', label: 'ASSIGN 任务分配' },
  { value: 'INSPECT', label: 'INSPECT 逐项检验' },
  { value: 'REVIEW', label: 'REVIEW 复核签名' },
  { value: 'JUDGE', label: 'JUDGE 综合判定' },
  { value: 'DEFECT', label: 'DEFECT 不合格处置' },
  { value: 'RECHECK', label: 'RECHECK 整改复检' },
  { value: 'ARCHIVE', label: 'ARCHIVE 报告归档' },
];

export const PROCESS_ROLES = [
  { value: 'SAMPLER', label: '抽样/收样员 SAMPLER' },
  { value: 'INSPECTOR', label: '检验员 INSPECTOR' },
  { value: 'REVIEWER', label: '复核判定人 REVIEWER' },
  { value: 'QA_MANAGER', label: '质量主管 QA_MANAGER' },
  { value: 'ADMIN', label: '管理员 ADMIN' },
];

export const CALENDAR_TYPES = [
  { value: 'NATURAL', label: '自然时间' },
  { value: 'WORKDAY', label: '工作日历(预留)' },
];

// ---------------- 阶段3：抽样/样品/检验/报告 ----------------

export const SAMPLING_SOURCES = [
  { value: 'NEW_ADMISSION', label: '新品准入' },
  { value: 'INCOMING_BATCH', label: '来货批检' },
  { value: 'PERIODIC', label: '在售周期抽检' },
  { value: 'COMPLAINT', label: '客诉专项' },
  { value: 'FLYING', label: '飞行抽检' },
];

export const SAMPLING_STATUS = [
  { value: 'DRAFT', label: '草稿', color: 'default' },
  { value: 'PENDING_RECEIVE', label: '待收样', color: 'processing' },
  { value: 'RECEIVED', label: '已收样', color: 'cyan' },
  { value: 'ARCHIVED', label: '已归档', color: 'green' },
  { value: 'CANCELLED', label: '已撤销', color: 'red' },
];

export const SAMPLE_TYPES = [
  { value: 'INSPECTION', label: '检验样', color: 'blue' },
  { value: 'RETAIN', label: '留样', color: 'purple' },
  { value: 'BACKUP', label: '备样', color: 'default' },
];

export const SAMPLE_STATUS = [
  { value: 'PENDING_RECEIVE', label: '待收样', color: 'default' },
  { value: 'IN_INSPECTION', label: '在检', color: 'processing' },
  { value: 'RETAINING', label: '留样中', color: 'purple' },
  { value: 'DISPOSED', label: '已处置', color: 'red' },
];

export const TASK_STATUS = [
  { value: 'PENDING_ASSIGN', label: '待分配', color: 'default' },
  { value: 'PENDING_INSPECT', label: '待检', color: 'gold' },
  { value: 'INSPECTING', label: '检验中', color: 'processing' },
  { value: 'PENDING_REVIEW', label: '待复核', color: 'cyan' },
  { value: 'JUDGED', label: '已判定', color: 'blue' },
  { value: 'RECHECKING', label: '复检中', color: 'orange' },
  { value: 'CLOSED', label: '已闭环', color: 'green' },
];

export const JUDGEMENTS = [
  { value: 'PASS', label: '合格', color: 'green' },
  { value: 'FAIL', label: '不合格', color: 'red' },
  { value: 'NONE', label: '未判定', color: 'default' },
];

export const REPORT_CONCLUSIONS = [
  { value: 'QUALIFIED', label: '合格', color: 'green' },
  { value: 'UNQUALIFIED', label: '不合格', color: 'red' },
  { value: 'CONCESSION', label: '让步接收', color: 'orange' },
  { value: 'PENDING_RECHECK', label: '待复检', color: 'gold' },
];

export const REPORT_STATUS = [
  { value: 'DRAFT', label: '草稿', color: 'default' },
  { value: 'SIGNED', label: '已双签', color: 'blue' },
  { value: 'ISSUED', label: '已签发', color: 'green' },
];

export const PACKAGE_BATCH_CHECK = [
  { value: 'MATCH', label: '一致' },
  { value: 'MISMATCH', label: '不一致' },
];

export const DISPOSE_TYPES = [
  { value: 'DESTROY', label: '销毁' },
  { value: 'RETURN', label: '退还' },
];

// ---------------- 阶段4：不合格闭环/整改/批次 ----------------

export const DEFECT_CASE_STATUS = [
  { value: 'PENDING_REVIEW', label: '待质量评审', color: 'gold' },
  { value: 'PENDING_APPROVAL', label: '待主管审批', color: 'cyan' },
  { value: 'PROCESSING', label: '处置中', color: 'processing' },
  { value: 'PENDING_RECHECK', label: '待复检闭环', color: 'orange' },
  { value: 'CLOSED', label: '已闭环', color: 'green' },
  { value: 'CANCELLED', label: '已撤销', color: 'red' },
];

export const DISPOSITIONS = [
  { value: 'RETURN', label: '退货' },
  { value: 'OFF_SHELF', label: '下架' },
  { value: 'DESTROY', label: '销毁' },
  { value: 'EXCHANGE', label: '换货' },
  { value: 'RECTIFY', label: '供应商整改' },
  { value: 'CONCESSION', label: '让步接收' },
];

export const RECT_STATUS = [
  { value: 'ISSUED', label: '已签发待回复', color: 'gold' },
  { value: 'REPLIED', label: '已回复待验证', color: 'cyan' },
  { value: 'VERIFYING', label: '复检中', color: 'processing' },
  { value: 'PASSED', label: '验证通过', color: 'green' },
  { value: 'FAILED', label: '验证不通过', color: 'red' },
];

export const BATCH_STATUS = [
  { value: 'NORMAL', label: '正常', color: 'green' },
  { value: 'NEAR_EXPIRY', label: '近效期', color: 'orange' },
  { value: 'EXPIRED', label: '已过期', color: 'red' },
];

export const SUPPLIER_GRADES: Record<string, { label: string; color: string }> = {
  A: { label: 'A级', color: 'green' },
  B: { label: 'B级', color: 'blue' },
  C: { label: 'C级', color: 'orange' },
  D: { label: 'D级', color: 'red' },
};

// ---------------- 阶段6：新品引入（NPI） ----------------

export const NPI_STATUS = [
  { value: 'DRAFT', label: '立项', color: 'default' },
  { value: 'STD_REVIEW', label: '标准评审中', color: 'gold' },
  { value: 'SOURCING', label: '寻源送样中', color: 'processing' },
  { value: 'AUDIT', label: '验厂中', color: 'cyan' },
  { value: 'PRODUCING', label: '量产中', color: 'blue' },
  { value: 'EXT_TEST', label: '外检中', color: 'purple' },
  { value: 'LISTING_REVIEW', label: '待上市放行', color: 'orange' },
  { value: 'LISTED', label: '已上市', color: 'green' },
  { value: 'TERMINATED', label: '已终止', color: 'red' },
];

export const STD_GRADES = [
  { value: 'HIGH', label: '高档', color: 'red' },
  { value: 'MID', label: '中档', color: 'orange' },
  { value: 'LOW', label: '低档', color: 'blue' },
];

export const STD_GRADE_FULL: Record<string, { label: string; color: string }> = {
  HIGH: { label: '高档（严于国标/企业定制）', color: 'red' },
  MID: { label: '中档（符合国标并加严）', color: 'orange' },
  LOW: { label: '低档（国标准入底线）', color: 'blue' },
};

export const EVAL_QUALITY = [
  { value: 'PENDING', label: '待判定', color: 'default' },
  { value: 'QUALIFIED', label: '符合标准', color: 'green' },
  { value: 'UNQUALIFIED', label: '不符合', color: 'red' },
];

export const EVAL_DIMENSIONS = [
  { code: 'QUALITY', name: '质量符合性', defaultWeight: 30 },
  { code: 'PACKAGE', name: '包装与标签', defaultWeight: 20 },
  { code: 'PRICE', name: '价格竞争力', defaultWeight: 20 },
  { code: 'DELIVERY', name: '交期保障', defaultWeight: 15 },
  { code: 'SERVICE', name: '服务配合', defaultWeight: 15 },
];

export const AUDIT_STATUS = [
  { value: 'PLANNED', label: '已排期', color: 'default' },
  { value: 'IN_PROGRESS', label: '评分中', color: 'processing' },
  { value: 'SUBMITTED', label: '已提交', color: 'cyan' },
  { value: 'CONFIRMED', label: '已确认', color: 'green' },
];

export const AUDIT_CONCLUSION = [
  { value: 'PASS', label: '合格', color: 'green' },
  { value: 'CONDITIONAL', label: '限期整改', color: 'orange' },
  { value: 'FAIL', label: '不合格', color: 'red' },
];

export const AUDIT_CATEGORIES = [
  { value: 'QUALIFICATION', label: '资质证照' },
  { value: 'PRODUCTION', label: '生产合规' },
  { value: 'WAREHOUSE', label: '仓储合规' },
  { value: 'COLD_CHAIN', label: '冷链保障' },
  { value: 'QUALITY_SYSTEM', label: '质量体系' },
];

export const AUDIT_ITEM_RESULTS = [
  { value: 'COMPLIANT', label: '符合', color: 'green' },
  { value: 'MINOR', label: '一般不符合', color: 'orange' },
  { value: 'MAJOR', label: '严重不符合', color: 'red' },
  { value: 'NA', label: '不适用', color: 'default' },
];

export const EXT_STATUS = [
  { value: 'PLANNED', label: '计划中', color: 'default' },
  { value: 'SENT', label: '已送检', color: 'processing' },
  { value: 'REPORTED', label: '已出报告', color: 'green' },
];

export const EXT_CONCLUSION = [
  { value: 'PENDING', label: '检测中', color: 'default' },
  { value: 'PASS', label: '合格', color: 'green' },
  { value: 'FAIL', label: '不合格', color: 'red' },
  { value: 'PARTIAL', label: '部分不合格', color: 'orange' },
];

export const LAB_QUALIFICATIONS = [
  { value: 'CMA', label: 'CMA 资质认定' },
  { value: 'CNAS', label: 'CNAS 实验室认可' },
  { value: 'NMPA', label: '药监局认可' },
  { value: 'OTHER', label: '其他' },
];
