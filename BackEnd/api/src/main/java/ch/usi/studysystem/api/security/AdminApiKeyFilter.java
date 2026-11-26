package ch.usi.studysystem.api.security;

import ch.usi.studysystem.api.dto.ApiResponse;
import ch.usi.studysystem.infrastructure.security.AdminKeyVerifier;
import ch.usi.studysystem.infrastructure.security.AdminTokenService;
import ch.usi.studysystem.infrastructure.tracing.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Admin auth guard for {@code /api/admin/**}. A request passes with either
 * <ul>
 *   <li>{@code Authorization: Bearer <token>} from {@code POST /api/auth/login}, or</li>
 *   <li>{@code X-Admin-Key: <key>} when {@code ADMIN_API_KEY} is set (handy for scripts).</li>
 * </ul>
 * Anything else gets 401. Everything outside {@code /api/admin} stays public and read-only.
 * Runs right after {@code TraceIdFilter}, so rejected calls are still logged, and accepted ones are
 * logged with {@code user.id = <admin user>}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Key";
    public static final String PATH_PREFIX = "/api/admin/";
    private static final String BEARER = "Bearer ";

    private final AdminTokenService tokens;
    private final AdminKeyVerifier keys;
    private final JsonMapper json;

    public AdminApiKeyFilter(AdminTokenService tokens, AdminKeyVerifier keys, JsonMapper json) {
        this.tokens = tokens;
        this.keys = keys;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith(PATH_PREFIX) || path.equals("/api/admin"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equals(request.getMethod())) { // CORS preflight carries no auth headers
            chain.doFilter(request, response);
            return;
        }
        Optional<String> user = authenticate(request);
        if (user.isEmpty()) {
            reject(response, "Log in with POST /api/auth/login and send 'Authorization: Bearer <token>'");
            return;
        }
        TraceContext ctx = TraceContext.current();
        if (ctx != null) {
            ctx.setUser(user.get(), AdminTokenService.ROLE_ADMIN);
        }
        chain.doFilter(request, response);
    }

    private Optional<String> authenticate(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return tokens.verify(auth.substring(BEARER.length()).trim());
        }
        if (keys.check(request.getHeader(HEADER)) == AdminKeyVerifier.Result.OK) {
            return Optional.of("admin");
        }
        return Optional.empty();
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ApiResponse.fail("UNAUTHORIZED", message));
    }
}
