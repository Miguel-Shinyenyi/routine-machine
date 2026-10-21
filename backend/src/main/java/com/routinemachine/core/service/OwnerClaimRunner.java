package com.routinemachine.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

// Runs OwnerClaimService at startup when OWNER_EMAIL is set. A wrong email is logged, not fatal:
// the app must still start, and nothing has moved (the claim is all or nothing).
@Component
public class OwnerClaimRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OwnerClaimRunner.class);

    private final OwnerClaimService ownerClaimService;
    private final OwnerRoleService ownerRoleService;
    private final String ownerEmail;

    public OwnerClaimRunner(
            OwnerClaimService ownerClaimService, OwnerRoleService ownerRoleService,
            @Value("${owner.email:}") String ownerEmail) {
        this.ownerClaimService = ownerClaimService;
        this.ownerRoleService = ownerRoleService;
        this.ownerEmail = ownerEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (ownerEmail == null || ownerEmail.isBlank()) {
            return;
        }
        try {
            Map<String, Integer> claimed = ownerClaimService.claimUnownedDataFor(ownerEmail.trim());
            log.info("Owner claim: unowned rows given to the owner account, per table: {}", claimed);
        } catch (RuntimeException e) {
            log.error("Owner claim skipped, nothing was changed: {}", e.getMessage());
        }
        // Separate from the claim: a claim that fails on a data conflict must not leave the owner
        // without the role.
        try {
            ownerRoleService.grantOwnerRoleTo(ownerEmail.trim());
            log.info("Owner role granted to the configured owner account");
        } catch (RuntimeException e) {
            log.error("Owner role not granted, nothing was changed: {}", e.getMessage());
        }
    }
}
