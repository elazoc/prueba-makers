package com.wecaelum.prestemos.infrastructure.adapter.in.web.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wecaelum.prestemos.application.model.PageQuery;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PageQueryFactoryTest {

    private static final Set<String> ALLOWED = Set.of("requestedAt", "amount", "status");

    @Test
    void defaultsApplyWhenNothingIsGiven() {
        assertThat(PageQueryFactory.of(null, null, null, ALLOWED, "requestedAt,desc"))
                .isEqualTo(new PageQuery(0, 20, "requestedAt", false));
    }

    @Test
    void parsesExplicitValues() {
        assertThat(PageQueryFactory.of(2, 100, "amount,asc", ALLOWED, "requestedAt,desc"))
                .isEqualTo(new PageQuery(2, 100, "amount", true));
        assertThat(PageQueryFactory.of(0, 1, "status", ALLOWED, "requestedAt,desc").ascending()).isTrue();
        assertThat(PageQueryFactory.of(0, 1, "status,DESC", ALLOWED, "requestedAt,desc").ascending()).isFalse();
    }

    @Test
    void rejectsAbuseWithSpanishMessages() {
        assertThatThrownBy(() -> PageQueryFactory.of(-1, null, null, ALLOWED, "requestedAt,desc"))
                .hasMessage("La página debe ser 0 o mayor.");
        assertThatThrownBy(() -> PageQueryFactory.of(0, 0, null, ALLOWED, "requestedAt,desc"))
                .hasMessage("El tamaño de página debe estar entre 1 y 100.");
        assertThatThrownBy(() -> PageQueryFactory.of(0, 101, null, ALLOWED, "requestedAt,desc"))
                .hasMessage("El tamaño de página debe estar entre 1 y 100.");
        assertThatThrownBy(() -> PageQueryFactory.of(0, 10, "passwordHash", ALLOWED, "requestedAt,desc"))
                .hasMessage("No se puede ordenar por 'passwordHash'.");
        assertThatThrownBy(() -> PageQueryFactory.of(0, 10, "amount,sideways", ALLOWED, "requestedAt,desc"))
                .hasMessage("El orden debe tener el formato campo,asc o campo,desc.");
        assertThatThrownBy(() -> PageQueryFactory.of(0, 10, "amount,asc,x", ALLOWED, "requestedAt,desc"))
                .hasMessage("El orden debe tener el formato campo,asc o campo,desc.");
    }
}
