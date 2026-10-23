package dev.olsa777.clouddesk.request;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RequestService {
    private final RequestRepository repository;

    public RequestService(RequestRepository repository) { this.repository = repository; }

    public List<RequestView> list(RequestStatus status) {
        var requests = status == null ? repository.findAllByOrderByCreatedAtDescIdDesc()
                : repository.findByStatusOrderByCreatedAtDescIdDesc(status);
        return requests.stream().map(RequestView::from).toList();
    }

    public RequestView get(long id) { return RequestView.from(find(id)); }

    @Transactional
    public RequestView create(CreateRequest input) {
        return RequestView.from(repository.save(new ResourceRequest(input)));
    }

    @Transactional
    public RequestView updateStatus(long id, RequestStatus next) {
        var request = find(id);
        request.transitionTo(next);
        // Flush inside this transaction so optimistic-lock conflicts become an HTTP 409.
        repository.flush();
        return RequestView.from(request);
    }

    public StatsView stats() {
        var requests = repository.findAll();
        long pending = 0, approved = 0, provisioned = 0, rejected = 0;
        long cpu = 0, memory = 0, storage = 0;
        for (var request : requests) {
            switch (request.getStatus()) {
                case NEW -> pending++;
                case APPROVED -> approved++;
                case PROVISIONED -> provisioned++;
                case REJECTED -> rejected++;
            }
            if (request.getStatus().reservesResources()) {
                cpu += request.getCpu();
                memory += request.getMemoryGb();
                storage += request.getStorageGb();
            }
        }
        return new StatsView(requests.size(), pending, approved, provisioned, rejected, cpu, memory, storage);
    }

    private ResourceRequest find(long id) {
        return repository.findById(id).orElseThrow(() -> new RequestNotFoundException(id));
    }
}
