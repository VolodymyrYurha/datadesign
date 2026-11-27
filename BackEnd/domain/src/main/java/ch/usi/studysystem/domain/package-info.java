/**
 * Domain layer (core). Models and repository interfaces only, in plain Java.
 *
 * <p>Put domain models here (Course, Professor, Program, Student, ...) and one
 * repository interface per need (e.g. {@code CourseGraphRepository} for Neo4j queries,
 * {@code CourseDocumentRepository} for Mongo, {@code CourseSearchRepository} for Elastic).
 * The {@code dal} module implements them; the {@code application} module uses them.
 */
package ch.usi.studysystem.domain;
