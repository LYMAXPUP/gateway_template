package com.cmbchina.backend.common.security;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.springframework.stereotype.Component;

/**
 * 从网关传递的明文 Header 中读取已验证的用户 ID，写入 request attribute 供 Controller 使用。
 * 设计文档 3.1 节：网关校验完成后 header 中加上校验结果，后端不再做二次校验。
 */
@Component
public class GatewayUserFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String userId = httpRequest.getHeader("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(httpRequest);
                wrapper.setAttribute("gateway.userId", userId);
                wrapper.setAttribute("gateway.authStatus",
                        httpRequest.getHeader("X-Auth-Status"));
                chain.doFilter(wrapper, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}