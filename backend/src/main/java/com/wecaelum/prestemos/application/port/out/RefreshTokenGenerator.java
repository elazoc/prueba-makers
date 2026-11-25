package com.wecaelum.prestemos.application.port.out;

/** Creates opaque refresh tokens and the hash under which they are stored. */
public interface RefreshTokenGenerator {

    GeneratedToken generate();

    String hash(String raw);

    /** The value for the client and the hash for the database. */
    record GeneratedToken(String raw, String hash) {

        @Override
        public String toString() {
            return "GeneratedToken[raw=[PROTECTED], hash=" + hash + "]";
        }
    }
}
