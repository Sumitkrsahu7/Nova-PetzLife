package com.novapetzlife;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NovaPetzlifeApplicationTests {

    @Test
    void contextLoads() {
        // Verifies Spring context, Flyway migration execution, and bean wiring
    }
}
