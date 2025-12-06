import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LRUCacheSegment<K,V> implements ILRUCache<K,V> {
    private final int capacity;
    private final Map<K, Node<K,V>> map;
    private final Node<K,V> head; // mru
    private final Node<K,V> tail; // lru
    private final Lock lock = new ReentrantLock();

    private static class Node<K,V> {
        final K key;
        volatile V value;
        Node<K,V> prev;
        Node<K,V> next;
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Node:[" + key + ", " + value + "]";
        }
    }

    public LRUCacheSegment(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Illegal capacity: " + capacity);
        }
        this.capacity = capacity;
        this.map = new HashMap<>();
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public String toString() {
        List<Node<K,V>> list = new ArrayList<>();
        Node<K,V> current = this.head;
        while ((current = current.next) != this.tail) {
            list.add(current);
        }
        return list.toString();
    }

    private void removeNode(Node<K,V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void addToHead(Node<K,V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void moveToHead(Node<K,V> node) {
        removeNode(node);
        addToHead(node);
    }

    public V get(K key) {
        lock.lock();
        try {
            if (!map.containsKey(key)) {
                return null;
            }
            Node<K,V> node = map.get(key);
            moveToHead(node); // mru
            return node.value;
        } finally {
            lock.unlock();
        }
    }

    private void evictLRU() {
        Node<K,V> lruNode = tail.prev;
        if (lruNode == head) return;
        removeNode(lruNode);
        map.remove(lruNode.key);
    }

    public void put(K key, V value) {
        lock.lock();
        try {
            if (map.containsKey(key)) {
                Node<K,V> node = map.get(key);
                node.value = value;
                moveToHead(node); // mru
            } else {
                if (map.size() >= capacity) {
                    evictLRU();
                }
                Node<K,V> newNode = new Node<>(key, value);
                map.put(key, newNode);
                addToHead(newNode); // mru
            }
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }
}