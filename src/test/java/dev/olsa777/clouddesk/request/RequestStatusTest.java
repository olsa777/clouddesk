package dev.olsa777.clouddesk.request;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class RequestStatusTest {
    @ParameterizedTest
    @CsvSource({
            "NEW,NEW,false", "NEW,APPROVED,true", "NEW,REJECTED,true", "NEW,PROVISIONED,false",
            "APPROVED,NEW,false", "APPROVED,APPROVED,false", "APPROVED,REJECTED,false", "APPROVED,PROVISIONED,true",
            "REJECTED,NEW,false", "REJECTED,APPROVED,false", "REJECTED,REJECTED,false", "REJECTED,PROVISIONED,false",
            "PROVISIONED,NEW,false", "PROVISIONED,APPROVED,false", "PROVISIONED,REJECTED,false", "PROVISIONED,PROVISIONED,false"
    })
    void allowsOnlyDocumentedTransitions(RequestStatus from, RequestStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
