package com.routinemachine.core.api;

import com.routinemachine.core.domain.CurrentReadingLog;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.repository.CurrentReadingLogRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/current-reading")
public class CurrentReadingController {

    private final CurrentReadingLogRepository currentReadingLogRepository;

    public CurrentReadingController(CurrentReadingLogRepository currentReadingLogRepository) {
        this.currentReadingLogRepository = currentReadingLogRepository;
    }

    @GetMapping
    public CurrentReadingResponse latest() {
        return currentReadingLogRepository.findLatest()
                .map(CurrentReadingResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No current reading logged yet"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentReadingResponse create(@Valid @RequestBody CurrentReadingRequest request) {
        CurrentReadingLog saved = currentReadingLogRepository.save(
                new CurrentReadingLog(request.title(), ReadingSource.MANUAL));
        return CurrentReadingResponse.from(saved);
    }
}
