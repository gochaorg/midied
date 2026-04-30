package xyz.cofe.mitrenier.math;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObservableCollectionsTest {
    private ObservableNavigableMap<String, Integer> map;
    private ObservableNavigableSet<String> set;
    private TestMapListener mapListener;
    private TestSetListener setListener;

    // Вспомогательный класс для отслеживания событий карты
    private static class TestMapListener implements MapEventListener<String, Integer> {
        List<String> inserts = new ArrayList<>();
        List<String> deletes = new ArrayList<>();

        @Override
        public void onInsert(String key, Integer value) {
            inserts.add(key + "=" + value);
        }

        @Override
        public void onDelete(String key, Integer value) {
            deletes.add(key + "=" + value);
        }
    }

    // Вспомогательный класс для отслеживания событий множества
    private static class TestSetListener implements SetEventListener<String> {
        List<String> inserts = new ArrayList<>();
        List<String> deletes = new ArrayList<>();

        @Override
        public void onInsert(String element) {
            inserts.add(element);
        }

        @Override
        public void onDelete(String element) {
            deletes.add(element);
        }
    }

    @BeforeEach
    void setUp() {
        map = new ObservableNavigableMap<>();
        set = new ObservableNavigableSet<>();
        mapListener = new TestMapListener();
        setListener = new TestSetListener();
        map.addListener(mapListener);
        set.addListener(setListener);
    }

    // Тесты для ObservableNavigableMap
    @Test
    void testMapPutAndInsertEvent() {
        map.put("apple", 1);
        assertEquals(1, map.size());
        assertEquals(Arrays.asList("apple=1"), mapListener.inserts);
        assertTrue(mapListener.deletes.isEmpty());
    }

    @Test
    void testMapPutUpdateAndEvents() {
        map.put("apple", 1);
        map.put("apple", 2);
        assertEquals(1, map.size());
        assertEquals(Arrays.asList("apple=1", "apple=2"), mapListener.inserts);
        assertEquals(Arrays.asList("apple=1"), mapListener.deletes);
    }

    @Test
    void testMapRemoveAndDeleteEvent() {
        map.put("apple", 1);
        map.remove("apple");
        assertEquals(0, map.size());
        assertEquals(Arrays.asList("apple=1"), mapListener.inserts);
        assertEquals(Arrays.asList("apple=1"), mapListener.deletes);
    }

    @Test
    void testMapClearAndDeleteEvents() {
        map.put("apple", 1);
        map.put("banana", 2);
        map.clear();
        assertEquals(0, map.size());
        assertEquals(Arrays.asList("apple=1", "banana=2"), mapListener.inserts);
        assertTrue(mapListener.deletes.contains("apple=1") && mapListener.deletes.contains("banana=2"));
    }

    @Test
    void testMapPutAllAndInsertEvents() {
        Map<String, Integer> toAdd = new HashMap<>();
        toAdd.put("apple", 1);
        toAdd.put("banana", 2);
        map.putAll(toAdd);
        assertEquals(2, map.size());
        assertTrue(mapListener.inserts.size()==2);
        assertTrue(mapListener.inserts.contains("banana=2"));
        assertTrue(mapListener.inserts.contains("apple=1"));
        //assertEquals(Arrays.asList("apple=1", "banana=2"), mapListener.inserts);
        assertTrue(mapListener.deletes.isEmpty());
    }

    @Test
    void testMapNavigationalMethods() {
        map.put("apple", 1);
        map.put("banana", 2);
        map.put("zebra", 3);
        assertEquals("apple", map.lowerKey("banana"));
        assertEquals("apple", map.ceilingKey("apple"));
        assertEquals("banana", map.higherKey("apple"));
        assertEquals(new AbstractMap.SimpleEntry<>("apple", 1), map.firstEntry());
        assertEquals(new AbstractMap.SimpleEntry<>("zebra", 3), map.lastEntry());
    }

    @Test
    void testMapPollFirstEntryAndDeleteEvent() {
        map.put("apple", 1);
        map.put("banana", 2);
        Map.Entry<String, Integer> entry = map.pollFirstEntry();
        assertEquals("apple", entry.getKey());
        assertEquals(1, entry.getValue());
        assertEquals(1, map.size());
        assertEquals(Arrays.asList("apple=1", "banana=2"), mapListener.inserts);
        assertEquals(Arrays.asList("apple=1"), mapListener.deletes);
    }

    @Test
    void testMapSubMap() {
        map.put("apple", 1);
        map.put("banana", 2);
        map.put("zebra", 3);
        NavigableMap<String, Integer> subMap = map.subMap("apple", true, "zebra", false);
        assertEquals(2, subMap.size());
        assertTrue(subMap.containsKey("apple") && subMap.containsKey("banana"));
    }

    // Тесты для ObservableNavigableSet
    @Test
    void testSetAddAndInsertEvent() {
        set.add("apple");
        assertEquals(1, set.size());
        assertEquals(Arrays.asList("apple"), setListener.inserts);
        assertTrue(setListener.deletes.isEmpty());
    }

    @Test
    void testSetAddDuplicateNoEvent() {
        set.add("apple");
        set.add("apple");
        assertEquals(1, set.size());
        assertEquals(Arrays.asList("apple"), setListener.inserts);
        assertTrue(setListener.deletes.isEmpty());
    }

    @Test
    void testSetRemoveAndDeleteEvent() {
        set.add("apple");
        set.remove("apple");
        assertEquals(0, set.size());
        assertEquals(Arrays.asList("apple"), setListener.inserts);
        assertEquals(Arrays.asList("apple"), setListener.deletes);
    }

    @Test
    void testSetClearAndDeleteEvents() {
        set.add("apple");
        set.add("banana");
        set.clear();
        assertEquals(0, set.size());
        assertEquals(Arrays.asList("apple", "banana"), setListener.inserts);
        assertTrue(setListener.deletes.contains("apple") && setListener.deletes.contains("banana"));
    }

    @Test
    void testSetAddAllAndInsertEvents() {
        List<String> toAdd = Arrays.asList("apple", "banana");
        set.addAll(toAdd);
        assertEquals(2, set.size());
        assertEquals(Arrays.asList("apple", "banana"), setListener.inserts);
        assertTrue(setListener.deletes.isEmpty());
    }

    @Test
    void testSetRemoveAllAndDeleteEvents() {
        set.add("apple");
        set.add("banana");
        set.removeAll(Arrays.asList("apple", "banana", "zebra"));
        assertEquals(0, set.size());
        assertEquals(Arrays.asList("apple", "banana"), setListener.inserts);
        assertTrue(setListener.deletes.contains("apple") && setListener.deletes.contains("banana"));
    }

    @Test
    void testSetNavigationalMethods() {
        set.add("apple");
        set.add("banana");
        set.add("zebra");
        assertEquals("apple", set.lower("banana"));
        assertEquals("banana", set.higher("apple"));
        assertEquals("apple", set.first());
        assertEquals("zebra", set.last());
    }

    @Test
    void testSetPollFirstAndDeleteEvent() {
        set.add("apple");
        set.add("banana");
        String element = set.pollFirst();
        assertEquals("apple", element);
        assertEquals(1, set.size());
        assertEquals(Arrays.asList("apple", "banana"), setListener.inserts);
        assertEquals(Arrays.asList("apple"), setListener.deletes);
    }

    @Test
    void testSetSubSet() {
        set.add("apple");
        set.add("banana");
        set.add("zebra");
        NavigableSet<String> subSet = set.subSet("apple", true, "zebra", false);
        assertEquals(2, subSet.size());
        assertTrue(subSet.contains("apple") && subSet.contains("banana"));
    }

    @Test
    void testSetRemoveIfAndDeleteEvents() {
        set.add("apple");
        set.add("banana");
        set.add("zebra");
        set.removeIf(s -> s.startsWith("a"));
        assertEquals(2, set.size());
        assertFalse(set.contains("apple"));
        assertEquals(Arrays.asList("apple", "banana", "zebra"), setListener.inserts);
        assertEquals(Arrays.asList("apple"), setListener.deletes);
    }
}
