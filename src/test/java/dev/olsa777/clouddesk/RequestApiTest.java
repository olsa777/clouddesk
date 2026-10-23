package dev.olsa777.clouddesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.olsa777.clouddesk.request.RequestRepository;
import dev.olsa777.clouddesk.request.RequestStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired RequestRepository repository;

    private static final String VALID = """
            {"title":"  API для учебного проекта  ","description":"  Тестовое окружение  ",
             "environment":"DEVELOPMENT","cpu":2,"memoryGb":4,"storageGb":40}
            """;

    @BeforeEach
    void clearDatabase() { repository.deleteAll(); }

    @Test
    void createsRetrievesAndProvisionsARequest() throws Exception {
        var result = mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("API для учебного проекта"))
                .andExpect(jsonPath("$.description").value("Тестовое окружение"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andReturn();
        var created = json.readTree(result.getResponse().getContentAsString());
        long id = created.get("id").asLong();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/api/requests/" + id);
        mvc.perform(get("/api/requests/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        transition(id, "APPROVED", 200);
        transition(id, "PROVISIONED", 200);
        mvc.perform(get("/api/requests/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROVISIONED"))
                .andExpect(jsonPath("$.createdAt").value(created.get("createdAt").asText()));
    }

    @Test
    void filtersListsNewestFirstAndCountsOnlyReservedResources() throws Exception {
        long pending = create();
        long approved = create();
        long provisioned = create();
        long rejected = create();
        transition(approved, "APPROVED", 200);
        transition(provisioned, "APPROVED", 200);
        transition(provisioned, "PROVISIONED", 200);
        transition(rejected, "REJECTED", 200);
        mvc.perform(get("/api/requests")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(rejected));
        mvc.perform(get("/api/requests").param("status", "NEW")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(pending));
        mvc.perform(get("/api/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.approved").value(1))
                .andExpect(jsonPath("$.provisioned").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.totalCpu").value(4))
                .andExpect(jsonPath("$.totalMemoryGb").value(8))
                .andExpect(jsonPath("$.totalStorageGb").value(80));
    }

    @Test
    void rejectsConflictingTransitionsWithoutChangingStoredState() throws Exception {
        long id = create();
        transition(id, "PROVISIONED", 409);
        transition(id, "APPROVED", 200);
        transition(id, "REJECTED", 409);
        transition(id, "APPROVED", 409);
        mvc.perform(get("/api/requests/{id}", id)).andExpect(jsonPath("$.status").value("APPROVED"));
        transition(id, "PROVISIONED", 200);
        transition(id, "NEW", 409);
        transition(id, "REJECTED", 409);
        long rejected = create();
        transition(rejected, "REJECTED", 200);
        transition(rejected, "APPROVED", 409);
    }

    @Test
    void protectsAgainstLostUpdatesFromTwoStaleCopies() throws Exception {
        long id = create();
        var first = repository.findById(id).orElseThrow();
        var stale = repository.findById(id).orElseThrow();
        first.transitionTo(RequestStatus.APPROVED);
        repository.saveAndFlush(first);
        stale.transitionTo(RequestStatus.REJECTED);
        assertThatThrownBy(() -> repository.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.APPROVED);
    }

    @Test
    void reportsAllInvalidFieldsAndDoesNotPersistRequest() throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content("""
                {"title":" ","description":" ","environment":null,"cpu":-1,"memoryGb":129,"storageGb":9}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists())
                .andExpect(jsonPath("$.fieldErrors.environment").exists())
                .andExpect(jsonPath("$.fieldErrors.cpu").exists())
                .andExpect(jsonPath("$.fieldErrors.memoryGb").exists())
                .andExpect(jsonPath("$.fieldErrors.storageGb").exists());
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "", "{broken", "[]",
            "{\"environment\":\"MOON\"}", "{\"environment\":0}", "{\"cpu\":1.5}"})
    void rejectsMissingMalformedAndIncorrectlyTypedBodies(String input) throws Exception {
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.fieldErrors").isMap());
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsUnknownFieldsAndOversizeStrings() throws Exception {
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(VALID);
        input.put("unexpected", "value");
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isBadRequest());
        input.remove("unexpected");
        input.put("title", "x".repeat(121));
        input.put("description", "x".repeat(2001));
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void validatesTheOtherResourceBoundaries() throws Exception {
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(VALID);
        input.put("cpu", 33);
        input.put("memoryGb", 0);
        input.put("storageGb", 2001);
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(input.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cpu").exists())
                .andExpect(jsonPath("$.fieldErrors.memoryGb").exists())
                .andExpect(jsonPath("$.fieldErrors.storageGb").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "", "{\"status\":\"UNKNOWN\"}", "{\"status\":null}"})
    void validatesStatusRequestBody(String input) throws Exception {
        long id = create();
        mvc.perform(patch("/api/requests/{id}/status", id).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(RequestStatus.NEW);
    }

    @Test
    void returnsConsistentErrorsForMissingIdsFiltersRoutesAndUnsupportedMethods() throws Exception {
        mvc.perform(get("/api/requests/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404)).andExpect(jsonPath("$.fieldErrors").isMap());
        transition(999999, "APPROVED", 404);
        mvc.perform(get("/api/requests").param("status", "unknown"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mvc.perform(get("/api/requests/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(delete("/api/requests/1")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mvc.perform(post("/api/requests").contentType(MediaType.TEXT_PLAIN).content(VALID))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void emptyDatabaseHasZeroStatsAndEmptyLists() throws Exception {
        mvc.perform(get("/api/requests")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/stats")).andExpect(status().isOk()).andExpect(content().json("""
                {"total":0,"pending":0,"approved":0,"provisioned":0,"rejected":0,
                 "totalCpu":0,"totalMemoryGb":0,"totalStorageGb":0}
                """));
    }

    private long create() throws Exception {
        var result = mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void transition(long id, String next, int expectedStatus) throws Exception {
        var result = mvc.perform(patch("/api/requests/{id}/status", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + next + "\"}"))
                .andExpect(status().is(expectedStatus));
        if (expectedStatus == 200) result.andExpect(jsonPath("$.status").value(next));
        else result.andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.message").isString()).andExpect(jsonPath("$.fieldErrors").isMap());
    }
}
