package com.cmbchina.backend.gateway.filter;

import com.cmbchina.backend.common.security.XcodePrincipal;
import com.cmbchina.backend.gateway.security.GatewayAuthenticationWebFilter;
import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 清理客户端身份头，并将网关已验证的身份信息以明文 Header 传递给下游。
 * 设计文档 3.1 节：校验完成后 header 中加上校验结果，后端不再做二次校验。
 */
@Component
public class TrustedIdentityGlobalFilter implements GlobalFilter, Ordered {

    /** 为已登录用户传递明文身份头，不签发内部加密票据。 */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        XcodePrincipal principal = exchange.getAttribute(GatewayAuthenticationWebFilter.PRINCIPAL_ATTRIBUTE);
        String requestId = requestId(exchange.getRequest().getHeaders().getFirst("X-Request-Id"));
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            // 无条件清理客户端可能伪造的内部身份头。
            headers.remove(HttpHeaders.AUTHORIZATION);
            headers.remove(HttpHeaders.COOKIE);
            headers.remove("X-User-Id");
            headers.set("X-Request-Id", requestId);
            if (principal != null) {
                // 明文传递用户身份，后端根据此头做业务处理。
                headers.set("X-User-Id", principal.getName());
                headers.set("X-Auth-Status", "200");
            }
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    /** 接受格式受限的请求 ID，否则生成新值。 */
    private String requestId(String supplied) {
        return supplied != null && supplied.matches("[A-Za-z0-9_-]{1,64}")
                ? supplied : UUID.randomUUID().toString();
    }

    /** 在路由转发过滤器之前注入信任身份头。 */
    @Override
    public int getOrder() {
        return -50;
    }
}
