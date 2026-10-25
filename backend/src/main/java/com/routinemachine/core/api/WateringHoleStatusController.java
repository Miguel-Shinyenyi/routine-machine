package com.routinemachine.core.api;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.service.OwnerAccount;
import com.routinemachine.core.service.WateringHoleStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// GET /api/watering-holes: status of every source Phase 6 names, for the frontend's watering
// holes page. "enabled" reflects each sync's own feature flag (read the same way the sync
// services themselves do, via @Value with the same defaults), not whether it has ever
// actually synced successfully. "lastActivityAt" is the latest timestamp this backend has
// recorded for that source, hub-sync's own table for HUB, measurements for everything else.
// Deliberately does not cover Strava (deferred, not built, see backend.md) or the per-item
// "how each activity is counted" table, which the frontend builds from GET /api/routine-items
// directly rather than this endpoint duplicating that data.
@RestController
@RequestMapping("/api/watering-holes")
public class WateringHoleStatusController {

    private final WateringHoleStatusService statusService;
    private final OwnerAccount ownerAccount;

    public WateringHoleStatusController(WateringHoleStatusService statusService, OwnerAccount ownerAccount) {
        this.statusService = statusService;
        this.ownerAccount = ownerAccount;
    }

    // Per-user scoping, part 5: these are the deployment's own integrations, run on credentials set
    // for the whole deployment, so they are connected for the owner account and for nobody else.
    // Anyone but the owner: every source reads "not connected" with no activity, which is true for
    // that account. With no owner configured nobody is the owner.
    @GetMapping
    public List<WateringHoleStatusResponse> list(@AuthenticationPrincipal Jwt jwt) {
        List<WateringHoleStatusResponse> statuses = statusService.statuses();
        if (!ownerAccount.isOwner(TokenUser.idOf(jwt))) {
            return statuses.stream()
                    .map(status -> new WateringHoleStatusResponse(status.source(), false, null, status.description()))
                    .toList();
        }
        return statuses;
    }
}
