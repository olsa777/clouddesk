package dev.olsa777.clouddesk.request;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RequestTimestampPersistenceTest {
    @Autowired RequestRepository repository;
    @Autowired EntityManager entityManager;

    @ParameterizedTest
    @CsvSource({
            "2026-10-23T08:49:59.317498561Z,2026-10-23T08:49:59.317498Z",
            "2026-10-23T08:49:59.999999999Z,2026-10-23T08:49:59.999999Z",
            "2026-10-23T08:49:59.000000001Z,2026-10-23T08:49:59Z"
    })
    void createAndUpdateKeepExactTimestampsAcrossDatabaseRoundTrips(String input, String expected) {
        var nanoseconds = Instant.parse(input);
        var microseconds = Instant.parse(expected);
        var request = new ResourceRequest(new CreateRequest("Precision test", "Database round trip",
                Environment.DEVELOPMENT, 2, 4, 40), nanoseconds);

        // A response created before reading the database must already have its stored precision.
        var createdResponse = RequestView.from(request);
        assertThat(createdResponse.createdAt()).isEqualTo(microseconds);
        assertThat(createdResponse.updatedAt()).isEqualTo(microseconds);
        repository.saveAndFlush(request);
        long id = request.getId();
        entityManager.clear(); // Force SQL instead of returning the first-level cache entry.
        var reloaded = repository.findById(id).orElseThrow();
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdResponse.createdAt());
        assertThat(reloaded.getUpdatedAt()).isEqualTo(createdResponse.updatedAt());

        reloaded.transitionTo(RequestStatus.APPROVED, nanoseconds.plusSeconds(1));
        var updatedResponse = RequestView.from(reloaded);
        assertThat(updatedResponse.updatedAt()).isEqualTo(microseconds.plusSeconds(1));
        repository.flush();
        entityManager.clear();
        var updated = repository.findById(id).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(createdResponse.createdAt());
        assertThat(updated.getUpdatedAt()).isEqualTo(updatedResponse.updatedAt());
        assertThat(updated.getStatus()).isEqualTo(RequestStatus.APPROVED);
    }
}
