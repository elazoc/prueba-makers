package com.wecaelum.prestemos.application.model;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, int page, int size, long totalElements) implements Serializable {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
    }

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }
}
