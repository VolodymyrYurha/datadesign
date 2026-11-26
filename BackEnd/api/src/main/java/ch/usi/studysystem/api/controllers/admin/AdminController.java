package ch.usi.studysystem.api.controllers.admin;

import ch.usi.studysystem.api.config.OpenApiConfig;
import ch.usi.studysystem.api.dto.ApiResponse;
import ch.usi.studysystem.infrastructure.tracing.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example of a guarded endpoint. Every controller under {@code /api/admin} needs the
 * {@code X-Admin-Key} header (see {@code AdminApiKeyFilter}); write endpoints go here.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Write/edit endpoints. Need the X-Admin-Key header.")
@SecurityRequirement(name = OpenApiConfig.ADMIN_KEY_SCHEME)
public class AdminController {

    @GetMapping("/whoami")
    @Operation(summary = "Check the admin key", description = "200 with the caller when the key is valid, 401 otherwise.")
    public ApiResponse<Map<String, String>> whoami() {
        TraceContext ctx = TraceContext.current();
        return ApiResponse.ok(Map.of(
                "userId", ctx != null ? ctx.userId() : "admin",
                "role", ctx != null ? ctx.userRole() : "admin"));
    }
}
