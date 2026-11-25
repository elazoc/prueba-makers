package com.wecaelum.prestemos.infrastructure.adapter.in.web.user;

import com.wecaelum.prestemos.application.port.in.CreateUserCommand;
import com.wecaelum.prestemos.application.port.in.ManageUsersUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateUserCommand;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.auth.UserResponse;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.common.PageQueryFactory;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.common.PageResponse;
import com.wecaelum.prestemos.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** User administration. {@code /me} is open to any signed-in user; the rest is ADMIN-only, checked by the security chain. */
@RestController
@RequestMapping("/api/v1/users")
class UserController {

    static final Set<String> SORT_FIELDS = Set.of("createdAt", "email", "fullName");
    static final String DEFAULT_SORT = "createdAt,desc";

    private final ManageUsersUseCase users;

    UserController(ManageUsersUseCase users) {
        this.users = users;
    }

    @GetMapping("/me")
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return UserResponse.from(users.get(me.id()));
    }

    @GetMapping
    PageResponse<UserResponse> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        var query = PageQueryFactory.of(page, size, sort, SORT_FIELDS, DEFAULT_SORT);
        return PageResponse.from(users.list(role, active, query), UserResponse::from);
    }

    @GetMapping("/{id}")
    UserResponse get(@PathVariable String id) {
        return UserResponse.from(users.get(UserId.of(id)));
    }

    @PostMapping
    ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        User created = users.create(
                new CreateUserCommand(request.email(), request.password(), request.fullName(), request.role()));
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id().value()))
                .body(UserResponse.from(created));
    }

    @PutMapping("/{id}")
    UserResponse update(
            @AuthenticationPrincipal AuthenticatedUser me,
            @PathVariable String id,
            @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(users.update(new UpdateUserCommand(
                UserId.of(id), me.id(), request.fullName(), request.role(), request.active())));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> deactivate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        users.deactivate(UserId.of(id), me.id());
        return ResponseEntity.noContent().build();
    }
}
