package com.routinemachine.core.service;

import com.routinemachine.core.domain.User;

import java.util.Objects;

// Per-user scoping (docs/backend.md). A row is owned by one user, or by nobody (a pre-account
// "unowned" row, which OwnerClaimService hands to the owner). An id of null always means
// "nobody", so a null id can only ever see and touch unowned rows. (Every controller passes the id from the token.)
// idOf reads the id off a lazy proxy without loading it, so this is safe outside a transaction.
public final class Ownership {

    private Ownership() {
    }

    public static Long idOf(User owner) {
        return owner == null ? null : owner.getId();
    }

    public static boolean ownedBy(User rowOwner, Long userId) {
        return Objects.equals(idOf(rowOwner), userId);
    }

    public static boolean sameOwner(User first, User second) {
        return Objects.equals(idOf(first), idOf(second));
    }
}
