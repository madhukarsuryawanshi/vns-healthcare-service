package com.vns.healthcare.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Component
public class RequestTimingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingInterceptor.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute("requestStartTime", System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object startTime = request.getAttribute("requestStartTime");
        if (startTime instanceof Long) {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - (Long) startTime);
            log.info("REQUEST {} {} status={} elapsedMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs);
        }
    }
}
