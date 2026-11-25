package com.wecaelum.prestemos.infrastructure.adapter.in.web.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wecaelum.prestemos.application.model.PageQuery;
import com.wecaelum.prestemos.application.model.PageResult;
import com.wecaelum.prestemos.application.port.in.CreateUserCommand;
import com.wecaelum.prestemos.application.port.in.ManageUsersUseCase;
import com.wecaelum.prestemos.application.port.in.UpdateUserCommand;
import com.wecaelum.prestemos.domain.exception.CannotDeactivateSelfException;
import com.wecaelum.prestemos.domain.model.Email;
import com.wecaelum.prestemos.domain.model.Role;
import com.wecaelum.prestemos.domain.model.User;
import com.wecaelum.prestemos.domain.model.UserId;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.error.GlobalExceptionHandler;
import com.wecaelum.prestemos.infrastructure.adapter.in.web.error.ProblemDetailFactory;
import com.wecaelum.prestemos.infrastructure.config.ClockConfig;
import com.wecaelum.prestemos.infrastructure.security.AuthenticatedUser;
import com.wecaelum.prestemos.infrastructure.security.UserAuthSnapshotProvider;
import com.wecaelum.prestemos.infrastructure.security.WebSliceSecurityConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(controllers = UserController.class)
@Import({WebSliceSecurityConfig.class, GlobalExceptionHandler.class, ProblemDetailFactory.class, ClockConfig.class})
class UserControllerTest {

    static final AuthenticatedUser USER =
            new AuthenticatedUser(UserId.of("00000000-0000-0000-0000-000000000001"), new Email("usuario@test.com"), Role.USER);
    static final AuthenticatedUser ADMIN =
            new AuthenticatedUser(UserId.of("00000000-0000-0000-0000-000000000002"), new Email("admin@test.com"), Role.ADMIN);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ManageUsersUseCase users;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    UserAuthSnapshotProvider snapshots;

    static RequestPostProcessor as(AuthenticatedUser who) {
        return authentication(new UsernamePasswordAuthenticationToken(
                who, null, AuthorityUtils.createAuthorityList("ROLE_" + who.role().name())));
    }

    static User user(AuthenticatedUser who, String name, boolean active) {
        Instant now = Instant.parse("2026-11-25T10:00:00Z");
        return new User(who.id(), who.email(), "$2a$10$secrethashsecrethash", name, who.role(), active, now, now, 0L);
    }

    @Test
    void meReturnsOwnProfileForAnyRole() throws Exception {
        for (AuthenticatedUser who : List.of(USER, ADMIN)) {
            when(users.get(who.id())).thenReturn(user(who, "Persona Demo", true));
            mvc.perform(get("/api/v1/users/me").with(as(who)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(who.id().value().toString()))
                    .andExpect(jsonPath("$.email").value(who.email().value()))
                    .andExpect(jsonPath("$.role").value(who.role().name()))
                    .andExpect(jsonPath("$.active").value(true));
        }
    }

    @Test
    void adminCrud() throws Exception {
        User created = user(USER, "Persona Nueva", true);
        when(users.create(any())).thenReturn(created);
        mvc.perform(post("/api/v1/users").with(as(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"usuario@test.com\",\"password\":\"12345678\",\"fullName\":\"Persona Nueva\",\"role\":\"USER\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/users/" + created.id().value())))
                .andExpect(jsonPath("$.fullName").value("Persona Nueva"));
        ArgumentCaptor<CreateUserCommand> create = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(users).create(create.capture());
        assertThat(create.getValue().rawPassword()).isEqualTo("12345678");
        assertThat(create.getValue().role()).isEqualTo(Role.USER);

        when(users.get(USER.id())).thenReturn(created);
        mvc.perform(get("/api/v1/users/" + USER.id().value()).with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("usuario@test.com"));

        when(users.update(any())).thenReturn(created.changeProfile("Persona Nueva", Role.ADMIN, true, Instant.now()));
        mvc.perform(put("/api/v1/users/" + USER.id().value()).with(as(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Persona Nueva\",\"role\":\"ADMIN\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        ArgumentCaptor<UpdateUserCommand> update = ArgumentCaptor.forClass(UpdateUserCommand.class);
        verify(users).update(update.capture());
        assertThat(update.getValue().id()).isEqualTo(USER.id());
        assertThat(update.getValue().actingAdmin()).isEqualTo(ADMIN.id());
        assertThat(update.getValue().role()).isEqualTo(Role.ADMIN);

        mvc.perform(delete("/api/v1/users/" + USER.id().value()).with(as(ADMIN)))
                .andExpect(status().isNoContent());
        verify(users).deactivate(USER.id(), ADMIN.id());
    }

    @Test
    void listParsesFiltersAndPaging() throws Exception {
        when(users.list(eq(Role.USER), eq(true), any()))
                .thenReturn(new PageResult<>(List.of(user(USER, "Persona Demo", true)), 1, 5, 6));
        mvc.perform(get("/api/v1/users?role=USER&active=true&page=1&size=5&sort=email,asc").with(as(ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("usuario@test.com"))
                .andExpect(jsonPath("$.totalPages").value(2));
        ArgumentCaptor<PageQuery> captor = ArgumentCaptor.forClass(PageQuery.class);
        verify(users).list(any(), any(), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new PageQuery(1, 5, "email", true));

        for (String bad : List.of("sort=passwordHash", "role=NOPE", "active=maybe", "size=1000")) {
            mvc.perform(get("/api/v1/users?" + bad).with(as(ADMIN)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void invalidBodiesAre400() throws Exception {
        for (String body : List.of(
                "{\"email\":\"nope\",\"password\":\"12345678\",\"fullName\":\"Persona\",\"role\":\"USER\"}",
                "{\"email\":\"a@b.com\",\"password\":\"corta\",\"fullName\":\"Persona\",\"role\":\"USER\"}",
                "{\"email\":\"a@b.com\",\"password\":\"12345678\",\"fullName\":\"P\",\"role\":\"USER\"}",
                "{\"email\":\"a@b.com\",\"password\":\"12345678\",\"fullName\":\"Persona\"}")) {
            mvc.perform(post("/api/v1/users").with(as(ADMIN)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(put("/api/v1/users/" + USER.id().value()).with(as(ADMIN)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Persona\",\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(users);
    }

    @Test
    void adminCannotDeactivateSelf() throws Exception {
        doThrow(new CannotDeactivateSelfException()).when(users).deactivate(ADMIN.id(), ADMIN.id());
        mvc.perform(delete("/api/v1/users/" + ADMIN.id().value()).with(as(ADMIN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
    }

    @Test
    void userRoleGets403OnUsersAdmin() throws Exception {
        mvc.perform(get("/api/v1/users").with(as(USER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + ADMIN.id().value()).with(as(USER))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users").with(as(USER)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/" + ADMIN.id().value()).with(as(USER))).andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }

    @Test
    void responsesNeverExposePasswordHash() throws Exception {
        when(users.get(ADMIN.id())).thenReturn(user(ADMIN, "Admin Demo", true));
        when(users.list(any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(user(ADMIN, "Admin Demo", true)), 0, 20, 1));
        for (String url : List.of("/api/v1/users/me", "/api/v1/users/" + ADMIN.id().value(), "/api/v1/users")) {
            mvc.perform(get(url).with(as(ADMIN)))
                    .andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("password"))))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secrethash"))));
        }
    }
}
