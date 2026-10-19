package com.atlas.erp.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminBootstrapService bootstrapService;
    private final boolean enabled;
    private final String name;
    private final String email;
    private final String password;

    public AdminBootstrapRunner(
            AdminBootstrapService bootstrapService,
            @Value("${app.bootstrap.admin.enabled:false}") boolean enabled,
            @Value("${app.bootstrap.admin.name:}") String name,
            @Value("${app.bootstrap.admin.email:}") String email,
            @Value("${app.bootstrap.admin.password:}") String password) {
        this.bootstrapService = bootstrapService;
        this.enabled = enabled;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }

        if (bootstrapService.createFirstAdmin(name, email, password)) {
            log.info("Initial administrator bootstrap completed.");
        } else {
            log.info("Initial administrator bootstrap skipped because users already exist.");
        }
    }
}
