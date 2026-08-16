package dev.tuiop.filedrop.access;

public interface TokenService {
    String generateToken();

    String hashToken(String token);

    boolean isValidFormat(String token);

}
