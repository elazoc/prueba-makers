package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;

final class UserJpaMapper {

    private UserJpaMapper() {}

    static User toDomain(UserJpaEntity e) {
        return new User(
                new UserId(e.getId()), new Email(e.getEmail()), e.getPasswordHash(), e.getFullName(), e.getRole(),
                e.isActive(), e.getCreatedAt(), e.getUpdatedAt(), e.getVersion());
    }

    static UserJpaEntity toEntity(User u) {
        UserJpaEntity e = new UserJpaEntity();
        e.setId(u.id().value());
        e.setEmail(u.email().value());
        e.setPasswordHash(u.passwordHash());
        e.setFullName(u.fullName());
        e.setRole(u.role());
        e.setActive(u.active());
        e.setCreatedAt(u.createdAt());
        e.setUpdatedAt(u.updatedAt());
        e.setVersion(u.version());
        return e;
    }
}
