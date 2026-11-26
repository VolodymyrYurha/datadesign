package ch.usi.studysystem.domain.system;

import ch.usi.studysystem.domain.DataStore;

/** Health of one database. */
public record DbStatus(DataStore db, boolean up, long latencyMs, String version, String detail) {
}
