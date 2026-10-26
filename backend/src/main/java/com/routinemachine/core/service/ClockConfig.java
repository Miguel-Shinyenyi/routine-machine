package com.routinemachine.core.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// The one place the application's clock comes from, so a service that works out "today" can be given a fixed moment in a test
// instead of reading the real time (Backlog P). UTC is the instant; a person's own day is worked out from their saved zone.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
