package com.routinemachine.core.api;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.domain.HubSyncProcessedFile;
import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.repository.HubSyncProcessedFileRepository;
import com.routinemachine.core.repository.MeasurementRepository;
import com.routinemachine.core.service.OwnerAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

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

    private final HubSyncProcessedFileRepository hubSyncProcessedFileRepository;
    private final MeasurementRepository measurementRepository;
    private final OwnerAccount ownerAccount;
    private final boolean hubSyncEnabled;
    private final boolean wakaTimeEnabled;
    private final boolean activityWatchEnabled;
    private final boolean gitHubEnabled;
    private final boolean rescueTimeEnabled;
    private final boolean healthIngestConfigured;

    public WateringHoleStatusController(
            HubSyncProcessedFileRepository hubSyncProcessedFileRepository,
            MeasurementRepository measurementRepository,
            OwnerAccount ownerAccount,
            @Value("${hub-sync.enabled:true}") boolean hubSyncEnabled,
            @Value("${wakatime-sync.enabled:true}") boolean wakaTimeEnabled,
            @Value("${activitywatch-sync.enabled:true}") boolean activityWatchEnabled,
            @Value("${github-sync.enabled:true}") boolean gitHubEnabled,
            @Value("${rescuetime-sync.enabled:false}") boolean rescueTimeEnabled,
            @Value("${health-ingest.token:}") String healthIngestToken) {
        this.hubSyncProcessedFileRepository = hubSyncProcessedFileRepository;
        this.measurementRepository = measurementRepository;
        this.ownerAccount = ownerAccount;
        this.hubSyncEnabled = hubSyncEnabled;
        this.wakaTimeEnabled = wakaTimeEnabled;
        this.activityWatchEnabled = activityWatchEnabled;
        this.gitHubEnabled = gitHubEnabled;
        this.rescueTimeEnabled = rescueTimeEnabled;
        this.healthIngestConfigured = !healthIngestToken.isBlank();
    }

    // Per-user scoping, part 5: these are the deployment's own integrations, run on credentials set
    // for the whole deployment, so they are connected for the owner account and for nobody else.
    // Anyone but the owner: every source reads "not connected" with no activity, which is true for
    // that account. With no owner configured nobody is the owner.
    @GetMapping
    public List<WateringHoleStatusResponse> list(@AuthenticationPrincipal Jwt jwt) {
        List<WateringHoleStatusResponse> statuses = allSources();
        if (!ownerAccount.isOwner(TokenUser.idOf(jwt))) {
            return statuses.stream()
                    .map(status -> new WateringHoleStatusResponse(status.source(), false, null, status.description()))
                    .toList();
        }
        return statuses;
    }

    private List<WateringHoleStatusResponse> allSources() {
        return List.of(
                new WateringHoleStatusResponse(
                        "HUB", hubSyncEnabled, lastHubSyncActivity(),
                        "Reads UMWAYI's journal entries and essays over the public GitHub API; "
                                + "marks journaling/writing routine items done."),
                new WateringHoleStatusResponse(
                        "WAKATIME", wakaTimeEnabled, lastMeasurementActivity(MeasurementSource.WAKATIME),
                        "Pulls daily coding totals from WakaTime's summaries API."),
                new WateringHoleStatusResponse(
                        "ACTIVITYWATCH", activityWatchEnabled, lastMeasurementActivity(MeasurementSource.ACTIVITYWATCH),
                        "Pulls daily active-time totals from a local aw-server instance on the same machine."),
                new WateringHoleStatusResponse(
                        "GITHUB", gitHubEnabled, lastMeasurementActivity(MeasurementSource.GITHUB),
                        "Backup signal: counts commits from GitHub's public events API, not a primary source."),
                new WateringHoleStatusResponse(
                        "RESCUETIME", rescueTimeEnabled, lastMeasurementActivity(MeasurementSource.RESCUETIME),
                        "Optional, off by default. Pulls daily active-time totals from RescueTime's API."),
                new WateringHoleStatusResponse(
                        "HEALTH", healthIngestConfigured, lastMeasurementActivity(MeasurementSource.HEALTH),
                        "Push-only: accepts measurements via POST /api/measurements (token-protected); "
                                + "the backend never pulls health data itself."));
    }

    private OffsetDateTime lastHubSyncActivity() {
        return hubSyncProcessedFileRepository.findTopByOrderByCreatedAtDesc()
                .map(HubSyncProcessedFile::getCreatedAt)
                .orElse(null);
    }

    private OffsetDateTime lastMeasurementActivity(MeasurementSource source) {
        return measurementRepository.findTopBySourceOrderByUpdatedAtDesc(source)
                .map(Measurement::getUpdatedAt)
                .orElse(null);
    }
}
