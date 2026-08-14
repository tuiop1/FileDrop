package dev.tuiop.filedrop.access;

public interface PasswordService {
    void validate(String rawPassword);

    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}
