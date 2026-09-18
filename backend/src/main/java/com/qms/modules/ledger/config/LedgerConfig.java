package com.qms.modules.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 商品台账模块配置：提供编程式事务模板。
 * Excel 批量导入要求“每行独立事务、单行失败不影响其他行”，
 * 声明式事务无法逐行控制边界，因此统一使用 TransactionTemplate。
 */
@Configuration
public class LedgerConfig {

    @Bean
    public TransactionTemplate ledgerTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}
