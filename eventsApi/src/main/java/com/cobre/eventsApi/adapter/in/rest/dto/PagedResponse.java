package com.cobre.eventsApi.adapter.in.rest.dto;

import com.cobre.eventsApi.domain.model.PagedResult;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
        List<T> data,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {
    public static <D, T> PagedResponse<T> from(PagedResult<D> result, Function<D, T> mapper) {
        return new PagedResponse<>(
                result.data().stream().map(mapper).toList(),
                result.page(),
                result.pageSize(),
                result.totalItems(),
                result.totalPages()
        );
    }
}
