package dev.olsa777.clouddesk.api;

import java.util.Map;

public record ApiError(int status, String message, Map<String, String> fieldErrors) { }
