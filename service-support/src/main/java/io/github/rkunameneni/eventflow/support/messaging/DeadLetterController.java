package io.github.rkunameneni.eventflow.support.messaging;

import io.github.rkunameneni.eventflow.events.Topics;
import io.github.rkunameneni.eventflow.support.web.error.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin endpoint for replaying dead-lettered saga events. The base path is set per service. */
@RestController
@RequestMapping("${eventflow.dlq.base-path:/api/v1/admin/dlq}")
@Tag(name = "Dead-letter topics", description = "Operator tooling for failed events")
public class DeadLetterController {

    private final DeadLetterReplayer replayer;

    public DeadLetterController(DeadLetterReplayer replayer) {
        this.replayer = replayer;
    }

    @PostMapping("/{topic}/replay")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Republish up to 'max' messages from <topic>.DLT back to <topic>")
    public DeadLetterReplayer.ReplayResult replay(
            @PathVariable String topic,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int max) {
        if (!Topics.ALL.contains(topic)) {
            throw new ResourceNotFoundException("Topic", topic);
        }
        return replayer.replay(topic, max);
    }
}
