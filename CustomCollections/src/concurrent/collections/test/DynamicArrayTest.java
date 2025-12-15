package concurrent.collections.test;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import concurrent.collections.DynamicArray;

public class DynamicArrayTest {
	
	@Test
    public void testBasicInsertAndGet() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(42);
        arr.insert(100);
        
        assertEquals(Integer.valueOf(42), arr.get(0));
        assertEquals(Integer.valueOf(100), arr.get(1));
        assertEquals(2, arr.getNumberOfElements());
    }
	
	@Test
    public void testArrayExpansion() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Insert more than initial capacity (10)
        for (int i = 0; i < 25; i++) {
            arr.insert(i);
        }
        
        // Verify all elements are present
        for (int i = 0; i < 25; i++) {
            assertEquals(Integer.valueOf(i), arr.get(i));
        }
        assertEquals(25, arr.getNumberOfElements());
    }
	
	@Test
    public void testSetOperation() throws Exception {
        DynamicArray<String> arr = new DynamicArray<>();
        arr.insert("original");
        arr.set(0, "modified");
        
        assertEquals("modified", arr.get(0));
    }
	
	@Test
    public void testIterator() {
        DynamicArray<Integer> arr = new DynamicArray<>();
        for (int i = 0; i < 5; i++) {
            arr.insert(i * 10);
        }
        
        List<Integer> collected = new ArrayList<>();
        Iterator<Integer> iter = arr.iterator();
        while (iter.hasNext()) {
            collected.add(iter.next());
        }
        
        assertEquals(Arrays.asList(0, 10, 20, 30, 40), collected);
    }
	
	@Test
    public void testConcurrentInserts() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        int numThreads = 10;
        int insertsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        Set<Integer> expectedElements = ConcurrentHashMap.newKeySet();
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < insertsPerThread; i++) {
                    arr.insert(threadId * 1000 + i);
                    expectedElements.add(threadId * 1000 + i);
                }
                latch.countDown();
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // CRITICAL: Should have exactly numThreads * insertsPerThread elements
        assertEquals(numThreads * insertsPerThread, arr.getNumberOfElements());
        
        // CRITICAL: No null values in the valid range
        for (int i = 0; i < arr.getNumberOfElements(); i++) {
            Integer val = arr.get(i);
            assertNotNull("Found null at index " + i, val);
        }
        
        // Verify the presence of each (distinct) element
        Iterator<Integer> arrIter = arr.iterator();
        while (arrIter.hasNext()) {
        	Integer element = arrIter.next();
        	assertTrue(expectedElements.contains(element));
        	expectedElements.remove(element);
        }
	}
	
	@Test
	public void testHighConcurrencyStress() throws Exception {
		DynamicArray<Integer> arr = new DynamicArray<>();
        int numThreads = 50; //For increased concurrency
        int insertsPerThread = 1000;
        AtomicInteger errors = new AtomicInteger(0);
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        Set<Integer> expectedElements = ConcurrentHashMap.newKeySet();
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
            	try {
            		barrier.await();
                    for (int i = 0; i < insertsPerThread; i++) {
                    	int val = threadId * insertsPerThread + i;
                        arr.insert(val);
                        expectedElements.add(val);
                    }
            	} catch (Exception e) {
            		errors.incrementAndGet();
                    e.printStackTrace();
            	}
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        // CRITICAL: Should have exactly numThreads * insertsPerThread elements
        assertEquals(numThreads * insertsPerThread, arr.getNumberOfElements());
        
        for (int i = 0; i < arr.getNumberOfElements(); i++) {
            Integer val = arr.get(i);
            assertNotNull("Found null at index " + i, val);
        }
        
        //Verify the presence of each (distinct) element
        int i = 0;
        Iterator<Integer> arrIter = arr.iterator();
        while (arrIter.hasNext()) {
        	Integer elementFromIterator = arrIter.next();
        	assertTrue(expectedElements.contains(elementFromIterator));
        	assertTrue(expectedElements.contains(arr.get(i)));
        	expectedElements.remove(elementFromIterator);
        	i++;
        }
	}
	
	@Test
    public void testConcurrentReadsAndWrites() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        int totalNumberOfElements = 100;
        
        // Pre-populate
        for (int i = 0; i < totalNumberOfElements; i++) {
            arr.insert(i);
        }
        
        assertEquals(totalNumberOfElements, arr.getNumberOfElements());
        
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);
        
        // 5 reader threads
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        int idx = ThreadLocalRandom.current().nextInt(totalNumberOfElements);
                        Integer val = arr.get(idx);
                        if (val == null) {
                        	errors.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                	errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 5 writer threads (set operations)
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 500; j++) {
                        int idx = ThreadLocalRandom.current().nextInt(totalNumberOfElements);
                        arr.set(idx, j);
                    }
                } catch (Exception e) {
                	errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals("Errors occurred during concurrent access", 0, errors.get());
        assertEquals(totalNumberOfElements, arr.getNumberOfElements());
    }
	
	@Test
    public void testIteratorDuringConcurrentInserts() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate with known values
        for (int i = 0; i < 20; i++) {
            arr.insert(i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicBoolean insertsComplete = new AtomicBoolean(false);
        List<List<Integer>> snapshots = new CopyOnWriteArrayList<>();
        
        // Inserter thread
        executor.submit(() -> {
            for (int i = 20; i < 100; i++) {
                arr.insert(i);
                try { Thread.sleep(1); } catch (InterruptedException e) {}
            }
            insertsComplete.set(true);
        });
        
        // Iterator threads - create snapshots
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                while (!insertsComplete.get()) {
                    Iterator<Integer> iter = arr.iterator();
                    List<Integer> snapshot = new ArrayList<>();
                    while (iter.hasNext()) {
                        snapshot.add(iter.next());
                    }
                    snapshots.add(snapshot);
                    try { Thread.sleep(5); } catch (InterruptedException e) {}
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        for (List<Integer> snapshot : snapshots) {
            assertFalse("Iterator returned empty snapshot", snapshot.isEmpty());
            // Check for nulls
            for (Integer val : snapshot) {
                assertNotNull("Iterator returned null value", val);
            }
        }
        
        for (int i = 0 ; i < 100 ; i++) {
        	assertTrue(arr.get(i) == i);
        }
        
        int i = 0;
        Iterator<Integer> arrIter = arr.iterator();
        while (arrIter.hasNext()) {
        	assertTrue(arrIter.next() == arr.get(i));
        	i++;
        }
    }
	
	@Test
    public void testEmptyArray() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        assertEquals(0, arr.getNumberOfElements());
        
        Iterator<Integer> iter = arr.iterator();
        assertFalse("Empty array should have no elements to iterate", iter.hasNext());
    }
	
	@Test(expected = NoSuchElementException.class)
    public void testIteratorNextOnEmpty() {
        DynamicArray<Integer> arr = new DynamicArray<>();
        Iterator<Integer> iter = arr.iterator();
        iter.next(); // Should throw
    }
	
	@Test
    public void testGetOutOfBounds() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(1);
        
        // Should return null or throw - document expected behavior
        Integer result = arr.get(100);
        assertNull("Out of bounds should return null", result);
    }
	
	@Test
    public void testMultipleExpansions() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Force multiple doublings: 10 -> 20 -> 40 -> 80
        for (int i = 0; i < 75; i++) {
            arr.insert(i);
        }
        
        // Verify all elements survive multiple expansions
        for (int i = 0; i < 75; i++) {
            assertEquals(Integer.valueOf(i), arr.get(i));
        }
    }
}