package xyz.cofe.mitrenier.math;

import xyz.cofe.coll.im.ImList;
import xyz.cofe.coll.im.iter.ExtIterable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Интервальное дерево для хранения и поиска интервалов. <br/>
 * Возможно несколько значений на одном и том же интервале
 *
 * @param <T> Тип точки на интервальной линии
 * @param <V> Значение связанное с интервалом
 */
public class IntervalTree<T, V> {
    private final Comparator<T> compare;

    public IntervalTree(Comparator<T> compare) {
        if( compare == null ) throw new IllegalArgumentException("compare==null");
        this.compare = compare;
    }

    public static <T extends Comparable<? super T>, V> IntervalTree<T, V> create() {
        return new IntervalTree<>(Comparator.<T>naturalOrder());
    }

    private int cmp(T a, T b) {
        return compare.compare(a, b);
    }

    // Класс узла дерева
    private class Node {
        T begin;                    // Начальная точка интервалов в узле
        T maxEnd;                   // Максимальная конечная точка в поддереве
        List<Interval<T, V>> intervals; // Список интервалов с одинаковым begin
        Node left;                  // Левое поддерево
        Node right;                 // Правое поддерево

        Node(T begin, Interval<T, V> interval) {
            this.begin = begin;
            this.intervals = new ArrayList<>();
            this.intervals.add(interval);
            this.maxEnd = interval.end;
        }

        void visit(ImList<Node> path, Consumer<ImList<Node>> visitor){
            var p = path.prepend(this);
            visitor.accept(path.prepend(p));
            if( left!=null )left.visit(p, visitor);
            if( right!=null )right.visit(p, visitor);
        }

        boolean isLeaf(){
            return left==null && right==null;
        }
    }

    public class Counter {
        public final String name;
        public long value;

        public Counter(String name) {
            this.name = name;
            counters.add(this);
        }

        public Counter inc(){
            value ++;
            return this;
        }
    }

    public final List<Counter> counters = new ArrayList<>();

    public class Counters {
        public void reset() {
            for( var c : counters ) c.value = 0;
        }
    }

    public List<Interval<T,V>> getIntervalList(){
        var lst = new ArrayList<Interval<T,V>>();
        var ws = new ArrayList<Node>();
        if( root!=null ) ws.add(root);
        while( !ws.isEmpty() ){
            var head = ws.remove(0);
            lst.addAll( head.intervals );

            if( head.right!=null )ws.add(0, head.right);
            if( head.left!=null )ws.add(0, head.left);
        }
        return lst;
    }

    private Node root;

    /**
     * Удаление всех значений
     */
    public void clear(){
        root = null;
    }

    // Метод оценки баланса

    //region Балансировка

    /**
     * Вычисляет ширину дерева - кол-во листьев
     * @return ширина дерева
     */
    public int getWidth(){
        if( root==null )return 0;

        int[] leafCount = new int[]{ 0 };
        root.visit(ImList.of(), path -> {
            if( path.head().map(Node::isLeaf).orElse(false)){
                leafCount[0]++;
            }
        });

        return leafCount[0];
    }

    /**
     * Возвращает кол-во узлов в дереве
     * @return кол-во узлов
     */
    public int getNodeCount(){
        if( root==null )return 0;

        int[] nodeCount = new int[]{ 0 };
        root.visit(ImList.of(), path -> {
            nodeCount[0]++;
        });

        return nodeCount[0];
    }

    /**
     * Вычисляет высоту дерева
     * @return высота дерева
     */
    public int getHeight(){
        if( root==null )return 0;
        return getHeight(root);
    }

    /**
     * Метод оценки баланса (isBalanced)
     *
     * <li> Проверяет, является ли дерево сбалансированным по высоте.  </li>
     * <li> Для каждого узла вычисляется высота левого и правого поддеревьев.  </li>
     * <li> Если разница в высотах превышает 1, дерево считается несбалансированным.  </li>
     *
     * @return Возвращает true, если дерево сбалансировано, и false в противном случае.
     */
    public boolean isBalanced() {
        return isBalanced(root);
    }

    private boolean isBalanced(Node node) {
        if( node == null ){
            return true;
        }
        int leftHeight = getHeight(node.left);
        int rightHeight = getHeight(node.right);
        if( Math.abs(leftHeight - rightHeight) > 1 ){
            return false;
        }
        return isBalanced(node.left) && isBalanced(node.right);
    }

    private int getHeight(Node node) {
        if( node == null ){
            return 0;
        }
        return 1 + Math.max(getHeight(node.left), getHeight(node.right));
    }

    /**
     * Метод оценки дисбаланса
     * <p>
     * Как это работает
     * <p>
     *   <ul>
     *      <li>Метод getMaxImbalance() вызывает вспомогательный метод calculateImbalance(), который рекурсивно обходит дерево. </li>
     *      <li>Для каждого узла вычисляется:
     *         <ul>
     *         <li>Высота поддеревьев (левое и правое). </li>
     *         <li>Разница высот между ними. </li>
     *         <li>Максимальная разница высот среди текущего узла и его поддеревьев. </li>
     *         </ul>
     *      </li>
     *      <li>Возвращается число, где: </li>
     *         <ul>
     *         <li>0 или 1 — дерево сбалансировано. </li>
     *         <li>Большие значения (например, 2, 3 и т.д.) указывают на возрастающий дисбаланс. </li>
     *         </ul>
     *      </li>
     * </ul>
     */
    public int getMaxImbalance() {
        return calculateImbalance(root)[1];
    }

    private int[] calculateImbalance(Node node) {
        if( node == null ){
            return new int[]{0, 0}; // [высота, максимальный дисбаланс]
        }
        int[] left = calculateImbalance(node.left);
        int[] right = calculateImbalance(node.right);
        int height = 1 + Math.max(left[0], right[0]);
        int imbalance = Math.abs(left[0] - right[0]);
        int maxImbalance = Math.max(imbalance, Math.max(left[1], right[1]));
        return new int[]{height, maxImbalance};
    }

    /**
     * Метод перебалансировки (rebalance)
     *
     * <li> Собирает все узлы дерева в отсортированном порядке с помощью обхода inorder.
     * <li> Строит новое сбалансированное дерево, выбирая средний элемент списка узлов в качестве корня.
     * <li> Рекурсивно применяет этот процесс к левому и правому поддеревьям.
     */
    public void rebalance() {
        List<Node> nodes = new ArrayList<>();
        inorderTraversal(root, nodes);
        root = buildBalancedTree(nodes, 0, nodes.size() - 1);
    }

    private void inorderTraversal(Node node, List<Node> nodes) {
        if( node != null ){
            inorderTraversal(node.left, nodes);
            nodes.add(node);
            inorderTraversal(node.right, nodes);
        }
    }

    private Node buildBalancedTree(List<Node> nodes, int start, int end) {
        if( start > end ){
            return null;
        }
        int mid = (start + end) / 2;
        Node node = nodes.get(mid);
        node.left = buildBalancedTree(nodes, start, mid - 1);
        node.right = buildBalancedTree(nodes, mid + 1, end);
        return node;
    }
    //endregion

    private Node findNode(Node node, T begin) {
        if( node == null ){
            return null;
        }
        int cmp = cmp(begin, node.begin);
        if( cmp < 0 ){
            return findNode(node.left, begin);
        } else if( cmp > 0 ){
            return findNode(node.right, begin);
        } else{
            return node;
        }
    }

    /**
     * Вставка нового интервала
     * @param begin Начало интервала
     * @param end Конец интервала
     * @param value Значение
     */
    public void insert(T begin, T end, V value) {
        Interval<T, V> interval = new Interval<>(begin, end, value);
        root = insert(root, interval);
    }

    private final Counter insertCounter = new Counter("insertCalls");

    private Node insert(Node node, Interval<T, V> interval) {
        insertCounter.inc();

        if( node == null ){
            return new Node(interval.begin, interval);
        }

        int cmp = cmp(interval.begin, node.begin);
        if( cmp < 0 ){
            node.left = insert(node.left, interval);
        } else if( cmp > 0 ){
            node.right = insert(node.right, interval);
        } else{
            // Если begin совпадает, добавляем интервал в список текущего узла
            node.intervals.add(interval);
            if( cmp(interval.end, node.maxEnd) > 0 ){
                node.maxEnd = interval.end;
            }
        }

        updateMaxEnd(node);
        return node;
    }

    /**
     * Удаление интервала по begin, end и value
     * @param begin Начало интервала
     * @param end Конец интервала
     * @param value Удаляемое значение
     */
    public void delete(T begin, T end, V value) {
        root = delete(root, begin, end, value);
    }

    private Node delete(Node node, T begin, T end, V value) {
        if( node == null ){
            return null;
        }

        int cmp = cmp(begin, node.begin);
        if( cmp < 0 ){
            node.left = delete(node.left, begin, end, value);
        } else if( cmp > 0 ){
            node.right = delete(node.right, begin, end, value);
        } else{
            // Нашли узел с нужным begin
            NavigableSet<Integer> removeSet = new TreeSet<>();

            for( int i = 0; i < node.intervals.size(); i++ ){
                Interval<T, V> interval = node.intervals.get(i);
                if( interval.end.equals(end) && (interval.value == value || interval.value.equals(value)) ){
                    removeSet.add(i);
                }
            }

            removeSet.descendingSet().forEach(i -> node.intervals.remove(i.intValue()));

            if( node.intervals.isEmpty() ){
                // Если список интервалов пуст, удаляем узел
                if( node.left == null ) return node.right;
                if( node.right == null ) return node.left;
                Node successor = findMin(node.right);
                node.begin = successor.begin;
                node.intervals = successor.intervals;
                node.right = delete(node.right, successor.begin, successor.intervals.get(0).end, successor.intervals.get(0).value);
            } else{
                // Обновляем maxEnd
                node.maxEnd = node.intervals.get(0).end;
                for( Interval<T, V> interval : node.intervals ){
                    if( cmp(interval.end, node.maxEnd) > 0 ){
                        node.maxEnd = interval.end;
                    }
                }
            }
        }

        updateMaxEnd(node);
        return node;
    }

    //

    /**
     * Удаление интервала по begin, end
     * @param begin Начало интервала
     * @param end Конец интервала
     */
    public void delete(T begin, T end) {
        root = delete(root, begin, end);
    }

    private Node delete(Node node, T begin, T end) {
        if( node == null ){
            return null;
        }

        int cmp = cmp(begin, node.begin);
        if( cmp < 0 ){
            node.left = delete(node.left, begin, end);
        } else if( cmp > 0 ){
            node.right = delete(node.right, begin, end);
        } else{
            // Нашли узел с нужным begin
            NavigableSet<Integer> removeSet = new TreeSet<>();

            for( int i = 0; i < node.intervals.size(); i++ ){
                Interval<T, V> interval = node.intervals.get(i);
                if( interval.end.equals(end) ){
                    removeSet.add(i);
                }
            }

            removeSet.descendingSet().forEach(i -> node.intervals.remove(i.intValue()));

            if( node.intervals.isEmpty() ){
                // Если список интервалов пуст, удаляем узел
                if( node.left == null ) return node.right;
                if( node.right == null ) return node.left;
                Node successor = findMin(node.right);
                node.begin = successor.begin;
                node.intervals = successor.intervals;
                node.right = delete(node.right, successor.begin, successor.intervals.get(0).end, successor.intervals.get(0).value);
            } else{
                // Обновляем maxEnd
                node.maxEnd = node.intervals.get(0).end;
                for( Interval<T, V> interval : node.intervals ){
                    if( cmp(interval.end, node.maxEnd) > 0 ){
                        node.maxEnd = interval.end;
                    }
                }
            }
        }

        updateMaxEnd(node);
        return node;
    }

    // Поиск минимального узла (для удаления)
    private Node findMin(Node node) {
        while( node.left != null ){
            node = node.left;
        }
        return node;
    }

    // Обновление maxEnd в узле
    private void updateMaxEnd(Node node) {
        node.maxEnd = node.intervals.stream().map(i -> i.end).max(this::cmp).orElse(node.begin);
        if( node.left != null && cmp(node.left.maxEnd, node.maxEnd) > 0 ){
            node.maxEnd = node.left.maxEnd;
        }
        if( node.right != null && cmp(node.right.maxEnd, node.maxEnd) > 0 ){
            node.maxEnd = node.right.maxEnd;
        }
    }

    // Поиск всех интервалов, пересекающихся с заданным диапазоном
    public List<Interval<T, V>> findOverlaps(T begin, T end) {
        Interval<T, V> query = new Interval<>(begin, end, null);
        List<Interval<T, V>> result = new ArrayList<>();
        findOverlaps(root, query, result);
        return result;
    }

    private final Counter findOverlapsCounter = new Counter("findOverlaps");

    private void findOverlaps(Node node, Interval<T, V> query, List<Interval<T, V>> result) {
        findOverlapsCounter.inc();

        if( node == null ){
            return;
        }

        // Проверяем все интервалы в текущем узле
        for( Interval<T, V> interval : node.intervals ){
            if( overlaps(interval, query) ){
                result.add(interval);
            }
        }

        // Идем в левое поддерево, если оно может содержать пересечения
        if( node.left != null && cmp(node.left.maxEnd, query.begin) >= 0 ){
            findOverlaps(node.left, query, result);
        }

        // Идем в правое поддерево, если текущий begin меньше query.end
        if( node.right != null && cmp(node.begin, query.end) < 0 ){
            findOverlaps(node.right, query, result);
        }
    }

    // Проверка пересечения двух интервалов
    private boolean overlaps(Interval<T, V> a, Interval<T, V> b) {
        return cmp(a.begin, b.end) < 0 && cmp(b.begin, a.end) < 0;
    }
}
