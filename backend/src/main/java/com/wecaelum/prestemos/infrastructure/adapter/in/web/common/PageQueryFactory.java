package com.wecaelum.prestemos.infrastructure.adapter.in.web.common;

import com.wecaelum.prestemos.application.model.PageQuery;
import java.util.Set;

/** Builds a {@link PageQuery} from raw request parameters, rejecting abusive values with Spanish messages. */
public final class PageQueryFactory {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private PageQueryFactory() {}

    /**
     * @param sort {@code field,asc|desc}; {@code field} alone means ascending. Null or blank uses {@code defaultSort}.
     * @throws IllegalArgumentException when a value is out of range or the field is not in {@code allowed}
     */
    public static PageQuery of(Integer page, Integer size, String sort, Set<String> allowed, String defaultSort) {
        int pageNumber = page == null ? 0 : page;
        if (pageNumber < 0) {
            throw new IllegalArgumentException("La página debe ser 0 o mayor.");
        }
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new IllegalArgumentException("El tamaño de página debe estar entre 1 y 100.");
        }
        String spec = sort == null || sort.isBlank() ? defaultSort : sort;
        String[] parts = spec.split(",", -1);
        if (parts.length > 2) {
            throw invalidOrder();
        }
        String field = parts[0].trim();
        boolean ascending = true;
        if (parts.length == 2) {
            String direction = parts[1].trim();
            if (direction.equalsIgnoreCase("desc")) {
                ascending = false;
            } else if (!direction.equalsIgnoreCase("asc")) {
                throw invalidOrder();
            }
        }
        if (!allowed.contains(field)) {
            throw new IllegalArgumentException("No se puede ordenar por '" + field + "'.");
        }
        return new PageQuery(pageNumber, pageSize, field, ascending);
    }

    private static IllegalArgumentException invalidOrder() {
        return new IllegalArgumentException("El orden debe tener el formato campo,asc o campo,desc.");
    }
}
