package com.neko.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Stable pagination envelope. Used instead of serialising Spring Data Page
 * directly, whose JSON shape is not part of its public contract.
 */
@Schema(name = "PageResponse", description = "A page of results")
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / (double) size);
        return new PageResponse<>(content, page, size, totalElements, totalPages,
                page == 0, page >= totalPages - 1);
    }
}
