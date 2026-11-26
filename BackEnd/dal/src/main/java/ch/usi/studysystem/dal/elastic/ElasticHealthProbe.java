package ch.usi.studysystem.dal.elastic;

import ch.usi.studysystem.domain.DataStore;
import ch.usi.studysystem.domain.system.DatabaseHealthProbe;
import ch.usi.studysystem.infrastructure.tracing.QueryTracer;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Repository;

@Repository
@Order(3)
public class ElasticHealthProbe implements DatabaseHealthProbe {

    private final ElasticsearchClient client;
    private final QueryTracer tracer;

    public ElasticHealthProbe(ElasticsearchClient client, QueryTracer tracer) {
        this.client = client;
        this.tracer = tracer;
    }

    @Override
    public DataStore store() {
        return DataStore.elasticsearch;
    }

    @Override
    public ProbeResult probe() {
        InfoResponse info = tracer.trace(DataStore.elasticsearch, "health", "GET /", () -> {
            try {
                return client.info();
            } catch (IOException e) {
                throw new UncheckedIOException(e.getMessage(), e);
            }
        });
        return new ProbeResult(info.version().number(), "cluster: " + info.clusterName());
    }
}
