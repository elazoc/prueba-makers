package com.wecaelum.prestemos.infrastructure.security;

import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.model.UserId;
import java.util.Optional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * Looks up the current role and active flag of a token's user, so deactivations and role changes apply at once.
 * Reads through the {@link UserRepository} port; the cache is evicted when the user changes (design section 7.1).
 */
@Component
public class UserAuthSnapshotProvider {

    private final UserRepository users;

    public UserAuthSnapshotProvider(UserRepository users) {
        this.users = users;
    }

    @Cacheable(cacheNames = "userAuthById", key = "#id.value()")
    public Optional<UserAuthSnapshot> find(UserId id) {
        return users.findById(id).map(user -> new UserAuthSnapshot(user.id(), user.role(), user.active()));
    }
}
