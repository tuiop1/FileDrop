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

    @Test
    void validatesOnlyCanonicalTokensWithTheConfiguredEntropyLength() {
        String token = tokenService.generateToken();

        assertThat(tokenService.isValidFormat(token)).isTrue();
        assertThat(tokenService.isValidFormat(null)).isFalse();
        assertThat(tokenService.isValidFormat("a".repeat(42))).isFalse();
        assertThat(tokenService.isValidFormat("a".repeat(44))).isFalse();
        assertThat(tokenService.isValidFormat(token + "=")).isFalse();
        assertThat(tokenService.isValidFormat("!" + token.substring(1))).isFalse();
    }
}
