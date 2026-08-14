package dev.tuiop.filedrop.access.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenServiceImplTests {

    private final TokenServiceImpl tokenService = new TokenServiceImpl();

    @Test
    void generatesUrlSafeTokensWithExpectedEntropyLength() {
        String firstToken = tokenService.generateToken();
        String secondToken = tokenService.generateToken();

        assertThat(firstToken).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(secondToken).matches("^[A-Za-z0-9_-]{43}$");
        assertThat(secondToken).isNotEqualTo(firstToken);
    }

    @Test
    void hashesTokenWithSha256() {
        assertThat(tokenService.hashToken("token"))
                .isEqualTo("3c469e9d6c5875d37a43f353d4f88e61fcf812c66eee3457465a40b0da4153e0");
    }
}
