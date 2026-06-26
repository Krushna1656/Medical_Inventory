package com.standalone.medadmin.model;

public record StockMovement(
        long id,
        String medicineName,
        String type,
        int quantity,
        String referenceNo,
        String note,
        String createdAt
) {
}
