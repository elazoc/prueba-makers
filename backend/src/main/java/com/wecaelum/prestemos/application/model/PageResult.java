package com.wecaelum.prestemos.application.model;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int page, int size, long totalElements) implements Serializable {

    public PageResult {
        if (page < 0) {
            throw new IllegalArgumentException("La página no puede ser negativa.");
        }
        if (size < 1) {
            throw new IllegalArgumentException("El tamaño de página debe ser al menos 1.");
        }
        if (totalElements < 0) {
            throw new IllegalArgumentException("El total de elementos no puede ser negativo.");
        }
        items = List.copyOf(items);
    }

    public int totalPages() {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceilDiv(totalElements, (long) size));
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }
}
