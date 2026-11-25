package com.wecaelum.prestemos.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.in.CreateUserCommand;
import com.wecaelum.prestemos.application.port.in.RegisterUserCommand;
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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

    @Mock
    UserRepository repo;

    @Mock
    PasswordHasher hasher;

    @Mock
    DomainEventPublisher publisher;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private UserService service() {
        return new UserService(repo, hasher, publisher, clock);
    }

    private User existing(Role role, boolean active) {
        return new User(
                UserId.newId(), new Email("a@test.com"), "hash", "Ana Pérez", role, active, NOW.minusSeconds(60),
                NOW.minusSeconds(60), 3L);
    }

    @Test
    void registerHashesPasswordAndForcesUserRole() {
        when(repo.existsByEmail(new Email("nuevo@test.com"))).thenReturn(false);
        when(hasher.hash("secreto123")).thenReturn("HASHED");
        when(repo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User saved = service().register(new RegisterUserCommand("nuevo@test.com", "secreto123", "Nuevo Usuario"));

        assertThat(saved.role()).isEqualTo(Role.USER);
        assertThat(saved.passwordHash()).isEqualTo("HASHED");
        assertThat(saved.active()).isTrue();
        assertThat(saved.createdAt()).isEqualTo(NOW);
        verifyNoInteractions(publisher);
    }

    @Test
    void registerNormalizesEmailAndRejectsDuplicateIgnoringCase() {
        when(repo.existsByEmail(new Email("admin@test.com"))).thenReturn(true);

        assertThatThrownBy(() -> service().register(new RegisterUserCommand("ADMIN@test.com ", "secreto123", "Otro")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void createByAdminKeepsRequestedRoleAndRejectsDuplicates() {
        when(repo.existsByEmail(new Email("jefe@test.com"))).thenReturn(false);
        when(hasher.hash("secreto123")).thenReturn("HASHED");
        when(repo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User saved = service().create(new CreateUserCommand("Jefe@Test.com", "secreto123", "Jefe Demo", Role.ADMIN));

        assertThat(saved.role()).isEqualTo(Role.ADMIN);
        assertThat(saved.email()).isEqualTo(new Email("jefe@test.com"));

        when(repo.existsByEmail(new Email("dup@test.com"))).thenReturn(true);
        assertThatThrownBy(() -> service().create(new CreateUserCommand("dup@test.com", "secreto123", "Dup", Role.USER)))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
    }

    @Test
    void adminCannotDeactivateSelf() {
        UserId me = UserId.newId();

        assertThatThrownBy(() -> service().deactivate(me, me)).isInstanceOf(CannotDeactivateSelfException.class);
        assertThatThrownBy(() -> service().update(new UpdateUserCommand(me, me, "Admin Demo", Role.ADMIN, false)))
                .isInstanceOf(CannotDeactivateSelfException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void adminCannotDemoteSelf() {
        UserId me = UserId.newId();

        assertThatThrownBy(() -> service().update(new UpdateUserCommand(me, me, "Admin Demo", Role.USER, true)))
                .isInstanceOf(CannotDemoteSelfException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void deactivatingAnAlreadyInactiveUserDoesNothing() {
        User user = existing(Role.USER, false);
        when(repo.findById(user.id())).thenReturn(Optional.of(user));

        service().deactivate(user.id(), UserId.newId());

        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void updatePublishesUserChanged() {
        User user = existing(Role.USER, true);
        when(repo.findById(user.id())).thenReturn(Optional.of(user));
        when(repo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User saved = service()
                .update(new UpdateUserCommand(user.id(), UserId.newId(), "Ana María", Role.ADMIN, true));

        assertThat(saved.fullName()).isEqualTo("Ana María");
        assertThat(saved.role()).isEqualTo(Role.ADMIN);
        assertThat(saved.updatedAt()).isEqualTo(NOW);
        verify(publisher)
                .publish(argThat(e -> e instanceof UserChanged u && u.userId().equals(user.id())
                        && u.occurredAt().equals(NOW)));
    }

    @Test
    void adminMayUpdateOwnProfileWhileStayingActive() {
        User me = existing(Role.ADMIN, true);
        when(repo.findById(me.id())).thenReturn(Optional.of(me));
        when(repo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User saved = service().update(new UpdateUserCommand(me.id(), me.id(), "Ana Admin", Role.ADMIN, true));

        assertThat(saved.fullName()).isEqualTo("Ana Admin");
    }

    @Test
    void deactivateDisablesUserAndPublishesUserChanged() {
        User user = existing(Role.USER, true);
        when(repo.findById(user.id())).thenReturn(Optional.of(user));
        when(repo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service().deactivate(user.id(), UserId.newId());

        verify(repo).save(argThat(u -> !u.active() && u.id().equals(user.id())));
        verify(publisher).publish(argThat(e -> e instanceof UserChanged u && u.userId().equals(user.id())));
    }

    @Test
    void missingUserOnUpdateDeactivateAndGetThrowsNotFound() {
        UserId id = UserId.newId();
        UserId admin = UserId.newId();
        when(repo.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(new UpdateUserCommand(id, admin, "Ana Pérez", Role.USER, true)))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> service().deactivate(id, admin)).isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> service().get(id)).isInstanceOf(UserNotFoundException.class);
        verify(repo, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void getAndListDelegateToRepository() {
        User user = existing(Role.USER, true);
        when(repo.findById(user.id())).thenReturn(Optional.of(user));
        PageQuery query = new PageQuery(0, 10, "createdAt", false);
        PageResult<User> page = new PageResult<>(List.of(user), 0, 10, 1);
        when(repo.findAll(Role.USER, true, query)).thenReturn(page);

        assertThat(service().get(user.id())).isSameAs(user);
        assertThat(service().list(Role.USER, true, query)).isSameAs(page);
    }

    @Test
    void useCaseMethodsAreTransactional() {
        int checked = 0;
        for (Method m : UserService.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                assertThat(m.isAnnotationPresent(Transactional.class))
                        .as("method %s", m.getName())
                        .isTrue();
                checked++;
            }
        }
        assertThat(checked).isEqualTo(6);
    }
}
