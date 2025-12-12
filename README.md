Design document  
# Least Recently Used (LRU) Cache Library

## 1. Introduction and Project Goals

### 1.1 Project Overview
This project involves the design and implementation of a generic, thread-safe **Least Recently Used (LRU) Cache** library in Java. The core goal is to build a robust, high-performance data structure with a complex algorithmic core (the $O(1)$ eviction policy) and validate its correctness and performance using rigorous testing methodologies.

### 1.2 Objectives & Deliverables
The primary evaluation focus is the quality of the tests, not the final feature set.

| Type            | Objective                                                                   | Deliverable                                                                                                                    |
|:----------------|:----------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------|
| **Functional**  | Implement `get(key)` and `put(key, value)` operations.                      | Final `LRUCache.java` library class.                                                                                           |
| **Algorithmic** | Ensure all core operations execute in $O(1)$ average time complexity.       | Performance test results proving $O(1)$ time complexity.                                                                       |
| **Quality**     | Ensure correct eviction policy under all conditions.                        | Comprehensive JUnit and jqwik (PBT) test suite.                                                                                |
| **System**      | Ensure the cache is thread-safe for concurrent use via external TCP access. | Integration test proving correct behavior under high-concurrency load. Also includes the "Cache Service" used for E2E testing. |

---

## 2. System Architecture and Design

The LRU Cache's complexity lies in its **Hybrid Data Structure**, designed to achieve $O(1)$ time complexity for all three necessary actions: lookup, insertion, and reordering.

### 2.1 Core Components

| Component              | Responsibility                                                                                                     | Java Implementation                                                                                                                 |
|:-----------------------|:-------------------------------------------------------------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------|
| **Node**               | Stores a single key-value pair and pointers to its neighbors.                                                      | Inner class `Node<K, V>` with fields: `K key`, `V value`, `Node prev`, `Node next`.                                                 |
| **HashMap**            | Provides $O(1)$ lookup for any key.                                                                                | `Map<K, Node<K, V>> map`. Stores a reference to the corresponding Node in the list.                                                 |
| **Doubly Linked List** | Maintains the recency order of items. Head is **MRU** (Most Recently Used), Tail is **LRU** (Least Recently Used). | Custom implementation using the `Node` class. Uses dummy head and tail nodes for clean pointer manipulation and edge-case handling. |
| **LRUCache**           | The main public interface. Orchestrates the HashMap and Doubly Linked List logic.                                  | Main public class `LRUCache<K, V>`.                                                                                                 |

### 2.2 Public API

The library's public interface is minimal:

```java
public class LRUCache<K, V> {
    // Constructor
    public LRUCache(int capacity) { }

    // Functional methods
    public V get(K key) { }           // Returns value or null; updates recency.
    public void put(K key, V value) { } // Inserts/updates; triggers eviction if capacity is exceeded.

    // Utility (Optional, for testing)
    public int size() { }
}
```
### 2.3 Deployment Model for Load Testing: The Cache Service
To perform E2E Load Testing and accurately measure the performance impact of thread-safety under a realistic network load, the LRUCache library is encapsulated within a minimal Cache Service.  

| Component                       | Responsibility                                                                                         | Purpose                                                                                                                          |
|:--------------------------------|:-------------------------------------------------------------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------|
| **Cache Service (Mini Server)** | Opens a TCP port and runs in a separate process. Contains a single, shared instance of LRUCache.       | Serves as the System Under Test (SUT), decoupling the core library from the testing environment.                                 |
| **Service Protocol**            | Implements a simple, line-based protocol over TCP (e.g., GET <key>, PUT <key> <value>).                | Simulates a basic external cache API, ensuring the thread-safety of the LRUCache is tested under I/O and synchronization stress. |
| **Load Test Client**            | A separate program that initiates $N$ concurrent TCP connections to the Service to simulate user load. | Measures the holistic system performance metrics (Latency, Throughput).                                                          |

## 3. Data Flow and Algorithms
### 3.1 get(K key) Algorithm
The get operation must update the access order, making the item the Most Recently Used.   
1. Lookup: Use the HashMap to find the Node in O(1). If not found (cache miss), return null.
2. Reorder: If found (cache hit), call an internal helper function moveToHead(Node node) to detach the node from its current position and re-attach it to the MRU end (Head) of the DoublyLinkedList. This is done in O(1).
3. Return: Return the value from the Node.

### 3.2 put(K key, V value) Algorithm
The put operation manages insertion, update, and the crucial eviction policy.  
1. Check Update: If the key exists in the HashMap:
   - Update the Node's value.
   - Call moveToHead(Node node).
2. Check Eviction: If the key does not exist AND the map.size() equals capacity:
   - Evict: Identify the Node at the LRU end (Tail): Node lruNode = tail.prev.
   - Remove this lruNode from the DoublyLinkedList and remove its key from the HashMap. (All O(1)).
3. New Insertion: Create a new Node, add it to the MRU end (Head) of the DoublyLinkedList, and map the key to this new Node in the HashMap.

## 4. Testing and Quality Assurance Plan
The project evaluation is primarily based on the quality of the tests, covering all three required categories.
### 4.1 Unit & Property-Based Testing (PBT)

| Test Type                                   | Library / Tool | Core Scenario / Property to Test                                                                                                                                                                                                                                       |
|:--------------------------------------------|:---------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| 
| **Unit Testing**                            | JUnit 5        | Edge Cases: Cache capacity of 1, capacity of 0 (error handling), inserting null keys/values, get on a non-existent key.                                                                                                                                                |
| **Unit Testing**                            | JUnit 5        | State Transitions: put on an existing key must move it to MRU (Head) but not change the cache size.                                                                                                                                                                    |
| **Property-Based Testing**                  | jqwik          | Eviction Invariant: Generate a random sequence of get and put operations where the total number of operations exceeds capacity. Property: The key evicted must always be the key that has the largest gap since its last access.                                       |
| **Integration Testing (Concurrency Focus)** | JUnit 5        | Thread Safety: Run thousands of concurrent put and get operations from multiple threads, assert that no size mismatch or NullPointerException occurs, and that the total number of items never exceeds capacity. (Requires synchronized methods or explicit Lock use). |

### 4.2 Performance Testing

| Test Type                      | Library / Tool                               | Scenario / Metric to Test                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|:-------------------------------|:---------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------| 
| **Micro-Benchmark**            | Java Microbenchmark Harness                  | Measure the execution time of individual get and put operations.  Goal: Demonstrate that the time taken remains flat (O(1)) as the cache capacity is scaled up from 100 to 1,000,000 items.                                                                                                                                                                                                                                                                     |
| **End-to-End (E2E) Load Test** | Custom TCP Client/Server (The Cache Service) | Goal 1 (Stress Behavior): Determine the maximum sustainable throughput (Requests/sec) by gradually increasing the concurrent thread count (N). Metric: Throughput (Requests/sec).  Goal 2 (Latency Under Load): Assess the system's stability and identify bottlenecks by measuring Average Latency (Response Time) at peak throughput. Expected Result: Latency should remain stable before system capacity is exhausted, confirming no crippling locks exist. |