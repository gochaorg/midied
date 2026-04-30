package xyz.cofe.nipal.files;

public sealed interface FileHash {
    record MD5(String value) implements FileHash {}
    record SHA1(String value) implements FileHash {}
    record SHA256(String value) implements FileHash {}
}
