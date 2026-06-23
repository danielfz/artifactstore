package me.danielf.artifactstore;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;

@Component
@Order(1)
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Log request
        log.info(">>> {} {}", req.getMethod(), req.getRequestURI());
        Collections.list(req.getHeaderNames())
                .forEach(h -> log.info("    > {}: {}", h, req.getHeader(h)));

        chain.doFilter(request, response);

        // Log response
        log.info("<<< {} {} {}", req.getMethod(), req.getRequestURI(), res.getStatus());
        res.getHeaderNames()
                .forEach(h -> log.info("    < {}: {}", h, res.getHeader(h)));
    }
}