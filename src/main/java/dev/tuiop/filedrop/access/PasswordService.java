package dev.tuiop.filedrop.access;

public interface PasswordService {
    String hash(String rawPassword);

    boolean matches(String rawPassword, String passwordHash);
}

