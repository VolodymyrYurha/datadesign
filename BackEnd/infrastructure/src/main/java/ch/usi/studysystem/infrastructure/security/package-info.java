/**
 * Admin auth.
 *
 * <p>The UI is read-only. Write/edit endpoints live under {@code /api/admin/**}. Callers log in with
 * {@code POST /api/auth/login} ({@link AdminTokenService}) and send {@code Authorization: Bearer <token>};
 * scripts may send {@code X-Admin-Key} instead ({@link AdminKeyVerifier}). The servlet filter that applies
 * both is {@code api/security/AdminApiKeyFilter}.
 */
package ch.usi.studysystem.infrastructure.security;
