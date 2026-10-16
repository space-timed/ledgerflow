package com.ledgerflow.common.security;

import com.ledgerflow.merchant.domain.Merchant;
import com.ledgerflow.merchant.service.ApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    public static final String API_KEY_HEADER = "X-Api-Key";
    private final ApiKeyService apiKeyService;

    public ApiKeyAuthInterceptor(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();

        // Skip auth for documentation, actuator, and public merchant onboarding endpoints
        if (path.startsWith("/swagger-ui") ||
            path.startsWith("/v3/api-docs") ||
            path.startsWith("/actuator") ||
            (path.equals("/api/v1/merchants") && request.getMethod().equalsIgnoreCase("POST")) ||
            path.startsWith("/api/v1/merchants/admin") ||
            path.startsWith("/api/v1/mock")) {
            return true;
        }

        String rawApiKey = request.getHeader(API_KEY_HEADER);
        Merchant merchant = apiKeyService.authenticate(rawApiKey);
        MerchantContext.setMerchant(merchant);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MerchantContext.clear();
    }
}
