package xyz.cofe.mitrenier;

import org.junit.jupiter.api.Test;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import static org.mockito.Mockito.*;

public class LazySingleTest {
    @Test
    public void deptest(){
        LazySingle<Integer> root = LazySingle.of(1).logSuffix("root");

        LazySingle<String> dep = LazySingle.of("dep").logSuffix("dep")
            .compose(root, (a,b) -> a+b).logSuffix("dep_compose");
        System.out.println(dep.get());
        assertTrue(dep.get().equals("dep1"));

        root.update(2);
        System.out.println(dep.get());
        assertTrue(dep.get().equals("dep2"));
    }

    @Test
    void shouldComputeValueOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        Supplier<Integer> arg = () -> 42;
        Function<Integer, String> builder = i -> {
            counter.incrementAndGet();
            return "value-" + i;
        };

        LazySingle<String> lazy = new LazySingle<>(arg, builder);

        assertEquals("value-42", lazy.get());
        assertEquals("value-42", lazy.get());
        assertEquals(1, counter.get()); // должно вызваться только один раз
    }

    @Test
    void shouldCreateFromInitialValue() {
        LazySingle<String> lazy = LazySingle.of("hello");
        assertEquals("hello", lazy.get());
        assertEquals("hello", lazy.get()); // повторный вызов
    }

    @Test
    void shouldThrowOnNullBuilder() {
        assertThrows(IllegalArgumentException.class, () ->
            new LazySingle<>((Supplier<String>) () -> "test", null));
    }

    @Test
    void shouldThrowOnNullArg() {
        assertThrows(IllegalArgumentException.class, () ->
            new LazySingle<>((Supplier<String>) null, s -> s));
    }

    @Test
    void shouldThrowOnNullInitial() {
        assertThrows(IllegalArgumentException.class, () ->
            new LazySingle<>((Supplier<String>) null));
    }

    // ============= Тесты onUpdate и уведомлений =============

    @Test
    void shouldNotifyOnUpdate() {
        AtomicReference<String> oldValue = new AtomicReference<>();
        AtomicReference<String> newValue = new AtomicReference<>();
        BiConsumer<String, String> listener = (oldV, newV) -> {
            oldValue.set(oldV);
            newValue.set(newV);
        };

        LazySingle<String> lazy = new LazySingle<>(() -> "initial");
        lazy.onUpdate(listener).bind();

        assertEquals("initial", lazy.get());

        lazy.update("updated");

        assertEquals("initial", oldValue.get());
        assertEquals("updated", newValue.get());
    }

    @Test
    void shouldNotNotifyOnSameValue() {
        AtomicBoolean notified = new AtomicBoolean(false);

        BiConsumer<String, String> listener = (o, n) -> notified.set(true);

        LazySingle<String> lazy = new LazySingle<>(() -> "same");
        lazy.get(); // ensure computed

        lazy.onUpdate(listener).bind();
        lazy.update("same"); // same value

        assertFalse(notified.get());
    }

    @Test
    void shouldSupportWeakListeners() throws InterruptedException, IllegalAccessException {
        LazySingle<String> lazy = new LazySingle<>(() -> "test");
        BiConsumer<String, String> listener = mock(BiConsumer.class);

        lazy.onUpdate(listener).weak().bind();

        lazy.get();
        lazy.update("new");

        verify(listener).accept("test", "new");

        // Удаляем сильную ссылку
        listener = null;
        System.gc();
        Thread.sleep(100); // даем шанс GC

        lazy.update("final");

        // слабый слушатель не должен вызываться
        // (нельзя проверить напрямую, но можно убедиться, что WeakHashMap его удалил)
        // Проверим, что WeakHashMap не содержит слушатель
        Field weakListenersField = assertDoesNotThrow(() ->
            LazySingle.class.getDeclaredField("weakUpdateListeners"));
        weakListenersField.setAccessible(true);

        @SuppressWarnings("unchecked")
        WeakHashMap<BiConsumer<String, String>, Object> weakMap =
            (WeakHashMap<BiConsumer<String, String>, Object>)  weakListenersField.get(lazy);

        assertTrue(weakMap.isEmpty(), "WeakHashMap should be empty after GC");
    }

    // ============= Тесты update и clear =============

    @Test
    void shouldUpdateValue() {
        LazySingle<String> lazy = new LazySingle<>(() -> "old");
        lazy.get(); // compute
        lazy.update("new");
        assertEquals("new", lazy.get());
    }

//    @Test
//    void shouldClearValue() {
//        LazySingle<String> lazy = new LazySingle<>(() -> "test");
//        lazy.get();
//
//        assertTrue(lazy.get() != null);
//        lazy.clear();
//
//        assertNull(lazy.value); // используем рефлексию, так как поле private
//    }

    @Test
    void shouldCloseCloseableOnClear() throws IOException, IOException {
        Closeable mockCloseable = mock(Closeable.class);
        LazySingle<Closeable> lazy = new LazySingle<>(() -> mockCloseable);
        lazy.get();
        lazy.clear();
        verify(mockCloseable).close();
    }

    @Test
    void shouldCloseCloseableInOptionalOnClear() throws IOException {
        Closeable mockCloseable = mock(Closeable.class);
        LazySingle<Optional<Closeable>> lazy = new LazySingle<>(() -> Optional.of(mockCloseable));
        lazy.get();
        lazy.clear();
        verify(mockCloseable).close();
    }

    @Test
    void shouldCloseCloseableInIterableOnClear() throws IOException {
        Closeable c1 = mock(Closeable.class);
        Closeable c2 = mock(Closeable.class);
        List<Closeable> list = Arrays.asList(c1, c2);
        LazySingle<Iterable<Closeable>> lazy = new LazySingle<>(() -> list);
        lazy.get();
        lazy.clear();
        verify(c1).close();
        verify(c2).close();
    }

    @Test
    void shouldCloseLazySingleInClear() {
        LazySingle<String> inner = new LazySingle<>(() -> "inner").onUpdate((a,b)->{
            System.out.println("aaaa");
        }).bind();

        LazySingle<LazySingle<String>> lazy = new LazySingle<>(() -> inner);
        lazy.get();
        lazy.clear();

        inner.get();

        //assertNull(inner.value); // inner должен быть очищен
    }

    // ============= Тесты map и compose =============

    @Test
    void shouldMapValue() {
        LazySingle<Integer> source = new LazySingle<>(() -> 10);
        LazySingle<String> mapped = source.map(String::valueOf);
        assertEquals("10", mapped.get());
    }

    @Test
    void shouldComposeTwoLazySingles() {
        LazySingle<Integer> a = new LazySingle<>(() -> 2);
        LazySingle<Integer> b = new LazySingle<>(() -> 3);
        LazySingle<Integer> sum = a.compose(b, Integer::sum);
        assertEquals(5, sum.get());
    }

    @Test
    void shouldInvalidateOnDependencyChange() {
        LazySingle<Integer> a = new LazySingle<>(() -> 1);
        LazySingle<Integer> b = new LazySingle<>(() -> 1);
        LazySingle<Integer> sum = a.compose(b, Integer::sum);

        assertEquals(2, sum.get());

        a.update(10);
        assertEquals(11, sum.get()); // 10 + 1

        b.update(5);
        assertEquals(15, sum.get()); // 10 + 5
    }


    // ============= Тесты логирования =============

//    @Test
//    void shouldLogWithSuffix() {
//        Logger mockLogger = mock(Logger.class);
//        try (var mocked = mockStatic(LoggerFactory.class)) {
//            mocked.when(() -> LoggerFactory.getLogger(LazySingle.class)).thenReturn(mockLogger);
//
//            LazySingle<String> lazy = new LazySingle<>(() -> "test").logSuffix("TEST");
//            lazy.get();
//
//            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
//            verify(mockLogger).info(captor.capture());
//            String logMessage = captor.getValue();
//            assertTrue(logMessage.startsWith("[TEST] compute value"));
//        }
//    }

    // ============= Тесты apply =============

    @Test
    void shouldApplyFunction() {
        LazySingle<String> lazy = new LazySingle<>(() -> "hello");
        String result = lazy.apply(String::toUpperCase);
        assertEquals("HELLO", result);
    }

    @Test
    void shouldApplyConsumer() {
        AtomicReference<String> captured = new AtomicReference<>();
        LazySingle<String> lazy = new LazySingle<>(() -> "captured");
        lazy.accept(captured::set);
        assertEquals("captured", captured.get());
    }

    // ============= Граничные случаи =============

    @Test
    void shouldHandleNullUpdate() {
        LazySingle<String> lazy = new LazySingle<>(() -> "test");
        assertThrows(IllegalArgumentException.class, () -> lazy.update(null));
    }

    @Test
    void shouldHandleNullComputeInMap() {
        LazySingle<String> lazy = new LazySingle<>(() -> "test");
        assertThrows(IllegalArgumentException.class, () -> lazy.map(null));
    }

    @Test
    void shouldHandleNullOtherInCompose() {
        LazySingle<String> lazy = new LazySingle<>(() -> "test");
        assertThrows(IllegalArgumentException.class, () -> lazy.compose(null, (a, b) -> a));
    }

    @Test
    void shouldHandleNullComposeFunction() {
        LazySingle<String> a = new LazySingle<>(() -> "a");
        LazySingle<String> b = new LazySingle<>(() -> "b");
        assertThrows(IllegalArgumentException.class, () -> a.compose(b, null));
    }
}
