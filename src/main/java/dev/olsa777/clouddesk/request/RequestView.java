package dev.olsa777.clouddesk.request;

import java.time.Instant;

public record RequestView(Long id, String title, String description, Environment environment,
                          int cpu, int memoryGb, int storageGb, RequestStatus status,
                          Instant createdAt, Instant updatedAt) {
    public static RequestView from(ResourceRequest request) {
        return new RequestView(request.getId(), request.getTitle(), request.getDescription(),
                request.getEnvironment(), request.getCpu(), request.getMemoryGb(),
                request.getStorageGb(), request.getStatus(), request.getCreatedAt(), request.getUpdatedAt());
    }
}
