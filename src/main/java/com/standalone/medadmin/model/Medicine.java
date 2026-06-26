package com.standalone.medadmin.model;

import java.time.LocalDate;

public record Medicine(
        long id,
        String name,
        int quantity,
        double price,
        LocalDate expiryDate
) {
}
