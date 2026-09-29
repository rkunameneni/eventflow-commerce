package io.github.rkunameneni.eventflow.support.web.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ProblemDetailsAdviceTest {

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SampleController())
            .setControllerAdvice(new ProblemDetailsAdvice())
            .build();

    @Test
    void notFoundBecomesA404Problem() throws Exception {
        mvc.perform(get("/things/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:eventflow:problem:not-found"))
                .andExpect(jsonPath("$.detail").value("Thing missing was not found"));
    }

    @Test
    void businessRuleViolationsExposeAStableCode() throws Exception {
        mvc.perform(get("/things/locked"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("THING_LOCKED"));
    }

    @Test
    void concurrentModificationBecomesA409() throws Exception {
        mvc.perform(get("/things/stale"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflicting update"));
    }

    @Test
    void validationErrorsListEveryInvalidField() throws Exception {
        mvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:eventflow:problem:validation"))
                .andExpect(jsonPath("$.errors.name").value("must not be blank"));
    }

    record NewThing(@NotBlank String name) {
    }

    @RestController
    static class SampleController {

        @GetMapping("/things/{id}")
        String find(@PathVariable String id) {
            throw switch (id) {
                case "locked" -> new BusinessRuleViolationException("THING_LOCKED", "Thing is locked");
                case "stale" -> new OptimisticLockingFailureException("stale version");
                default -> new ResourceNotFoundException("Thing", id);
            };
        }

        @PostMapping("/things")
        String create(@Valid @RequestBody NewThing thing) {
            return thing.name();
        }
    }
}
