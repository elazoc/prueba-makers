package com.wecaelum.prestemos.infrastructure.adapter.out.persistence;

import com.wecaelum.prestemos.application.model.PageQuery;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Maps the sort field of a {@link PageQuery} to entity properties, rejecting anything not listed. */
final class SortWhitelist {

    private static final Set<String> LOAN_FIELDS = Set.of("requestedAt", "amount", "status");
    private static final Set<String> USER_FIELDS = Set.of("createdAt", "email", "fullName");

    private SortWhitelist() {}

    static Pageable loans(PageQuery query) {
        return loans(query, "");
    }

    /**
     * {@code prefix} is prepended to every sort property: the JPQL alias with a dot (for example {@code "l."})
     * when the query needs it, or empty when Spring Data resolves the property by itself (derived queries
     * and single-entity {@code @Query}).
     */
    static Pageable loans(PageQuery query, String prefix) {
        return page(query, LOAN_FIELDS, "requestedAt", prefix);
    }

    static Pageable users(PageQuery query) {
        return page(query, USER_FIELDS, "createdAt", "");
    }

    private static Pageable page(PageQuery query, Set<String> allowed, String defaultField, String prefix) {
        String field = query.sortField();
        Sort.Direction direction;
        if (field == null) {
            field = defaultField;
            direction = Sort.Direction.DESC;
        } else {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("No se puede ordenar por '" + field + "'.");
            }
            direction = query.ascending() ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
        Sort sort = Sort.by(direction, prefix + field).and(Sort.by(Sort.Direction.ASC, prefix + "id"));
        return PageRequest.of(query.page(), query.size(), sort);
    }
}
