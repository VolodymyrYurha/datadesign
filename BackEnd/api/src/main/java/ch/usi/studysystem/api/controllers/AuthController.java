package ch.usi.studysystem.api.controllers;

import ch.usi.studysystem.api.dto.ApiResponse;
import ch.usi.studysystem.infrastructure.security.AdminTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Admin login. Use the returned token as 'Authorization: Bearer <token>'.")
public class AuthController {

    private final AdminTokenService tokens;

    public AuthController(AdminTokenService tokens) {
        this.tokens = tokens;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token, String tokenType, Instant expiresAt) {
    }

    @PostMapping("/login")
    @Operation(summary = "Log in as admin",
            description = "Returns a bearer token for /api/admin/**. Default credentials admin / admin "
                    + "(ADMIN_USERNAME / ADMIN_PASSWORD). 401 on wrong credentials.")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        return tokens.login(req.username(), req.password())
                .map(t -> ResponseEntity.ok(ApiResponse.ok(new LoginResponse(t.token(), "Bearer", t.expiresAt()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.fail("UNAUTHORIZED", "Invalid username or password")));
    }
}
