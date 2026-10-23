package dev.olsa777.clouddesk.request;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RequestRepository extends JpaRepository<ResourceRequest, Long> {
    List<ResourceRequest> findAllByOrderByCreatedAtDescIdDesc();
    List<ResourceRequest> findByStatusOrderByCreatedAtDescIdDesc(RequestStatus status);
}
