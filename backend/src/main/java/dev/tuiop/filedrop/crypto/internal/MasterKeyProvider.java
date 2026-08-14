package dev.tuiop.filedrop.crypto.internal;

import javax.crypto.SecretKey;

public interface MasterKeyProvider {
    SecretKey getKey();

    int getVersion();
}
