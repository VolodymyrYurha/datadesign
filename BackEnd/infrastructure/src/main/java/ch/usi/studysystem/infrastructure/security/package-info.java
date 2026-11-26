/**
 * Admin auth guard.
 *
 * <p>The UI is read-only. Write/edit endpoints live under {@code /api/admin/**} and need the
 * {@code X-Admin-Key} header to match {@code app.admin.api-key} (env {@code ADMIN_API_KEY}).
 * {@link AdminKeyVerifier} holds the check; the servlet filter that applies it is
 * {@code api/security/AdminApiKeyFilter}.
 */
package ch.usi.studysystem.infrastructure.security;
