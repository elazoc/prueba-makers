package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
class UserPersistenceAdapter implements UserRepository {

    private final SpringDataUserRepository repository;

    UserPersistenceAdapter(SpringDataUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public User save(User user) {
        return UserJpaMapper.toDomain(repository.saveAndFlush(UserJpaMapper.toEntity(user)));
    }

    @Override
    public Optional<User> findById(UserId id) {
        return repository.findById(id.value()).map(UserJpaMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return repository.findByEmail(email.value()).map(UserJpaMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return repository.existsByEmail(email.value());
    }

    @Override
    public PageResult<User> findAll(Role roleOrNull, Boolean activeOrNull, PageQuery query) {
        Page<UserJpaEntity> page = repository.search(roleOrNull, activeOrNull, SortWhitelist.users(query));
        return new PageResult<>(
                page.getContent().stream().map(UserJpaMapper::toDomain).toList(),
                query.page(), query.size(), page.getTotalElements());
    }
}
