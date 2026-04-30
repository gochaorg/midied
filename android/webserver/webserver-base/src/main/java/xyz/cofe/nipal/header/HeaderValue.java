package xyz.cofe.nipal.header;

import java.util.Optional;
import java.util.function.Supplier;

public interface HeaderValue<V> {
    Optional<V> parse(String str);
    default Optional<V> defaultValue(){ return Optional.empty(); }

    default HeaderValue<V> defaultValue(Supplier<Optional<V>> value) {
        if( value==null ) throw new IllegalArgumentException("value==null");
        return new DefaultValue<>(this, value);
    }

    @SuppressWarnings({"OptionalUsedAsFieldOrParameterType", "OptionalAssignedToNull"})
    default HeaderValue<V> defaultValue(Optional<V> value) {
        if( value==null ) throw new IllegalArgumentException("value==null");
        return new DefaultValue<>(this, ()->value);
    }

    default HeaderValue<V> defaultValue(V value) {
        return new DefaultValue<>(this, ()->Optional.ofNullable(value));
    }

    static class DefaultValue<V> implements HeaderValue<V> {
        private final HeaderValue<V> target;
        private final Supplier<Optional<V>> defaultValue;

        public DefaultValue(HeaderValue<V> target, Supplier<Optional<V>> defaultValue) {
            if( target==null ) throw new IllegalArgumentException("target==null");
            if( defaultValue==null ) throw new IllegalArgumentException("defaultValue==null");
            this.target = target;
            this.defaultValue = defaultValue;
        }

        @Override
        public Optional<V> parse(String str) {
            return target.parse(str);
        }

        @Override
        public Optional<V> defaultValue() {
            return defaultValue.get();
        }
    }

    public static HeaderValue<Boolean> boolValue = str -> {
        if( str==null || str.isBlank() )return Optional.empty();
        if( str.equalsIgnoreCase("true")) return Optional.of(true);
        if( str.equalsIgnoreCase("false")) return Optional.of(false);
        return Optional.empty();
    };
}
