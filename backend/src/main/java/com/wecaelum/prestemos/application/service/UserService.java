package com.wecaelum.prestemos.application.service;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.in.CreateUserCommand;
import com.wecaelum.prestemos.application.port.in.ManageUsersUseCase;
import com.wecaelum.prestemos.application.port.in.RegisterUserCommand;
import com.wecaelum.prestemos.application.port.in.RegisterUserUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateUserCommand;
import com.wecaelum.prestemos.application.port.out.DomainEventPublisher;
import com.wecaelum.prestemos.application.port.out.PasswordHasher;
import com.wecaelum.prestemos.application.port.out.UserRepository;
import com.wecaelum.prestemos.domain.event.UserChanged;
import com.wecaelum.prestemos.domain.exception.CannotDeactivateSelfException;
import com.wecaelum.prestemos.domain.exception.CannotDemoteSelfException;
import com.wecaelum.prestemos.domain.exception.EmailAlreadyRegisteredException;
import com.wecaelum.prestemos.domain.exception.UserNotFoundException;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

public class UserService implements RegisterUserUseCase, ManageUsersUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final DomainEventPublisher events;
    private final Clock clock;

    public UserService(UserRepository users, PasswordHasher hasher, DomainEventPublisher events, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public User register(RegisterUserCommand command) {
        return createUser(command.email(), command.rawPassword(), command.fullName(), Role.USER);
    }

    @Override
    @Transactional
    public User create(CreateUserCommand command) {
        return createUser(command.email(), command.rawPassword(), command.fullName(), command.role());
    }

    @Override
    @Transactional
    public User update(UpdateUserCommand command) {
        if (!command.active() && command.id().equals(command.actingAdmin())) {
            throw new CannotDeactivateSelfException();
        }
        if (command.role() != Role.ADMIN && command.id().equals(command.actingAdmin())) {
            throw new CannotDemoteSelfException();
        }
        User user = find(command.id());
        Instant now = clock.instant();
        User saved = users.save(user.changeProfile(command.fullName(), command.role(), command.active(), now));
        events.publish(new UserChanged(saved.id(), now));
        return saved;
    }

    @Override
    @Transactional
    public void deactivate(UserId target, UserId actingAdmin) {
        if (target.equals(actingAdmin)) {
            throw new CannotDeactivateSelfException();
        }
        User user = find(target);
        if (!user.active()) {
            return;
        }
        Instant now = clock.instant();
        User saved = users.save(user.changeProfile(user.fullName(), user.role(), false, now));
        events.publish(new UserChanged(saved.id(), now));
    }

    @Override
    @Transactional(readOnly = true)
    public User get(UserId id) {
        return find(id);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> list(Role roleOrNull, Boolean activeOrNull, PageQuery query) {
        return users.findAll(roleOrNull, activeOrNull, query);
    }

    private User createUser(String rawEmail, String rawPassword, String fullName, Role role) {
        Email email = new Email(rawEmail);
        if (users.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User user = User.register(email, hasher.hash(rawPassword), fullName, role, clock.instant());
        return users.save(user);
    }

    private User find(UserId id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }
}
