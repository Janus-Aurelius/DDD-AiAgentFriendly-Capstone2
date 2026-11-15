package com.retail.pim.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring Boot Application Entry Point for Retail PIM Platform.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.retail.pim.shared", "com.retail.pim.catalog", "com.retail.pim.taxonomy"})
public class PimCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(PimCatalogApplication.class, args);
    }
}
