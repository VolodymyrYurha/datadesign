package ch.usi.studysystem.infrastructure.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Default sink: writes a one-line summary of each request to the application log. */
@Component
public class LoggingTraceSink implements TraceSink {

    private static final Logger log = LoggerFactory.getLogger("trace");

    @Override
    public void record(RequestTrace t) {
        log.info("{} {} -> {} in {} ms, {} db call(s)",
                t.method(), t.path(), t.status(), t.durationMs(), t.spans().size());
    }
}
