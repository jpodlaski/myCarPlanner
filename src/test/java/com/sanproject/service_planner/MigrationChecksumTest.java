package com.sanproject.service_planner;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway refuses to start against a database whose applied migrations no longer match the files,
 * but CI always migrates a fresh database and never notices. This pins every released migration:
 * change a schema with a new V&lt;n&gt; file, then add its hash here.
 */
class MigrationChecksumTest {

    private static final Map<String, String> RELEASED = Map.of(
            "V1__baseline_schema.sql", "e7eab6ea57ea517c68f70cfb34b2cbd35cca2e6d67b0d79173bda56b9ebbac2c",
            "V2__account_uniqueness.sql", "4d6f315a7de7bc6b1ba97db403c016ae0a7a52306d1d78e0198f6bf931ac7b23",
            "V3__email_verification_and_tokens.sql", "8683f5cb102e6da56311b2f3a5b651a8e552732470030efdded43c2e92be99f9",
            "V4__email_outbox.sql", "b71bc730f617760bdf35bc3579c1ee4782eeaa54dd892a14f5f58fa1b4a83b73",
            "V5__vehicle_vin_unique.sql", "c6e67ce797844855859514716bdf16d4903baa26e7abb6ba70a52e47227a685e",
            "V6__account_deletion_token.sql", "fd2ea3f2ab7b25dd5fd8d70b73b3a14cb4f7bff170a67b824b6f031c7d0025ce",
            "V7__booking_ready_for_work.sql", "6e6d096cdffe92ce37ba8a70b0e2e8bb55f45e620cb9b0703536572337c7597a",
            "V8__invoice_number.sql", "a033206f84de653d8ce5a2648281ea4a0dc73d864f3a74a7482c7c9a90d79ced");

    @Test
    void releasedMigrationsAreNeverEdited() throws Exception {
        Map<String, String> actual = currentMigrationHashes();

        assertThat(actual.keySet())
                .as("every migration must be pinned in RELEASED (add the hash of a new V<n> file)")
                .containsExactlyInAnyOrderElementsOf(RELEASED.keySet());
        RELEASED.forEach((file, hash) -> assertThat(actual.get(file))
                .as("%s was edited after release; add a new V<n> migration instead", file)
                .isEqualTo(hash));
    }

    private static Map<String, String> currentMigrationHashes() throws Exception {
        Resource[] files = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*.sql");
        Map<String, String> hashes = new TreeMap<>();
        for (Resource file : files) {
            // Line endings are normalised so a Windows checkout does not look like an edit.
            String sql = new String(file.getContentAsByteArray(), StandardCharsets.UTF_8).replace("\r", "");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8));
            hashes.put(file.getFilename(), HexFormat.of().formatHex(digest));
        }
        return hashes;
    }
}
