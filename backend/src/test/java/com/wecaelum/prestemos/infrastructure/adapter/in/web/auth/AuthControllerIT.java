package com.wecaelum.prestemos.infrastructure.adapter.in.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** Runs every test in a rolled-back transaction, so registered users do not leak into other tests. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIT {

    @Autowired
    private MockMvc mvc;

    @Test
    void seededUserLogsIn() throws Exception {
        login("usuario@test.com", "123")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.accessToken").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.user.email").value("usuario@test.com"))
                .andExpect(jsonPath("$.user.fullName").value("Usuario Demo"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.active").value(true))
                .andExpect(jsonPath("$.user.createdAt").value("2026-11-01T10:00:00Z"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void seededAdminLogsInWithAdminRole() throws Exception {
        login("admin@test.com", "123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        login("  Usuario@Test.com ", "123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("usuario@test.com"));
    }

    @Test
    void wrongPasswordIs401ProblemJson() throws Exception {
        login("usuario@test.com", "1234")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void unknownEmailIsTheSame401AsWrongPassword() throws Exception {
        login("nadie@test.com", "123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void blankLoginFieldsAre400() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
    }

    @Test
    void registerCreatesUserRoleAndRejectsShortPassword() throws Exception {
        String email = "nuevo-" + UUID.randomUUID() + "@test.com";

        MvcResult created = register(email, "12345678", "Nueva Persona")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Nueva Persona"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/users/" + id);

        login(email, "12345678").andExpect(status().isOk());

        register("otro-" + UUID.randomUUID() + "@test.com", "1234567", "Otra Persona")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("password")));
    }

    @Test
    void registerDuplicateIgnoringCaseIs409() throws Exception {
        register("  USUARIO@test.com ", "12345678", "Usuario Copia")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerRejectsInvalidEmailAndName() throws Exception {
        register("no-es-email", "12345678", "A")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("fullName")));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private ResultActions register(String email, String password, String fullName) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password
                        + "\",\"fullName\":\"" + fullName + "\"}"));
    }
}
