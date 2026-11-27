/**
 * Data access layer. One sub-package per store ({@code neo4j}, {@code mongo}, {@code elastic})
 * implementing the repository interfaces from {@code domain}.
 *
 * <p>Wrap every DB call in {@link ch.usi.studysystem.infrastructure.tracing.QueryTracer} so it
 * shows up in the query inspector. A mock implementation (in-memory, from
 * {@code DataPipeline/data/sample}) can later replace a real one via a Spring profile.
 */
package ch.usi.studysystem.dal;
