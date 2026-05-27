package com.paylinker.api.secure.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkValidateRequest(@NotBlank String token) {}
