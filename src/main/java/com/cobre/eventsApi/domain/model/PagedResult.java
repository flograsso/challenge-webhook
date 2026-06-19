package com.cobre.eventsApi.domain.model;

import java.util.List;

public record PagedResult<T>(
        List<T> data,
        int page,
        int pageSize,
        long totalItems,
        int totalPages
) {}
