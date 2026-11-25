package com.wecaelum.prestemos.infrastructure.adapter.in.web.common;

import com.wecaelum.prestemos.application.model.PageResult;
import java.util.List;
import java.util.function.Function;

/** Flat page envelope of the API: {@code {content, page, size, totalElements, totalPages}}. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        return new PageResponse<>(
                result.items().stream().map(mapper).toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());
    }
}
