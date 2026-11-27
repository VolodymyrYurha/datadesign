package ch.usi.studysystem.application.system;

import ch.usi.studysystem.domain.system.DatabaseHealthProbe;
import ch.usi.studysystem.domain.system.DatabaseHealthProbe.ProbeResult;
import ch.usi.studysystem.domain.system.DbStatus;
import java.util.List;
import org.springframework.stereotype.Service;

/** Checks every registered database. Knows nothing about how each DB is queried. */
@Service
public class SystemHealthService {

    private final List<DatabaseHealthProbe> probes;

    public SystemHealthService(List<DatabaseHealthProbe> probes) {
        this.probes = probes;
    }

    public List<DbStatus> checkAll() {
        return probes.stream().map(SystemHealthService::check).toList();
    }

    private static DbStatus check(DatabaseHealthProbe probe) {
        long start = System.nanoTime();
        try {
            ProbeResult r = probe.probe();
            return new DbStatus(probe.store(), true, ms(start), r.version(), r.detail());
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return new DbStatus(probe.store(), false, ms(start), null, msg);
        }
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
