package com.routinemachine.core.service;

import com.routinemachine.core.domain.DailyLogSource;
import com.routinemachine.core.domain.EvidenceEvent;
import com.routinemachine.core.domain.EvidenceStatus;
import com.routinemachine.core.domain.Goal;
import com.routinemachine.core.domain.ScoringRule;
import com.routinemachine.core.domain.User;
import com.routinemachine.core.repository.DailyCreditRepository;
import com.routinemachine.core.repository.EvidenceEventRepository;
import com.routinemachine.core.repository.ScoringRuleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

// Phase 9: the evidence-to-credit pipeline from docs/product-direction.md (ingest, validate,
// normalise, deduplicate, qualify, aggregate — publish/audit live in HillsideV2Service and the
// ledger read path). One credit per qualifying goal per local calendar day; extra qualifying
// evidence the same day never earns a second one, enforced by daily_credits' own UNIQUE
// constraint via an ON CONFLICT DO NOTHING upsert, not an application-level check.
@Service
public class EvidenceCreditService {

    private final EvidenceEventRepository evidenceEventRepository;
    private final DailyCreditRepository dailyCreditRepository;
    private final ScoringRuleRepository scoringRuleRepository;

    public EvidenceCreditService(
            EvidenceEventRepository evidenceEventRepository, DailyCreditRepository dailyCreditRepository,
            ScoringRuleRepository scoringRuleRepository) {
        this.evidenceEventRepository = evidenceEventRepository;
        this.dailyCreditRepository = dailyCreditRepository;
        this.scoringRuleRepository = scoringRuleRepository;
    }

    public EvidenceEvent ingest(
            User user, Goal goal, DailyLogSource source, String sourceEventId, Instant occurredAt, Integer minutes) {
        Optional<EvidenceEvent> existing =
                evidenceEventRepository.findByUserIdAndSourceAndSourceEventId(user.getId(), source, sourceEventId);
        if (existing.isPresent()) {
            return existing.get();
        }

        // The day is decided in the user's configured time zone, not UTC — see
        // docs/product-direction.md's "Time" section. Stored once, here, so a later zone
        // change never silently rewrites which day a past credit landed on.
        LocalDate localDate = occurredAt.atZone(ZoneId.of(user.getTimeZone())).toLocalDate();

        Optional<ScoringRule> rule = scoringRuleRepository.findFirstByGoalIdOrderByVersionDesc(goal.getId());
        Qualification qualification = qualify(source, minutes, rule);

        EvidenceEvent event = new EvidenceEvent(
                user, goal, source, sourceEventId, occurredAt, localDate, minutes,
                qualification.status(), qualification.ruleVersion(), qualification.reason());

        EvidenceEvent saved;
        try {
            saved = evidenceEventRepository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // Lost a true concurrent race on the same (user, source, sourceEventId) to another
            // caller; its row is the record now, so return that instead of failing.
            return evidenceEventRepository
                    .findByUserIdAndSourceAndSourceEventId(user.getId(), source, sourceEventId)
                    .orElseThrow(() -> e);
        }

        if (saved.earnsCredit()) {
            dailyCreditRepository.awardCreditIfAbsent(user.getId(), goal.getId(), localDate, saved.getId());
        }

        return saved;
    }

    private record Qualification(EvidenceStatus status, String reason, int ruleVersion) {
    }

    private Qualification qualify(DailyLogSource source, Integer minutes, Optional<ScoringRule> rule) {
        if (source == DailyLogSource.MANUAL) {
            return new Qualification(
                    EvidenceStatus.SELF_REPORTED, "Self-reported; self-reports count and are labelled",
                    rule.map(ScoringRule::getVersion).orElse(0));
        }
        if (rule.isEmpty()) {
            return new Qualification(EvidenceStatus.ACCEPTED, "No published rule for this goal yet; any evidence counts", 0);
        }
        ScoringRule r = rule.get();
        if (r.getMinMinutes() == null) {
            return new Qualification(EvidenceStatus.ACCEPTED, r.getDescription(), r.getVersion());
        }
        if (minutes == null) {
            return new Qualification(
                    EvidenceStatus.PENDING, "Waiting for a duration to confirm this qualifies", r.getVersion());
        }
        if (minutes >= r.getMinMinutes()) {
            return new Qualification(EvidenceStatus.ACCEPTED, r.getDescription(), r.getVersion());
        }
        return new Qualification(
                EvidenceStatus.REJECTED, "Under " + r.getMinMinutes() + " minutes. Still in your tally.", r.getVersion());
    }
}
