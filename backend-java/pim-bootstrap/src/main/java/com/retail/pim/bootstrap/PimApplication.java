package com.retail.pim.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PIM Modular Monolith Application Bootstrap.
 * Single deployable runtime hosting hexagonal contexts.
 */
@SpringBootApplication(scanBasePackages = "com.retail.pim")
@EntityScan(basePackages = "com.retail.pim")
@EnableJpaRepositories(basePackages = "com.retail.pim")
@EnableScheduling
public class PimApplication {

    public static void main(String[] args) {
        SpringApplication.run(PimApplication.class, args);
    }
}
