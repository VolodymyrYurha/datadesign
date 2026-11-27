/**
 * Data access layer. One sub-package per store ({@code neo4j}, {@code mongo}, {@code elastic})
 * implementing the repository interfaces from {@code domain}.
 *
 * <p>Wrap every DB call in {@link ch.usi.studysystem.infrastructure.tracing.QueryTracer} so it
 * shows up in the query inspector. There are no mock implementations: local development runs against
 * the docker-compose databases, which are seeded with the real scraped dataset.
 */
package ch.usi.studysystem.dal;
