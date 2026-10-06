package com.pharmacy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.List;

/**
 * Logs, before anything connects to the database, which config files were loaded and which
 * database/user will be used (never the password). Makes deployment problems easy to see in the log.
 */
public class StartupInfoListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    private static final Logger log = LoggerFactory.getLogger(StartupInfoListener.class);

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment env = event.getEnvironment();
        List<String> files = new ArrayList<>();
        for (PropertySource<?> ps : env.getPropertySources()) {
            String name = ps.getName();
            if (name.contains("Config resource") && name.contains("file [")) files.add(name);
        }
        String user = env.getProperty("spring.datasource.username", "");
        log.info("Pharmacy System profiles: {}", String.join(",", env.getActiveProfiles()));
        log.info("Pharmacy System external config files: {}", files.isEmpty() ? "NONE FOUND" : files);
        log.info("Pharmacy System database: url={} user={} password={}",
                env.getProperty("spring.datasource.url"),
                user.isEmpty() ? "(EMPTY!)" : user,
                env.getProperty("spring.datasource.password", "").isEmpty() ? "(EMPTY!)" : "(set)");
        if (files.isEmpty() && env.acceptsProfiles(org.springframework.core.env.Profiles.of("prod"))) {
            log.warn("No config.yml found. Put it in {}/pharmacy-system/config.yml (or next to ROOT.war in webapps)",
                    System.getProperty("user.home"));
        }
    }
}
