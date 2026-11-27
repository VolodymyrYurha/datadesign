package ch.usi.studysystem.api.controllers;

import ch.usi.studysystem.api.dto.ApiResponse;
import ch.usi.studysystem.application.system.SystemHealthService;
import ch.usi.studysystem.domain.system.DbStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System", description = "Service and database status")
public class SystemController {

    private final SystemHealthService health;

    public SystemController(SystemHealthService health) {
        this.health = health;
    }

    @GetMapping("/health")
    @Operation(summary = "Ping Neo4j, MongoDB and Elasticsearch",
            description = "Returns 200 when all three stores answer, 503 otherwise. "
                    + "meta.queries lists the raw probe query sent to each store.")
    public ResponseEntity<ApiResponse<List<DbStatus>>> health() {
        List<DbStatus> statuses = health.checkAll();
        boolean allUp = statuses.stream().allMatch(DbStatus::up);
        return ResponseEntity.status(allUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.ok(statuses));
    }
}
