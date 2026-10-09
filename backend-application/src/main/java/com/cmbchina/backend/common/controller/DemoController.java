package com.cmbchina.backend.common.controller;

import com.cmbchina.backend.common.response.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 演示控制器，用于验证网关转发链路是否正常工作。
 * GET /api/demo/ping 返回当前用户身份信息。
 * POST /api/demo/echo 原样返回请求体。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    @GetMapping("/ping")
    public ResponseEntity<String> ping(HttpServletRequest request) {
        String userId = (String) request.getAttribute("gateway.userId");
        if (userId == null) {
            userId = "anonymous";
        }
        return ResponseEntity.success("pong from backend, user=" + userId);
    }

    @PostMapping("/echo")
    public ResponseEntity<Object> echo(@RequestBody Object body) {
        return ResponseEntity.success(body);
    }
}