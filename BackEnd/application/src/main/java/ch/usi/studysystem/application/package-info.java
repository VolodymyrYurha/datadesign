/**
 * Application layer (business logic). One sub-package per feature, e.g.
 * {@code planner} for the study planner that combines Neo4j, Mongo and Elastic.
 * Depends only on {@code domain} interfaces, never on a DB driver.
 */
package ch.usi.studysystem.application;
