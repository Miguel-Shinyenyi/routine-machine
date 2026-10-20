package com.routinemachine.core.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerClaimRunnerTest {

    @Test
    void doesNothingWhenNoOwnerEmailIsConfigured() throws Exception {
        OwnerClaimService service = mock(OwnerClaimService.class);

        new OwnerClaimRunner(service, "").run(null);
        new OwnerClaimRunner(service, "   ").run(null);

        verify(service, never()).claimUnownedDataFor(anyString());
    }

    @Test
    void claimsForTheConfiguredOwnerEmail() throws Exception {
        OwnerClaimService service = mock(OwnerClaimService.class);
        when(service.claimUnownedDataFor("owner@example.com")).thenReturn(Map.of("goals", 3));

        new OwnerClaimRunner(service, " owner@example.com ").run(null);

        verify(service).claimUnownedDataFor("owner@example.com");
    }

    @Test
    void aWrongOwnerEmailIsLoggedNotFatal() {
        OwnerClaimService service = mock(OwnerClaimService.class);
        when(service.claimUnownedDataFor("typo@example.com")).thenThrow(new NoSuchElementException("no such user"));

        assertThatCode(() -> new OwnerClaimRunner(service, "typo@example.com").run(null)).doesNotThrowAnyException();
    }
}
