package com.golinkgone.glgbackend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires env vars (BASE_URL, DB_URL, ISSUER_URI, JWK_SET_URI, PROJECT_URL, SUPABASE_SERVICE_ROLE_KEY, MAXMIND_DB_PATH). Run as integration test once those are wired in a test profile.")
@SpringBootTest
class IdResolutionSystemApplicationTests {

    @Test
    void contextLoads() {
    }
}
