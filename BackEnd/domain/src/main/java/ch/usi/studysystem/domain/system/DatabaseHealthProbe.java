package ch.usi.studysystem.domain.system;

import ch.usi.studysystem.domain.DataStore;

/**
 * Port for checking that a database answers. Implemented once per store in the
 * {@code dal} module; used by the {@code application} module.
 */
public interface DatabaseHealthProbe {

    DataStore store();

    /** Runs the cheapest possible query. Throws if the store is unreachable. */
    ProbeResult probe();

    record ProbeResult(String version, String detail) {
    }
}
