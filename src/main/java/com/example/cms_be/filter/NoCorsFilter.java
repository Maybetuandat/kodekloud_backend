package com.example.cms_be.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoCorsFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Wrap response to intercept header setting
        HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(httpResponse) {
            private final Set<String> corsHeaders = Set.of(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials",
                "Access-Control-Allow-Methods",
                "Access-Control-Allow-Headers",
                "Access-Control-Max-Age",
                "Access-Control-Expose-Headers"
            );
            
            @Override
            public void setHeader(String name, String value) {
                if (!corsHeaders.contains(name)) {
                    super.setHeader(name, value);
                }
                // Ignore CORS headers
            }
            
            @Override
            public void addHeader(String name, String value) {
                if (!corsHeaders.contains(name)) {
                    super.addHeader(name, value);
                }
                // Ignore CORS headers
            }
        };
        
        chain.doFilter(request, responseWrapper);
    }
}