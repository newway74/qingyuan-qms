package com.qms.modules.inspection.engine;

import com.qms.modules.standard.entity.NetContentTolerance;
import com.qms.modules.standard.mapper.NetContentToleranceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * JJF1070 分档解析：命中分档与库中无分档两种情况。
 */
@ExtendWith(MockitoExtension.class)
class InspectionJudgeEngineResolveToleranceTest {

    @Mock
    private NetContentToleranceMapper toleranceMapper;

    @Test
    void resolve_returns_matched_tier() {
        NetContentTolerance tier = new NetContentTolerance();
        tier.setShortageType("PERCENT");
        tier.setShortageValue(new BigDecimal("4.5"));
        when(toleranceMapper.selectOne(any())).thenReturn(tier);

        InspectionJudgeEngine engine = new InspectionJudgeEngine(toleranceMapper);
        assertSame(tier, engine.resolveTolerance(new BigDecimal("150")));
    }

    @Test
    void resolve_returns_null_when_no_tier_configured() {
        when(toleranceMapper.selectOne(any())).thenReturn(null);
        InspectionJudgeEngine engine = new InspectionJudgeEngine(toleranceMapper);
        assertNull(engine.resolveTolerance(new BigDecimal("99999")));
    }
}
