package com.flockenergy.meterops.dto;

import java.util.List;

/**
 * Generic paginated response wrapper used by all list endpoints.
 *
 * @param <T> the type of items in this page
 */
public record PageResponse<T>(
        List<T> data,
        int total,
        int page,
        int pageSize,
        int totalPages
) {
    /**
     * Factory helper to create a {@code PageResponse} from a full list
     * by slicing into the requested page.
     */
    public static <T> PageResponse<T> of(List<T> allItems, int page, int pageSize) {
        int total = allItems.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
        int safePage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = (safePage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<T> slice = fromIndex < total ? allItems.subList(fromIndex, toIndex) : List.of();
        return new PageResponse<>(slice, total, safePage, pageSize, totalPages);
    }
}
