package com.wecaelum.prestemos.domain.model;

import java.time.Instant;

public record User(
        UserId id,
        Email email,
        String passwordHash,
        String fullName,
        Role role,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        Long version) {

    private static final int MIN_NAME = 2;
    private static final int MAX_NAME = 100;

    public User {
        fullName = validName(fullName);
    }

    public static User register(Email email, String passwordHash, String fullName, Role role, Instant now) {
        return new User(UserId.newId(), email, passwordHash, fullName, role, true, now, now, null);
    }

    public User changeProfile(String fullName, Role role, boolean active, Instant now) {
        return new User(id, email, passwordHash, fullName, role, active, createdAt, now, version);
    }

    private static String validName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.length() < MIN_NAME || name.length() > MAX_NAME) {
            throw new IllegalArgumentException("El nombre debe tener entre 2 y 100 caracteres.");
        }
        return name;
    }
}
