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
	
	private final int ARRAY_INITIAL_SIZE = 10;
	
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
        
        // Insert more than initial capacity (ARRAY_INITIAL_SIZE)
        for (int i = 0; i < ARRAY_INITIAL_SIZE * 2.5; i++) {
            arr.insert(i);
        }
        
        // Verify all elements are present
        for (int i = 0; i < ARRAY_INITIAL_SIZE * 2.5; i++) {
            assertEquals(Integer.valueOf(i), arr.get(i));
        }
        
        assertEquals((int) (ARRAY_INITIAL_SIZE * 2.5), arr.getNumberOfElements());
    }
	
	@Test
    public void testSetOperation() throws Exception {
        DynamicArray<String> arr = new DynamicArray<>();
        arr.insert("original");
        arr.set(0, "modified");
        
        assertEquals("modified", arr.get(0));
        assertEquals(1, arr.getNumberOfElements());
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
                    arr.insert(threadId * insertsPerThread + i);
                    expectedElements.add(threadId * insertsPerThread + i);
                }
                latch.countDown();
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals(numThreads * insertsPerThread, arr.getNumberOfElements());
        
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
        int numThreads = 100; //For increased concurrency
        int insertsPerThread = 5000;
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
        executor.awaitTermination(60, TimeUnit.SECONDS);
        
        assertEquals(numThreads * insertsPerThread, arr.getNumberOfElements());
        
        for (int i = 0; i < arr.getNumberOfElements(); i++) {
            Integer val = arr.get(i);
            assertNotNull("Found null at index " + i, val);
        }
        
        // Verify the presence of each (distinct) element 
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
	
	@Test(expected = Exception.class)
    public void testGetOutOfBounds() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(1);
        
        arr.get(100); // Should throw
    }
	
	@Test
    public void testMultipleExpansions() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        for (int i = 0; i < ARRAY_INITIAL_SIZE * 7.5; i++) {
            arr.insert(i);
        }
        
        // Verify all elements survive multiple expansions
        for (int i = 0; i < ARRAY_INITIAL_SIZE * 7.5; i++) {
            assertEquals(Integer.valueOf(i), arr.get(i));
        }
    }
	
	@Test
	public void testPopSingleElement() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(42);
        arr.pop(0);
        
        assertEquals(0, arr.getNumberOfElements());
    }
	
	@Test(expected = Exception.class)
	public void testInvalidInput() throws Exception {
		DynamicArray<Integer> arr = new DynamicArray<>();
        arr.pop(-1);
	}
	
	@Test(expected = Exception.class)
	public void testOutOfBoundInput() throws Exception {
		DynamicArray<Integer> arr = new DynamicArray<>();
        arr.pop(0);
	}
	
	@Test
    public void testPopMiddleElement() throws Exception {
        DynamicArray<String> arr = new DynamicArray<>();
        arr.insert("A");
        arr.insert("B");
        arr.insert("C");
        arr.insert("D");
        
        arr.pop(1); // Remove "B"
        
        assertEquals(3, arr.getNumberOfElements());
        assertEquals("A", arr.get(0));
        assertEquals("C", arr.get(1)); // Shifted left
        assertEquals("D", arr.get(2)); // Shifted left
    }
	
	@Test
    public void testPopLastElement() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(10);
        arr.insert(20);
        arr.insert(30);
        
        arr.pop(2); // Remove last element
        
        assertEquals(2, arr.getNumberOfElements());
        assertEquals(Integer.valueOf(10), arr.get(0));
        assertEquals(Integer.valueOf(20), arr.get(1));
    }
	
	@Test
	public void testNullPostElementRemoval() throws Exception {
		DynamicArray<Integer> arr = new DynamicArray<>();
		arr.insert(10);	
		arr.pop(0);
		assertNull("Should return null", arr.get(0));
	}
	
	@Test
    public void testPopUntilEmpty() throws Exception {
        DynamicArray<String> arr = new DynamicArray<>();
        arr.insert("A");
        arr.insert("B");
        arr.insert("C");
        
        arr.pop(1); // Remove B
        arr.pop(1); // Remove C (now at index 1)
        arr.pop(0); // Remove A
        
        assertEquals(0, arr.getNumberOfElements());
    }
	
	@Test
    public void testMultipleConsecutivePops() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Populate at least 10 values to make the test deterministic
        int numberOfElements = Math.max(10, ARRAY_INITIAL_SIZE);
        for (int i = 0; i < numberOfElements; i++) {
            arr.insert(i * 10);
        }
        
        arr.pop(5); // Remove 50
        arr.pop(5); // Remove 60 (shifted to index 5)
        arr.pop(5); // Remove 70 (shifted to index 5)
        
        assertEquals(numberOfElements - 3, arr.getNumberOfElements());
        assertEquals(Integer.valueOf(0), arr.get(0));
        assertEquals(Integer.valueOf(40), arr.get(4));
        assertEquals(Integer.valueOf(80), arr.get(5)); // 80 shifted down
    }
	
	@Test(expected = Exception.class)
    public void testPopNegativeIndex() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(10);
        arr.pop(-1); // Should throw exception
    }
	
	@Test(expected = Exception.class)
    public void testPopOutOfBounds() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(10);
        arr.insert(20);
        arr.pop(5); // Index 5 doesn't exist
    }
	
	@Test(expected = Exception.class)
    public void testPopExactlyAtBoundary() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(10);
        arr.insert(20);
        arr.pop(2); // Index equals getNumberOfElements(), should fail
    }
	
	@Test
    public void testIteratorAfterPop() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        for (int i = 0; i < 5; i++) {
            arr.insert(i * 10);
        }
        
        arr.pop(2); // Remove 20
        
        List<Integer> collected = new ArrayList<>();
        Iterator<Integer> iter = arr.iterator();
        while (iter.hasNext()) {
            collected.add(iter.next());
        }
        
        assertEquals(Arrays.asList(0, 10, 30, 40), collected);
        assertEquals(4, collected.size());
    }
	
	@Test
    public void testIteratorDuringPop() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        for (int i = 0; i < 10; i++) {
            arr.insert(i);
        }
        
        // Create iterator before pop
        Iterator<Integer> iterBefore = arr.iterator();
        
        arr.pop(5); // Remove element 5
        
        // Create iterator after pop
        Iterator<Integer> iterAfter = arr.iterator();
        
        // Before iterator should have snapshot of 10 elements
        int countBefore = 0;
        while (iterBefore.hasNext()) {
            iterBefore.next();
            countBefore++;
        }
        
        // After iterator should have 9 elements
        int countAfter = 0;
        while (iterAfter.hasNext()) {
            iterAfter.next();
            countAfter++;
        }
        
        assertEquals("Iterator before pop should see 10 elements", 10, countBefore);
        assertEquals("Iterator after pop should see 9 elements", 9, countAfter);
    }
    
    @Test
    public void testConcurrentPops() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate with 100 elements
        for (int i = 0; i < 100; i++) {
            arr.insert(i);
        }
        
        int numThreads = 10;
        int popsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successfulPops = new AtomicInteger(0);
        
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < popsPerThread; i++) {
                        // Always pop from index 0 (head removal)
                        arr.pop(0);
                        successfulPops.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        assertEquals("Not all pops succeeded", numThreads * popsPerThread, successfulPops.get());
        assertEquals("Final array size incorrect", numThreads * popsPerThread, arr.getNumberOfElements());
        
        // Verify remaining elements are consecutive (50-99)
        int expectedVal = 50;
        for (int i = 0; i < arr.getNumberOfElements(); i++) {
        	assertEquals("Iterator after pop should see 9 elements", expectedVal, arr.get(i).intValue());
        	expectedVal++;
        }
    }
    
    @Test
    public void testConcurrentInsertAndPop() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Start with some elements
        for (int i = 0; i < 20; i++) {
            arr.insert(i * 100);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);
        AtomicInteger insertCount = new AtomicInteger(0);
        AtomicInteger popCount = new AtomicInteger(0);
        
        // 2 inserter threads
        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < 50; j++) {
                        arr.insert(threadId * 10000 + j);
                        insertCount.incrementAndGet();
                        Thread.sleep(1); // Small delay
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        // 2 popper threads
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    Thread.sleep(10); // Let some inserts happen first
                    for (int j = 0; j < 30; j++) {
                        try {
                            arr.pop(0); // Pop from head
                            popCount.incrementAndGet();
                            Thread.sleep(2);
                        } catch (Exception e) {
                            // Might fail if array is empty
                            break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        int expectedSize = 20 + insertCount.get() - popCount.get();
        //int expectedSize = 120;
        assertEquals("Array size mismatch after concurrent ops", expectedSize, arr.getNumberOfElements());
        
        // Verify no nulls in valid range
        for (int i = 0; i < arr.getNumberOfElements(); i++) {
        	if (arr.get(i) == null) {
        		System.out.println("encountered null at "+i);
        	}
            assertNotNull("Found null at index " + i + " after concurrent ops", arr.get(i));
        }
    }
    
    @Test
    public void testPopDuringArrayExpansion() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate close to expansion threshold
        for (int i = 0; i < (ARRAY_INITIAL_SIZE - 2); i++) {
            arr.insert(i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CyclicBarrier barrier = new CyclicBarrier(3);
        AtomicInteger errors = new AtomicInteger(0);
        
        // Thread 1: Trigger expansion with inserts
        executor.submit(() -> {
            try {
                barrier.await();
                for (int i = 0; i < 5; i++) {
                    arr.insert(100 + i); // Will trigger expansion
                }
            } catch (Exception e) {
                errors.incrementAndGet();
                e.printStackTrace();
            }
        });
        
        // Thread 2: Try to pop during expansion
        executor.submit(() -> {
            try {
                barrier.await();
                Thread.sleep(1); // Let expansion start
                for (int i = 0; i < 3; i++) {
                    arr.pop(0);
                }
            } catch (Exception e) {
                errors.incrementAndGet();
                e.printStackTrace();
            }
        });
        
        // Thread 3: Try to read during pop/expansion
        executor.submit(() -> {
            try {
                barrier.await();
                for (int i = 0; i < 20; i++) {
                    try {
                        Integer val = arr.get(0);
                        // Just reading, not asserting specific values
                    } catch (Exception e) {
                        // Might be out of bounds
                    }
                    Thread.sleep(2);
                }
            } catch (Exception e) {
                errors.incrementAndGet();
                e.printStackTrace();
            }
        });
        
        executor.shutdown();
        executor.awaitTermination(20, TimeUnit.SECONDS);
        
        assertEquals("Exceptions occurred during concurrent pop/expansion", 0, errors.get());
        
        // Verify array integrity
        int finalSize = arr.getNumberOfElements();
        for (int i = 0; i < finalSize; i++) {
            assertNotNull("Found null after expansion/pop stress", arr.get(i));
        }
    }
    
    @Test
    public void testPopWithSingleElement() throws Exception {
        DynamicArray<String> arr = new DynamicArray<>();
        arr.insert("only");
        
        arr.pop(0);
        
        assertEquals(0, arr.getNumberOfElements());
    }
    
    @Test
    public void testAlternatingInsertAndPop() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        for (int i = 0; i < 10; i++) {
            arr.insert(i);
            if (i % 2 == 1) {
                arr.pop(0); // Remove first element every other insert
            }
        }
        
        // Should have 5 elements remaining
        assertEquals(5, arr.getNumberOfElements());
    }
    
    @Test
    public void testPopAfterExpansion() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Trigger expansion
        for (int i = 0; i < ARRAY_INITIAL_SIZE * 2.5; i++) {
            arr.insert(i);
        }
        
        // Now pop several elements
        arr.pop(0);
        arr.pop(0);
        arr.pop(0);
        
        assertEquals((int)(ARRAY_INITIAL_SIZE * 2.5) - 3, arr.getNumberOfElements());
        assertEquals(Integer.valueOf(3), arr.get(0));
    }
    
    @Test
    public void testGetAfterPop() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(10);
        arr.insert(20);
        arr.insert(30);
        arr.insert(40);
        
        arr.pop(1); // Remove 20
        
        // Access each index to verify shift
        assertEquals(Integer.valueOf(10), arr.get(0));
        assertEquals(Integer.valueOf(30), arr.get(1)); // 30 shifted down
        assertEquals(Integer.valueOf(40), arr.get(2)); // 40 shifted down
        
        // Old index 3 should now be out of bounds
        assertNull("Index 3 should be out of bounds", arr.get(3));
    }
    
    @Test(timeout = 30000)
    public void testPopStress() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate
        for (int i = 0; i < 200; i++) {
            arr.insert(i);
        }
        
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        AtomicInteger successfulPops = new AtomicInteger(0);
        AtomicInteger failedPops = new AtomicInteger(0);
        
        for (int t = 0; t < numThreads; t++) {
            executor.submit(() -> {
                for (int i = 0; i < 5; i++) {
                    try {
                        // Pop from random positions
                        int size = arr.getNumberOfElements();
                        if (size > 0) {
                            int index = ThreadLocalRandom.current().nextInt(size);
                            arr.pop(index);
                            successfulPops.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failedPops.incrementAndGet();
                    }
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(20, TimeUnit.SECONDS);
        
        System.out.println("Successful pops: " + successfulPops.get());
        System.out.println("Failed pops: " + failedPops.get());
        System.out.println("Final size: " + arr.getNumberOfElements());
        
        int finalSize = arr.getNumberOfElements();
        assertEquals("Size mismatch", 200 - successfulPops.get(), finalSize);
        
        // Verify no nulls
        for (int i = 0; i < finalSize; i++) {
            assertNotNull("Found null at " + i, arr.get(i));
        }
    }
    
    @Test
    public void testSetMultipleElements() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        for (int i = 0; i < 5; i++) {
            arr.insert(i * 10);
        }
        
        arr.set(0, 999);
        arr.set(2, 888);
        arr.set(4, 777);
        
        assertEquals(Integer.valueOf(999), arr.get(0));
        assertEquals(Integer.valueOf(10), arr.get(1));   // Unchanged
        assertEquals(Integer.valueOf(888), arr.get(2));
        assertEquals(Integer.valueOf(30), arr.get(3));   // Unchanged
        assertEquals(Integer.valueOf(777), arr.get(4));
    }
    
    @Test
    public void testSetOverwritesPreviousValue() throws Exception {
        DynamicArray<String> arr = new DynamicArray<>();
        arr.insert("first");
        
        arr.set(0, "second");
        arr.set(0, "third");
        arr.set(0, "fourth");
        
        assertEquals("fourth", arr.get(0));
    }
    
    @Test
    public void testSetWithNullValue() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(100);
    
        arr.set(0, null);
        
        assertNull("Should be able to set null", arr.get(0));
    }
    
    @Test
    public void testSetDoesNotChangeSize() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        for (int i = 0; i < 10; i++) {
            arr.insert(i);
        }
        
        int sizeBefore = arr.getNumberOfElements();
        
        arr.set(5, 999);
        arr.set(0, 888);
        
        assertEquals("Set should not change array size", sizeBefore, arr.getNumberOfElements());
    }
    
    @Test(expected = Exception.class)
    public void testSetOutOfBounds() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(1);
        arr.insert(2);
        
        arr.set(10, 999); // Index 10 doesn't exist
    }
    
    @Test(expected = Exception.class)
    public void testSetElementDoesNotExist() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(1);
        arr.insert(2);
        
        arr.set(2, 999); // Index 2 doesn't have an element 
    }
    
    @Test(expected = Exception.class)
    public void testSetNegativeIndex() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(1);
        
        arr.set(-1, 999);
    }
    
    @Test(expected = Exception.class)
    public void testSetOnEmptyArray() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.set(0, 999); // No elements exist
    }
    
    @Test
    public void testSetAtLastValidIndex() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(10);
        arr.insert(20);
        arr.insert(30);
        
        arr.set(2, 999); // Index 2 is last valid index
        
        assertEquals(Integer.valueOf(999), arr.get(2));
    }
    
    @Test
    public void testSetAfterExpansion() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Trigger expansion (initial size 10)
        for (int i = 0; i < ARRAY_INITIAL_SIZE * 2.5; i++) {
            arr.insert(i);
        }
        
        // Set values across old and new array regions
        arr.set(0, 555);   // In original array region
        arr.set(ARRAY_INITIAL_SIZE * 2, 1515); // In expanded region
        
        assertEquals(Integer.valueOf(555), arr.get(0));
        assertEquals(Integer.valueOf(1515), arr.get(ARRAY_INITIAL_SIZE * 2));
    }
    
    @Test
    public void testSetDuringExpansion() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate close to threshold
        for (int i = 0; i < (ARRAY_INITIAL_SIZE - 1); i++) {
            arr.insert(i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger errors = new AtomicInteger(0);
        
        // Thread 1: Trigger expansion
        executor.submit(() -> {
            try {
                barrier.await();
                arr.insert(999); // Will trigger expansion
            } catch (Exception e) {
                errors.incrementAndGet();
                e.printStackTrace();
            }
        });
        
        // Thread 2: Try to set during expansion
        executor.submit(() -> {
            try {
                barrier.await();
                Thread.sleep(2); // Let expansion start
                arr.set(ARRAY_INITIAL_SIZE - 1, 555);  // Set in existing region
            } catch (Exception e) {
                errors.incrementAndGet();
                e.printStackTrace();
            }
        });
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        assertEquals("Errors during concurrent set/expansion", 0, errors.get());
        assertEquals(Integer.valueOf(555), arr.get(ARRAY_INITIAL_SIZE - 1));
    }
    
    /* ===========================
     * ITERATOR CONSISTENCY TESTS
     * ===========================
     */ 
    
    @Test
    public void testIteratorAfterSet() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        for (int i = 0; i < 5; i++) {
            arr.insert(i * 10);
        }
        
        arr.set(2, 999);
        
        List<Integer> collected = new ArrayList<>();
        Iterator<Integer> iter = arr.iterator();
        while (iter.hasNext()) {
            collected.add(iter.next());
        }
        
        assertEquals(Arrays.asList(0, 10, 999, 30, 40), collected);
    }
    
    @Test
    public void testIteratorSnapshotBeforeSet() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        for (int i = 0; i < 5; i++) {
            arr.insert(i);
        }
        
        // Create iterator before modification
        Iterator<Integer> iterBefore = arr.iterator();
        
        // Modify the array
        arr.set(2, 999);
        
        // Create iterator after modification
        Iterator<Integer> iterAfter = arr.iterator();
        
        // Before iterator should have old values
        List<Integer> beforeValues = new ArrayList<>();
        while (iterBefore.hasNext()) {
            beforeValues.add(iterBefore.next());
        }
        
        // After iterator should have new values
        List<Integer> afterValues = new ArrayList<>();
        while (iterAfter.hasNext()) {
            afterValues.add(iterAfter.next());
        }
        
        assertEquals(Arrays.asList(0, 1, 2, 3, 4), beforeValues);
        assertEquals(Arrays.asList(0, 1, 999, 3, 4), afterValues);
    }
    
    @Test
    public void testConcurrentSetsOnDifferentIndices() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate
        for (int i = 0; i < 100; i++) {
            arr.insert(i);
        }
        
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        // Each thread modifies its own range of 10 elements
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        int index = threadId * 10 + i;
                        arr.set(index, threadId * 1000 + i);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Verify each thread's modifications succeeded
        for (int t = 0; t < numThreads; t++) {
            for (int i = 0; i < 10; i++) {
                int index = t * 10 + i;
                Integer expected = t * 1000 + i;
                assertEquals("Mismatch at index " + index, expected, arr.get(index));
            }
        }
    }
    
    @Test
    public void testConcurrentSetsOnSameIndex() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(1000);
        
        int numThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        Set<Integer> valuesWritten = ConcurrentHashMap.newKeySet();
        
        // All threads try to set index 0 simultaneously
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    barrier.await(); // Synchronize start
                    arr.set(0, threadId);
                    valuesWritten.add(threadId);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        
        // Final value should be one of the written values
        Integer finalValue = arr.get(0);
        assertNotNull("Final value should not be null", finalValue);
        assertTrue("Final value should be from one of the threads", 
                   valuesWritten.contains(finalValue));
        
        // All threads should have completed
        assertEquals(numThreads, valuesWritten.size());
    }
    
    @Test
    public void testConcurrentSetAndGet() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate
        for (int i = 0; i < 50; i++) {
            arr.insert(i);
        }
        
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger nullReads = new AtomicInteger(0);
        
        // 10 setter threads
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        int index = ThreadLocalRandom.current().nextInt(50);
                        arr.set(index, threadId * 1000 + j);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 10 getter threads
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 200; j++) {
                        int index = ThreadLocalRandom.current().nextInt(50);
                        Integer val = arr.get(index);
                        // Values might be null if set to null, but shouldn't fail
                        if (val == null) {
                            nullReads.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();
        
        System.out.println("Null reads encountered: " + nullReads.get());
        // Test completes successfully if no exceptions thrown
    }
    
    @Test
    public void testConcurrentSetAndInsert() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate
        for (int i = 0; i < 20; i++) {
            arr.insert(i);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);
        AtomicInteger errors = new AtomicInteger(0);
        
        // 2 setter threads
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < 50; j++) {
                        arr.set(j % 20, j * 100);
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }
        
        // 2 inserter threads
        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < 30; j++) {
                        arr.insert(threadId * 10000 + j);
                        Thread.sleep(2);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        assertEquals("Errors during concurrent set/insert", 0, errors.get());
        assertEquals("Array size should be 20 + 60 inserts", 80, arr.getNumberOfElements());
    }
    
    @Test
    public void testConcurrentSetAndPop() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate
        for (int i = 0; i < 50; i++) {
            arr.insert(i * 10);
        }
        
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CyclicBarrier barrier = new CyclicBarrier(4);
        AtomicInteger setErrors = new AtomicInteger(0);
        AtomicInteger popErrors = new AtomicInteger(0);
        
        // 2 setter threads
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < 100; j++) {
                        try {
                            int size = arr.getNumberOfElements();
                            if (size > 0) {
                                int index = ThreadLocalRandom.current().nextInt(size);
                                arr.set(index, j);
                            }
                        } catch (Exception e) {
                            // Index might become invalid due to pop
                        }
                        Thread.sleep(1);
                    }
                } catch (Exception e) {
                    setErrors.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }
        
        // 2 popper threads
        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    barrier.await();
                    for (int j = 0; j < 10; j++) {
                        try {
                            arr.pop(0); // Pop from head
                        } catch (Exception e) {
                            // Might fail if array becomes empty
                        }
                        Thread.sleep(5);
                    }
                } catch (Exception e) {
                    popErrors.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        assertEquals("Set thread errors", 0, setErrors.get());
        assertEquals("Pop thread errors", 0, popErrors.get());
        
        // Verify remaining elements are non-null
        int finalSize = arr.getNumberOfElements();
        for (int i = 0; i < finalSize; i++) {
            // Elements might be null if explicitly set to null, but check no crashes
            arr.get(i);
        }
    }
    
    @Test
    public void testSetSameValueMultipleTimes() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        arr.insert(100);
        
        for (int i = 0; i < 10; i++) {
            arr.set(0, 999);
        }
        
        assertEquals(Integer.valueOf(999), arr.get(0));
    }
    
    @Test
    public void testSetAllElements() throws Exception {
        DynamicArray<String> arr = new DynamicArray<>();
        for (int i = 0; i < 10; i++) {
            arr.insert("old" + i);
        }
        
        for (int i = 0; i < 10; i++) {
            arr.set(i, "new" + i);
        }
        
        for (int i = 0; i < 10; i++) {
            assertEquals("new" + i, arr.get(i));
        }
    }
    
    @Test
    public void testSetWithDifferentTypes() throws Exception {
        DynamicArray<Object> arr = new DynamicArray<>();
        arr.insert("string");
        arr.insert(123);
        arr.insert(new ArrayList<>());
        
        arr.set(0, 456);
        arr.set(1, "replacement");
        arr.set(2, null);
        
        assertEquals(456, arr.get(0));
        assertEquals("replacement", arr.get(1));
        assertNull(arr.get(2));
    }
    
    @Test
    public void testSetAfterMultipleExpansions() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Trigger multiple expansions: 10 -> 20 -> 40 -> 80
        for (int i = 0; i < ARRAY_INITIAL_SIZE * 7; i++) {
            arr.insert(i);
        }
        
        // Set values across different expansion boundaries
        arr.set(0, 555);   // Original region
        arr.set((ARRAY_INITIAL_SIZE+1), 1515); // After first expansion
        arr.set((ARRAY_INITIAL_SIZE*2)+1, 3535); // After second expansion
        arr.set((ARRAY_INITIAL_SIZE*4)+1, 7070); // After third expansion
        
        assertEquals(Integer.valueOf(555), arr.get(0));
        assertEquals(Integer.valueOf(1515), arr.get(ARRAY_INITIAL_SIZE+1));
        assertEquals(Integer.valueOf(3535), arr.get((ARRAY_INITIAL_SIZE*2)+1));
        assertEquals(Integer.valueOf(7070), arr.get((ARRAY_INITIAL_SIZE*4)+1));
    }
    
    @Test(timeout = 30000)
    public void testSetStress() throws Exception {
        DynamicArray<Integer> arr = new DynamicArray<>();
        
        // Pre-populate
        int arraySize = 100;
        for (int i = 0; i < arraySize; i++) {
            arr.insert(i);
        }
        
        int numThreads = 50;
        int setsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CyclicBarrier barrier = new CyclicBarrier(numThreads);
        AtomicInteger totalSets = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);
        
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    barrier.await(); // Maximize contention
                    for (int i = 0; i < setsPerThread; i++) {
                        int index = ThreadLocalRandom.current().nextInt(arraySize);
                        arr.set(index, threadId * 1000000 + i);
                        totalSets.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                    e.printStackTrace();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(20, TimeUnit.SECONDS);
        
        System.out.println("Total sets attempted: " + totalSets.get());
        System.out.println("Errors: " + errors.get());
        
        assertEquals("Errors during stress test", 0, errors.get());
        assertEquals("All sets should have succeeded", 
                    numThreads * setsPerThread, totalSets.get());
        
        // Verify array integrity
        assertEquals("Array size should remain unchanged", arraySize, arr.getNumberOfElements());
        
        // All values should be non-null (unless explicitly set to null)
        int nonNullCount = 0;
        for (int i = 0; i < arraySize; i++) {
            if (arr.get(i) != null) {
                nonNullCount++;
            }
        }
        
        System.out.println("Non-null elements: " + nonNullCount);
        assertTrue("Most elements should be non-null", nonNullCount > 0);
    }
}