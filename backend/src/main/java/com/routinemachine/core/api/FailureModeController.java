package com.routinemachine.core.api;

import com.routinemachine.core.security.TokenUser;
import com.routinemachine.core.service.WeeklyFailureModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// The weekly "Which failure mode did you meet this week?" (staged Prompt 7). Who is asking comes from the token only.
@RestController
@RequestMapping("/api/failure-modes")
public class FailureModeController {

    private final WeeklyFailureModeService service;

    public FailureModeController(WeeklyFailureModeService service) {
        this.service = service;
    }

    @Operation(summary = "This week's failure-mode answer (the week starts on Monday in the person's own time zone)")
    @GetMapping("/current")
    public FailureModeResponse current(@AuthenticationPrincipal Jwt jwt) {
        return FailureModeResponse.from(service.current(TokenUser.idOf(jwt)));
    }

    @Operation(summary = "Answer, or change the answer, for this week")
    @ApiResponse(responseCode = "400", description = "The failure mode is missing or is not one of the eight. A required field, parameter or path value is missing or cannot be read.")
    @PutMapping("/current")
    public FailureModeResponse answer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FailureModeRequest request) {
        return FailureModeResponse.from(service.answer(TokenUser.idOf(jwt), request.failureMode()));
    }
}
