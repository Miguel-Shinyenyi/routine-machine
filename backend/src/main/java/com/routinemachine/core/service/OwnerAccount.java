package com.routinemachine.core.service;

import com.routinemachine.core.domain.RoutineItem;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

// Per-user scoping, part 5. The integrations (WakaTime, ActivityWatch, GitHub, RescueTime, the
// UMWAYI hub, health ingest) run on credentials set for the whole deployment, so what they pull
// in is the deployment owner's, not every account's. The owner is the account named by
// OWNER_EMAIL, the same one OwnerClaimService hands the pre-account data to. With none
// configured, or no account registered with that email yet, there is no owner, and an
// integration writes only to unowned rows (the pre-account data it has always written to).
@Component
public class OwnerAccount {

    private final UserRepository userRepository;
    private final String ownerEmail;

    public OwnerAccount(UserRepository userRepository, @Value("${owner.email:}") String ownerEmail) {
        this.userRepository = userRepository;
        this.ownerEmail = ownerEmail == null ? "" : ownerEmail.trim();
    }

    public Optional<User> owner() {
        if (ownerEmail.isEmpty()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(ownerEmail);
    }

    public boolean isOwner(Long userId) {
        return owner().map(owner -> owner.getId().equals(userId)).orElse(false);
    }

    // Whether an integration may write to a row owned by itemOwner: the owner's rows and unowned
    // (pre-account) rows, never another account's.
    public boolean isIntegrationTarget(User itemOwner) {
        return itemOwner == null || isOwner(itemOwner.getId());
    }

    public List<RoutineItem> integrationTargets(List<RoutineItem> items) {
        Optional<User> owner = owner();
        return items.stream()
                .filter(item -> item.getUser() == null
                        || owner.map(o -> o.getId().equals(item.getUser().getId())).orElse(false))
                .toList();
    }
}
