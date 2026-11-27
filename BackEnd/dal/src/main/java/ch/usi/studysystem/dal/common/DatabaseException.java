package ch.usi.studysystem.dal.common;

import ch.usi.studysystem.domain.DataStore;

/** Wraps a checked driver exception so callers only deal with runtime exceptions. */
public class DatabaseException extends RuntimeException {

    private final DataStore store;

    public DatabaseException(DataStore store, Throwable cause) {
        super(store + ": " + cause.getMessage(), cause);
        this.store = store;
    }

    public DataStore store() {
        return store;
    }
}
