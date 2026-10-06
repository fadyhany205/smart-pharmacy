package com.pharmacy;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/** Entry point when the WAR is deployed to an external Tomcat (e.g. cPanel). Always runs with the "prod" profile. */
public class ServletInitializer extends SpringBootServletInitializer {
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(PharmacyApplication.class).profiles("prod");
    }
}
