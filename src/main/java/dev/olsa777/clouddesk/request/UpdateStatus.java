package dev.olsa777.clouddesk.request;

import jakarta.validation.constraints.NotNull;

public record UpdateStatus(@NotNull(message = "Укажите статус") RequestStatus status) { }
