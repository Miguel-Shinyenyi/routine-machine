package com.routinemachine.core.api;

import com.routinemachine.core.domain.CurrentReadingLog;
import com.routinemachine.core.domain.ReadingSource;
import com.routinemachine.core.repository.CurrentReadingLogRepository;
import com.routinemachine.core.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.Optional;

@RestController
@RequestMapping("/api/current-reading")
public class CurrentReadingController {

    private final CurrentReadingLogRepository currentReadingLogRepository;
    private final UserRepository userRepository;

    public CurrentReadingController(
            CurrentReadingLogRepository currentReadingLogRepository, UserRepository userRepository) {
        this.currentReadingLogRepository = currentReadingLogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public CurrentReadingResponse latest(@RequestParam Optional<Long> userId) {
        // Per-user scoping (docs/backend.md): the latest entry of that user, or — userId
        // omitted — of nobody.
        Optional<CurrentReadingLog> latest = userId.isPresent()
                ? currentReadingLogRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId.get())
                : currentReadingLogRepository.findFirstByUserIsNullOrderByCreatedAtDescIdDesc();
        return latest
                .map(CurrentReadingResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No current reading logged yet"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentReadingResponse create(@Valid @RequestBody CurrentReadingRequest request) {
        CurrentReadingLog entry = new CurrentReadingLog(request.title(), ReadingSource.MANUAL);
        if (request.userId() != null) {
            entry.assignUser(userRepository.findById(request.userId())
                    .orElseThrow(() -> new IllegalArgumentException("No user with id " + request.userId())));
        }
        return CurrentReadingResponse.from(currentReadingLogRepository.save(entry));
    }
}
