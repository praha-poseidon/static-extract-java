package com.poseidon.javastatic.extract.rule;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerIdentityDictTest {

    @Test
    void splitsTrailingDictBlock() {
        String src = """
                rule "x"
                endpoint MQ outbound
                find call send
                build {
                  topic: t
                }

                dict {
                  com.foo.Bar.send() = cooper_topic
                  com.foo.Bar.other() = /v1/path
                }
                """;
        SerIdentityDict.Split split = SerIdentityDict.split(src);
        assertTrue(split.hasIdentity());
        assertEquals("cooper_topic", split.identity().get("com.foo.Bar.send()"));
        assertEquals("/v1/path", split.identity().get("com.foo.Bar.other()"));
        assertFalse(split.serBody().contains("dict"));
        assertTrue(split.serBody().contains("rule \"x\""));
    }

    @Test
    void noDictReturnsEmpty() {
        SerIdentityDict.Split split = SerIdentityDict.split("rule \"a\"\nfind call x\nbuild { t: a }\n");
        assertFalse(split.hasIdentity());
    }
}
