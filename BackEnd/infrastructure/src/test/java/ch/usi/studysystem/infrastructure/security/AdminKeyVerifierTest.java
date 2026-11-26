package ch.usi.studysystem.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.usi.studysystem.infrastructure.security.AdminKeyVerifier.Result;
import org.junit.jupiter.api.Test;

class AdminKeyVerifierTest {

    @Test
    void acceptsOnlyTheConfiguredKey() {
        AdminKeyVerifier v = new AdminKeyVerifier("s3cret");
        assertThat(v.check("s3cret")).isEqualTo(Result.OK);
        assertThat(v.check("other")).isEqualTo(Result.WRONG);
        assertThat(v.check(null)).isEqualTo(Result.MISSING);
        assertThat(v.check(" ")).isEqualTo(Result.MISSING);
    }

    @Test
    void blankKeyDisablesAdminApi() {
        AdminKeyVerifier v = new AdminKeyVerifier("");
        assertThat(v.enabled()).isFalse();
        assertThat(v.check("anything")).isEqualTo(Result.DISABLED);
    }
}
