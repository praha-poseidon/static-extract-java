package com.poseidon.javastatic.extract.jdt.trace.external;

/**
 * Flat identity dictionary: {@code "com.foo.Bar.send()" -> "topic_or_path"}.
 */
public interface IdentityDictResolver {

    /**
     * @return identity value or null if missing
     */
    String resolveIdentity(String methodKey);
}
