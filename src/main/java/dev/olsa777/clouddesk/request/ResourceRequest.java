package dev.olsa777.clouddesk.request;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "resource_requests")
public class ResourceRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, length = 2000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;
    @Column(nullable = false)
    private int cpu;
    @Column(nullable = false)
    private int memoryGb;
    @Column(nullable = false)
    private int storageGb;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    // If two operators update the same row concurrently, one receives HTTP 409.
    @Version
    private long version;

    protected ResourceRequest() { }

    public ResourceRequest(CreateRequest input) {
        this(input, Instant.now());
    }

    // Explicit time keeps precision tests independent of the OS/JDK clock.
    ResourceRequest(CreateRequest input, Instant timestamp) {
        title = input.title().trim();
        description = input.description().trim();
        environment = input.environment();
        cpu = input.cpu();
        memoryGb = input.memoryGb();
        storageGb = input.storageGb();
        status = RequestStatus.NEW;
        // H2 and PostgreSQL store microseconds; normalize before returning a DTO.
        createdAt = timestamp.truncatedTo(ChronoUnit.MICROS);
        updatedAt = createdAt;
    }

    public void transitionTo(RequestStatus next) {
        transitionTo(next, Instant.now());
    }

    void transitionTo(RequestStatus next, Instant timestamp) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidTransitionException("Переход " + status + " → " + next + " запрещён");
        }
        status = next;
        updatedAt = timestamp.truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Environment getEnvironment() { return environment; }
    public int getCpu() { return cpu; }
    public int getMemoryGb() { return memoryGb; }
    public int getStorageGb() { return storageGb; }
    public RequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
