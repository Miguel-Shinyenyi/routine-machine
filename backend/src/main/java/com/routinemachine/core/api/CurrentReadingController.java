package com.routinemachine.core.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.routinemachine.core.security.TokenUser;
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

    @Operation(summary = "Get the latest current-reading entry")
    @GetMapping
    public CurrentReadingResponse latest(@AuthenticationPrincipal Jwt jwt) {
        return currentReadingLogRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(TokenUser.idOf(jwt))
                .map(CurrentReadingResponse::from)
                .orElseThrow(() -> new NoSuchElementException("No current reading logged yet"));
    }

    @Operation(summary = "Record what is being read now")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentReadingResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CurrentReadingRequest request) {
        Long userId = TokenUser.idOf(jwt);
        CurrentReadingLog entry = new CurrentReadingLog(request.title(), ReadingSource.MANUAL);
        entry.assignUser(userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("No user with id " + userId)));
        return CurrentReadingResponse.from(currentReadingLogRepository.save(entry));
    }
}
