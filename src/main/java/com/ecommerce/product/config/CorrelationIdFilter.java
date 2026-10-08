package com.ecommerce.product.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String value = request.getHeader("X-Correlation-Id");
		String correlationId = value != null && value.matches("[a-zA-Z0-9._-]{1,64}") ? value
				: UUID.randomUUID().toString();
		MDC.put("correlationId", correlationId);
		response.setHeader("X-Correlation-Id", correlationId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove("correlationId");
		}
	}
}
