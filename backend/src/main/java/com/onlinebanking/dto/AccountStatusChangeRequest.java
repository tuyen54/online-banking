package com.onlinebanking.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request body for administrative account security status changes (lock/unlock).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountStatusChangeRequest {

    @NotBlank(message = "Administrative reason is mandatory")
    private String reason;
}

