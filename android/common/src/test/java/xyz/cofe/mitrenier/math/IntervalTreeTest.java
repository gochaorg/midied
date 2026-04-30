package xyz.cofe.mitrenier.math;

import org.junit.jupiter.api.Test;

public class IntervalTreeTest {
    @Test
    public void test1(){
        IntervalTree<Integer, String> tree = IntervalTree.create();

        // Добавление интервалов
        tree.insert(15, 20, "A");
        tree.insert(10, 30, "B");
        tree.insert(5, 20, "C");
        tree.insert(20, 25, "D");
        tree.insert(20, 28, "E");

        // Поиск пересекающихся интервалов с [12, 18]
        var overlaps = tree.findOverlaps(12, 18);

        System.out.println("Пересекающиеся интервалы: " + overlaps);

        tree.delete(10, 30);

        overlaps = tree.findOverlaps(12, 18);
        System.out.println("Пересекающиеся интервалы: " + overlaps);

        overlaps = tree.findOverlaps(21, 24);
        System.out.println("Пересекающиеся интервалы: " + overlaps);
    }
}
