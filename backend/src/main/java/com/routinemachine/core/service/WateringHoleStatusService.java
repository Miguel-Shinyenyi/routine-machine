package com.routinemachine.core.service;

import com.routinemachine.core.api.WateringHoleStatusResponse;
import com.routinemachine.core.domain.HubSyncProcessedFile;
import com.routinemachine.core.domain.Measurement;
import com.routinemachine.core.domain.MeasurementSource;
import com.routinemachine.core.repository.HubSyncProcessedFileRepository;
import com.routinemachine.core.repository.MeasurementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

// The status of every source Phase 6 names, with no view of who is asking. The watering holes page masks it for
// anyone but the owner (WateringHoleStatusController); the owner-only operations page reads it as it is.
@Service
public class WateringHoleStatusService {

    private final HubSyncProcessedFileRepository hubSyncProcessedFileRepository;
    private final MeasurementRepository measurementRepository;
    private final boolean hubSyncEnabled;
    private final boolean wakaTimeEnabled;
    private final boolean activityWatchEnabled;
    private final boolean gitHubEnabled;
    private final boolean rescueTimeEnabled;
    private final boolean healthIngestConfigured;

    public WateringHoleStatusService(
            HubSyncProcessedFileRepository hubSyncProcessedFileRepository,
            MeasurementRepository measurementRepository,
            @Value("${hub-sync.enabled:true}") boolean hubSyncEnabled,
            @Value("${wakatime-sync.enabled:true}") boolean wakaTimeEnabled,
            @Value("${activitywatch-sync.enabled:true}") boolean activityWatchEnabled,
            @Value("${github-sync.enabled:true}") boolean gitHubEnabled,
            @Value("${rescuetime-sync.enabled:false}") boolean rescueTimeEnabled,
            @Value("${health-ingest.token:}") String healthIngestToken) {
        this.hubSyncProcessedFileRepository = hubSyncProcessedFileRepository;
        this.measurementRepository = measurementRepository;
        this.hubSyncEnabled = hubSyncEnabled;
        this.wakaTimeEnabled = wakaTimeEnabled;
        this.activityWatchEnabled = activityWatchEnabled;
        this.gitHubEnabled = gitHubEnabled;
        this.rescueTimeEnabled = rescueTimeEnabled;
        this.healthIngestConfigured = !healthIngestToken.isBlank();
    }

    public List<WateringHoleStatusResponse> statuses() {
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
