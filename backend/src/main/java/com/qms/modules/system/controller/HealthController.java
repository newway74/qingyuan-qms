package com.qms.modules.system.controller;

import com.qms.common.result.R;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "健康检查")
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public R<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", "qingyuan-qms");
        body.put("serverTime", LocalDateTime.now().toString());
        return R.ok(body);
    }
}
