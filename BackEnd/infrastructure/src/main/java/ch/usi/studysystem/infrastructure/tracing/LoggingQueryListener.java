package ch.usi.studysystem.infrastructure.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes one log line per query. Logger name is {@code db.<store>}, so verbosity can be set
 * per database in application.yml, e.g. {@code logging.level.db.neo4j: DEBUG}.
 * Successful queries log at INFO, failed ones at WARN.
 */
@Component
public class LoggingQueryListener implements QueryListener {

    @Override
    public void onQuery(QuerySpan s) {
        Logger log = LoggerFactory.getLogger("db." + s.db().name());
        String query = s.query().replaceAll("\\s+", " ");
        if (s.error() == null) {
            log.info("{} {} ms, rows={} | {} | params={}", s.operation(), s.durationMs(), s.rows(), query, s.params());
        } else {
            log.warn("{} FAILED after {} ms: {} | {} | params={}", s.operation(), s.durationMs(), s.error(), query, s.params());
        }
    }
}
