package com.routinemachine.core.observability;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

// One line saying what went wrong, for a log line: each exception's class and message, then its cause, up to four links, cut
// at 200 characters. The GitHub and hub clients logged only the class name ("ConnectException"), so the reason for their
// failures could not be read from the log (docs/observability.md, Backlog item I). Used only where the exception cannot hold
// anything private: those two clients call public addresses, with no key or token in the address.
public final class FailureDescription {

    private static final int MAX_LINKS = 4;
    private static final int MAX_LENGTH = 200;

    private FailureDescription() {
    }

    public static String of(Throwable failure) {
        if (failure == null) {
            return "unknown";
        }
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        StringBuilder text = new StringBuilder();
        Throwable current = failure;
        for (int links = 0; current != null && links < MAX_LINKS && seen.add(current); links++) {
            if (links > 0) {
                text.append(" <- ");
            }
            text.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                text.append(": ").append(message.strip());
            }
            current = current.getCause();
        }
        if (text.length() > MAX_LENGTH) {
            return text.substring(0, MAX_LENGTH - 3) + "...";
        }
        return text.toString();
    }
}
