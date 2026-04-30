package xyz.cofe.nipal.header;

public record MediaType(String mime, double weight) {
    public MediaType {
        if( mime==null ) throw new IllegalArgumentException("mime==null");
    }
}
