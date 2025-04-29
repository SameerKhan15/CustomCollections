package concurrent.collections.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import concurrent.collections.ConcurrentLinkedHashSet;

public class ConcurrentLinkedHashSetTests {
	
	public void testDelete() {
		int numThreads = 100;
		int numIterationsPerThread = 100;
		
		ConcurrentLinkedHashSet<String> concurrentSet = new ConcurrentLinkedHashSet<>();
		AtomicInteger threadNumberGen = new AtomicInteger();
		
		List<Thread> writerThreads = new ArrayList<>();
		CountDownLatch threadsLatch = new CountDownLatch(numThreads);
		
		for (int i = 0 ; i < numThreads ; i++) {
			Thread writer = new Thread(() -> {
				String tid = String.valueOf(threadNumberGen.getAndIncrement());
				ThreadLocalRandom random = ThreadLocalRandom.current();
				
				List<String> toBeInsertedElements = new ArrayList<>();
				for (int a = 0 ; a < numIterationsPerThread ; a++) {
					toBeInsertedElements.add(tid + "_" + String.valueOf(a));
				}
				
				//decr its latch
				threadsLatch.countDown();
				
				//Wait for latch to reach equal to number of threads
				try {
					threadsLatch.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				for (int b = 0 ; b < numIterationsPerThread ; b++) {
					try {
						Thread.sleep(random.nextInt(5));
						concurrentSet.add(toBeInsertedElements.get(b));
						Thread.sleep(random.nextInt(5));
						concurrentSet.remove(toBeInsertedElements.get(b));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			
			writerThreads.add(writer);
		}
		
		for (Thread th : writerThreads) {
			th.start();		
		}
		
		for (Thread th : writerThreads) {
			try {
				th.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}		
		}
		
		//Global validation
		boolean sizeCheckSuccess = (concurrentSet.size() == 0);	
		System.out.println("[testDel] SizeCheck Success:"+sizeCheckSuccess);
	}
	
	public void testAdd() {
		int numThreads = 10;
		int numIterationsPerThread = 100;
		ConcurrentLinkedHashSet<String> concurrentSet = new ConcurrentLinkedHashSet<>();
		AtomicInteger threadNumberGen = new AtomicInteger();
		
		List<Thread> writerThreads = new ArrayList<>();
		AtomicBoolean sequencingTestFailure = new AtomicBoolean(false);
		CountDownLatch threadsLatch = new CountDownLatch(numThreads);
		
		for (int i = 0 ; i < numThreads ; i++) {
			Thread writer = new Thread(() -> {
				String tid = String.valueOf(threadNumberGen.getAndIncrement());
				
				List<String> toBeInsertedElements = new ArrayList<>();
				for (int a = 0 ; a < numIterationsPerThread ; a++) {
					toBeInsertedElements.add(tid + "_" + String.valueOf(a));
				}
				
				Collections.shuffle(toBeInsertedElements);
				
				//decr its latch
				threadsLatch.countDown();
				
				//Wait for latch to reach equal to number of threads
				try {
					threadsLatch.await();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				for (int b = 0 ; b < numIterationsPerThread ; b++) {
					try {
						concurrentSet.add(toBeInsertedElements.get(b));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				//Validation
				Iterator<String> concurrentSetIter = concurrentSet.iterator();
				int threadElementsPointer = 0;
				
				while (concurrentSetIter.hasNext()) {
					String setElement = concurrentSetIter.next();
					if (threadElementsPointer == (toBeInsertedElements.size() - 1)) {
						break;
					}
					
					if (toBeInsertedElements.contains(setElement)) {
						String elementToValidate = toBeInsertedElements.get(threadElementsPointer);
						if (elementToValidate != setElement) {
							System.out.println("[testAdd] Elements Sequencing Test Failure for thread:"+tid);
							sequencingTestFailure.set(true);
							break;
						}
						threadElementsPointer++;
					}
				}
			});
			
			writerThreads.add(writer);
		}
		
		for (Thread th : writerThreads) {
			th.start();		
		}
		
		for (Thread th : writerThreads) {
			try {
				th.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}		
		}
		
		//Global validation
		boolean sizeCheckSuccess = (concurrentSet.size() == numThreads * numIterationsPerThread);	
		System.out.println("[testAdd] SizeCheck Success:"+sizeCheckSuccess);
		System.out.println("[testAdd] SequencingTest Success:"+!sequencingTestFailure.get());
	}
	
	public void testOperationsSingleThreaded() {
		ConcurrentLinkedHashSet<String> set = new ConcurrentLinkedHashSet<>();
		for (int a = 0 ; a < 10 ; a++) {
			set.add(String.valueOf(a));
		}
		
		boolean sizeCheck = set.size() == 10;
		System.out.println("[testOperationsSingleThreaded] size check:"+sizeCheck);
		
		boolean iteratorTestSuccess = true;
		int a = 0;
		Iterator<String> setIter = set.iterator();
		while (setIter.hasNext()) {
			String elem = setIter.next();
			if (elem != String.valueOf(a)) {
				iteratorTestSuccess = false;
				break;
			}
			a++;
		}
		
		System.out.println("[testOperationsSingleThreaded] iter check:"+iteratorTestSuccess);
		
		set.remove(String.valueOf(0));
		
		boolean presenceCheck = set.contains(String.valueOf(0));
		System.out.println("[testOperationsSingleThreaded] presence check:"+presenceCheck);
		sizeCheck = set.size() == 10 - 1;
		System.out.println("[testOperationsSingleThreaded] size check:"+sizeCheck);
	}

	public static void main(String[] args) {
		ConcurrentLinkedHashSetTests tests = new ConcurrentLinkedHashSetTests();
		tests.testAdd();
		tests.testDelete();
	}
}