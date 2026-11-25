package com.wecaelum.prestemos.application.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageModelTest {

    @Test
    void pageQueryRejectsNegativePageAndSizeBelowOne() {
        assertThatThrownBy(() -> new PageQuery(-1, 10, "id", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(0, 0, "id", true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PageQuery(0, 1, null, true).size()).isEqualTo(1);
    }

    @Test
    void totalPagesIsCeilAndZeroWhenEmpty() {
        assertThat(new PageResult<>(List.of(), 0, 10, 0).totalPages()).isZero();
        assertThat(new PageResult<>(List.of(1), 0, 10, 10).totalPages()).isEqualTo(1);
        assertThat(new PageResult<>(List.of(1), 0, 10, 11).totalPages()).isEqualTo(2);
    }

    @Test
    void itemsAreCopiedAndMapKeepsPaging() {
        List<Integer> source = new ArrayList<>(List.of(1, 2));
        PageResult<Integer> page = new PageResult<>(source, 1, 2, 5);
        source.add(3);

        assertThat(page.items()).containsExactly(1, 2);
        assertThatThrownBy(() -> page.items().add(9)).isInstanceOf(UnsupportedOperationException.class);
        PageResult<String> mapped = page.map(String::valueOf);
        assertThat(mapped.items()).containsExactly("1", "2");
        assertThat(mapped.page()).isEqualTo(1);
        assertThat(mapped.totalElements()).isEqualTo(5);
    }

    @Test
    void pageResultRejectsInvalidPagingAndDoesNotOverflow() {
        assertThatThrownBy(() -> new PageResult<>(List.of(), -1, 10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageResult<>(List.of(), 0, 10, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new PageResult<>(List.of(), 0, 1, Long.MAX_VALUE).totalPages()).isEqualTo(Integer.MAX_VALUE);
        assertThat(new PageResult<>(List.of(), 0, Integer.MAX_VALUE, Integer.MAX_VALUE + 1L).totalPages())
                .isEqualTo(2);
    }
}
