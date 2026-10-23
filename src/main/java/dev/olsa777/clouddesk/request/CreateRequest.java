package dev.olsa777.clouddesk.request;

import jakarta.validation.constraints.*;

public record CreateRequest(
        @NotBlank(message = "Укажите название")
        @Size(max = 120, message = "Не более 120 символов") String title,
        @NotBlank(message = "Опишите назначение ресурсов")
        @Size(max = 2000, message = "Не более 2000 символов") String description,
        @NotNull(message = "Выберите окружение") Environment environment,
        @NotNull(message = "Укажите CPU") @Min(value = 1, message = "Минимум 1 vCPU")
        @Max(value = 32, message = "Максимум 32 vCPU") Integer cpu,
        @NotNull(message = "Укажите память") @Min(value = 1, message = "Минимум 1 ГБ")
        @Max(value = 128, message = "Максимум 128 ГБ") Integer memoryGb,
        @NotNull(message = "Укажите диск") @Min(value = 10, message = "Минимум 10 ГБ")
        @Max(value = 2000, message = "Максимум 2000 ГБ") Integer storageGb
) { }
