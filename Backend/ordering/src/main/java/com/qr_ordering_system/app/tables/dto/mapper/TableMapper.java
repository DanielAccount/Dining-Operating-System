package com.qr_ordering_system.app.tables.dto.mapper;

import com.qr_ordering_system.app.tables.domain.TableEntity;
import com.qr_ordering_system.app.tables.dto.response.TableResponse;

public final class TableMapper {

    private TableMapper() {
    }

    public static TableResponse toResponse(TableEntity table, String publicBaseUrl) {
        // Config values are for humans first: tolerate a trailing slash so a
        // base URL of "http://localhost:4200/" cannot produce "//t/abc".
        String base = publicBaseUrl == null
                ? ""
                : publicBaseUrl.trim().replaceAll("/+$", "");

        return new TableResponse(
                table.getId(),
                table.getTableNumber(),
                table.getQrSlug(),
                base + "/t/" + table.getQrSlug(),
                table.getCreatedAt(),
                table.getUpdatedAt()
        );
    }
}
