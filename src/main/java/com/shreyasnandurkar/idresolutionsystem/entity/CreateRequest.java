package com.shreyasnandurkar.idresolutionsystem.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRequest(

        @NotBlank(message = "URL must not be blank")
        @Pattern(
                regexp = "^https?://.*",
                message = "URL must start with http:// or https://"
        )
        String originalUrl
) {
}
