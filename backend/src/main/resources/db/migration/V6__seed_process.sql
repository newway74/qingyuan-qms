-- =====================================================================
-- V6: 默认品控流程（QC_DEFAULT PUBLISHED v1，九节点）+ 首版变更记录
-- 在途单据后续按发布版本快照引用；新版发布不影响在途单据。
-- =====================================================================

INSERT INTO qc_process_def
(id, process_code, process_name, version, status, remark, published_by, published_at, tenant_id, lock_version, created_by, created_at, updated_by, updated_at, deleted) VALUES
(9001, 'QC_DEFAULT', '清源 QMS 品控全流程（默认）', 1, 'PUBLISHED',
 '抽样→收样→分配→检验→复核→判定→不合格处置→复检→归档 九节点默认编排',
 1, NOW(), 1, 0, 1, NOW(), 1, NOW(), 0);

INSERT INTO qc_process_node
(id, process_def_id, node_code, node_name, responsible_role, sla_hours, calendar_type, required_fields, transition_rules, sort, tenant_id, created_by, created_at, updated_by, updated_at, deleted) VALUES
(7001, 9001, 'SAMPLING', '抽样登记', 'SAMPLER',    24, 'NATURAL',
 JSON_ARRAY('source','skuId','batchNo','productionDate','shelfLifeTo','storageCondition','samplingQty','samplingPlace'),
 JSON_OBJECT('pass','SUBMIT','reject',JSON_ARRAY('CANCEL')), 10, 1, 1, NOW(), 1, NOW(), 0),
(7002, 9001, 'RECEIVE',  '收样登记', 'SAMPLER',    24, 'NATURAL',
 JSON_ARRAY('printCodeConsistent','differenceRemark','receiveQty','sampleTypes'),
 JSON_OBJECT('pass','RECEIVED','reject',JSON_ARRAY('BACK_TO_SAMPLING')), 20, 1, 1, NOW(), 1, NOW(), 0),
(7003, 9001, 'ASSIGN',   '任务分配', 'QA_MANAGER',  8, 'NATURAL',
 JSON_ARRAY('inspectorId','reviewerId','deadline'),
 JSON_OBJECT('pass','ASSIGNED'), 30, 1, 1, NOW(), 1, NOW(), 0),
(7004, 9001, 'INSPECT',  '逐项检验', 'INSPECTOR',  48, 'NATURAL',
 JSON_ARRAY('results','attachments','autoJudgement'),
 JSON_OBJECT('pass','SUBMIT_REVIEW','reject',JSON_ARRAY('SAVE_DRAFT')), 40, 1, 1, NOW(), 1, NOW(), 0),
(7005, 9001, 'REVIEW',   '复核签名', 'REVIEWER',   24, 'NATURAL',
 JSON_ARRAY('signaturePassword','reviewComment'),
 JSON_OBJECT('pass','REVIEW_PASS','reject',JSON_ARRAY('REJECT_TO_INSPECT')), 50, 1, 1, NOW(), 1, NOW(), 0),
(7006, 9001, 'JUDGE',    '综合判定', 'REVIEWER',    8, 'NATURAL',
 JSON_ARRAY('finalJudgement','judgeSnapshot'),
 JSON_OBJECT('pass','JUDGED','routes',JSON_OBJECT('QUALIFIED','ARCHIVE','UNQUALIFIED','DEFECT','PENDING_RECHECK','RECHECK','CONCESSION','DEFECT')), 60, 1, 1, NOW(), 1, NOW(), 0),
(7007, 9001, 'DEFECT',   '不合格处置与审批', 'QA_MANAGER', 72, 'NATURAL',
 JSON_ARRAY('defectLevel','disposition','reason','approvalSignature'),
 JSON_OBJECT('pass','DISPOSED','reject',JSON_ARRAY('REJECT_REVIEW'),'guard',JSON_OBJECT('CONCESSION','A_CLASS_FORBIDDEN')), 70, 1, 1, NOW(), 1, NOW(), 0),
(7008, 9001, 'RECHECK',  '供应商整改与复检', 'INSPECTOR', 48, 'NATURAL',
 JSON_ARRAY('rectificationId','recheckResult','recheckConclusion'),
 JSON_OBJECT('pass','CLOSED','routes',JSON_OBJECT('PASS','ARCHIVE','FAIL','BACK_TO_DEFECT')), 80, 1, 1, NOW(), 1, NOW(), 0),
(7009, 9001, 'ARCHIVE',  '报告签发归档', 'SAMPLER', 24, 'NATURAL',
 JSON_ARRAY('reportId','reportPdf','archiveRemark'),
 JSON_OBJECT('pass','ARCHIVED'), 90, 1, 1, NOW(), 1, NOW(), 0);

INSERT INTO qc_process_change_log
(id, process_def_id, process_code, from_version, to_version, change_diff, changed_by, changed_at, tenant_id) VALUES
(8001, 9001, 'QC_DEFAULT', NULL, 1,
 JSON_OBJECT('added', JSON_ARRAY('SAMPLING','RECEIVE','ASSIGN','INSPECT','REVIEW','JUDGE','DEFECT','RECHECK','ARCHIVE'),
             'removed', JSON_ARRAY(),
             'changed', JSON_ARRAY()),
 1, NOW(), 1);
