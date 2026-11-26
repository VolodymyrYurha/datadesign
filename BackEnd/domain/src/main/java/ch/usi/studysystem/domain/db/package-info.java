/**
 * Raw database access, one interface per store (the "IXxxService" of .NET).
 *
 * <p>Inject these into application services. In unit tests, mock them
 * ({@code Mockito.mock(Neo4jService.class)}). Implementations live in the {@code dal} module,
 * extend {@code DatabaseService} and log every query.
 *
 * <p>Results use plain Java types ({@code Map}, {@code List}, {@code String}, numbers), so nothing
 * outside {@code dal} depends on a DB driver.
 */
package ch.usi.studysystem.domain.db;
