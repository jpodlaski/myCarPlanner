package com.sanproject.service_planner.dto;

import java.util.List;

/** One page of results; `page` is zero-based. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
}
