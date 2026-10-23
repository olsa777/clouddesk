package dev.olsa777.clouddesk.request;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api")
public class RequestController {
    private final RequestService service;

    public RequestController(RequestService service) { this.service = service; }

    @GetMapping("/requests")
    public List<RequestView> list(@RequestParam(required = false) RequestStatus status) {
        return service.list(status);
    }

    @GetMapping("/requests/{id}")
    public RequestView get(@PathVariable long id) { return service.get(id); }

    @PostMapping("/requests")
    public ResponseEntity<RequestView> create(@Valid @RequestBody CreateRequest input) {
        var created = service.create(input);
        return ResponseEntity.created(URI.create("/api/requests/" + created.id())).body(created);
    }

    @PatchMapping("/requests/{id}/status")
    public RequestView updateStatus(@PathVariable long id, @Valid @RequestBody UpdateStatus input) {
        return service.updateStatus(id, input.status());
    }

    @GetMapping("/stats")
    public StatsView stats() { return service.stats(); }
}
