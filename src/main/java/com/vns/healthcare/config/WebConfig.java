package com.vns.healthcare.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final RequestTimingInterceptor requestTimingInterceptor;

    public WebConfig(RequestTimingInterceptor requestTimingInterceptor) {
        this.requestTimingInterceptor = requestTimingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestTimingInterceptor);
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        log.debug("Registering custom MVC converters");
        registry.addConverter(new Converter<String, Long>() {
            @Override
            public Long convert(String source) {
                if (source == null || source.trim().isEmpty()) {
                    return null;
                }
                return Long.valueOf(source.trim());
            }
        });
        registry.addConverter(new Converter<String, LocalDate>() {
            @Override
            public LocalDate convert(String source) {
                if (source == null || source.trim().isEmpty()) {
                    return null;
                }
                return LocalDate.parse(source.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            }
        });
        registry.addConverter(new Converter<String, LocalTime>() {
            @Override
            public LocalTime convert(String source) {
                if (source == null || source.trim().isEmpty()) {
                    return null;
                }
                return LocalTime.parse(source.trim());
            }
        });
        registry.addConverter(new Converter<String, java.math.BigDecimal>() {
            @Override
            public java.math.BigDecimal convert(String source) {
                if (source == null || source.trim().isEmpty()) {
                    return null;
                }
                return new java.math.BigDecimal(source.trim().replace(",", ""));
            }
        });
    }
}
