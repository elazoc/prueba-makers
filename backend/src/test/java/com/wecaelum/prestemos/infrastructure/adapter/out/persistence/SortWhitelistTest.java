package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wecaelum.prestemos.application.model.PageQuery;
import org.junit.jupiter.api.Test;

class SortWhitelistTest {

    @Test
    void rejectedFieldHasSpanishClientFacingMessage() {
        assertThatThrownBy(() -> SortWhitelist.loans(new PageQuery(0, 10, "version", true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No se puede ordenar por 'version'.");
        assertThatThrownBy(() -> SortWhitelist.users(new PageQuery(0, 10, "passwordHash", true)))
                .hasMessage("No se puede ordenar por 'passwordHash'.");
    }
}
