package ch.usi.studysystem.api.security;

import ch.usi.studysystem.api.config.AppProperties;
import ch.usi.studysystem.api.dto.ApiResponse;
import ch.usi.studysystem.infrastructure.security.AdminKeyVerifier;
import ch.usi.studysystem.infrastructure.tracing.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Admin auth guard for {@code /api/admin/**}: the request needs an {@code X-Admin-Key} header equal to
 * {@code app.admin.api-key}. Everything else stays public and read-only.
 *
 * <ul>
 *   <li>missing or wrong key: 401</li>
 *   <li>no key configured: 403 (admin API turned off)</li>
 * </ul>
 * Runs right after {@code TraceIdFilter}, so rejected calls are still logged, and accepted ones are
 * logged with {@code user.id = admin}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Key";
    public static final String PATH_PREFIX = "/api/admin/";

    private final AdminKeyVerifier verifier;
    private final JsonMapper json;

    public AdminApiKeyFilter(AppProperties props, JsonMapper json) {
        this.verifier = new AdminKeyVerifier(props.admin() != null ? props.admin().apiKey() : null);
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
        if ("OPTIONS".equals(request.getMethod())) { // CORS preflight carries no custom headers
            chain.doFilter(request, response);
            return;
        }
        switch (verifier.check(request.getHeader(HEADER))) {
            case OK -> {
                TraceContext ctx = TraceContext.current();
                if (ctx != null) {
                    ctx.setUser("admin", "admin");
                }
                chain.doFilter(request, response);
            }
            case MISSING -> reject(response, HttpStatus.UNAUTHORIZED, "Missing " + HEADER + " header");
            case WRONG -> reject(response, HttpStatus.UNAUTHORIZED, "Invalid admin key");
            case DISABLED -> reject(response, HttpStatus.FORBIDDEN,
                    "Admin API is disabled: set ADMIN_API_KEY (app.admin.api-key) to enable it");
        }
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), ApiResponse.fail(status.name(), message));
    }
}
