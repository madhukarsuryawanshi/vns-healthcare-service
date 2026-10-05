package com.vns.healthcare;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
public class VnsHealthcareApplication {

    private static final Logger log = LoggerFactory.getLogger(VnsHealthcareApplication.class);

    public static void main(String[] args) {
        log.info("Starting VNS Healthcare application");
        SpringApplication.run(VnsHealthcareApplication.class, args);
    }
}
