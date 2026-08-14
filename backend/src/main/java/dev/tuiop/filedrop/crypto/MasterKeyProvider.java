package dev.tuiop.filedrop.crypto;

import javax.crypto.SecretKey;

public interface MasterKeyProvider {
    SecretKey getKey();

    int getVersion();
}
