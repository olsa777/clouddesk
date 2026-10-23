package dev.olsa777.clouddesk.request;

public record StatsView(long total, long pending, long approved, long provisioned, long rejected,
                        long totalCpu, long totalMemoryGb, long totalStorageGb) { }
