package concurrent.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DynamicArray<T> {
	
	private AtomicReference<T>[] array;
	private final int ARRAY_INITIAL_SIZE = 10;
	
	/* Pointer to next available slot# for an insert */
	private AtomicInteger nextAvailableSlot = new AtomicInteger(0); 
	
	/* This (reentrant) lock is used to synchronize on array-expansion operations. In order to constraint the complexity, I have coupled expansion 
	 * operation with next-slot get operations for inserts. 
	 * */
	private final ReentrantLock nextSlotGetLock;
	
	/* This read-write lock is used to prevent reads/updates/inserts conflict with array expansion operations. 
	 * The read/write operations are highly concurrent except when they conflict with array expansions, which is a serialized operation
	 * */
	private final ReadWriteLock rwLock; 
	
	public DynamicArray() {
		array = initArray(ARRAY_INITIAL_SIZE);
		nextSlotGetLock = new ReentrantLock();
		rwLock = new ReentrantReadWriteLock(true);
	}
	
	public void insert(T val) {
		/* First, get an (exclusive) lock. Get the current index slot#, which points to the next slot, for the to-be-inserted element
		 * The getAndIncrement increments the slot number *after* the get operation. If the incremented slot# exceeds the current array size, it implies we 
		 * need to expand the array. Outside of expansion operations, this lock duration is for the duration of AtomicInteger#getAndIncrement call 
		 * */
		nextSlotGetLock.lock();
		/* A key property to note is that no two inserts will have the same slot#, ever 
		 * */
		int currIndexSlot = nextAvailableSlot.getAndIncrement();
		
		/* Since getAndIncrement is invoked under an exclusive lock, we only need to check against the equality condition. The first thread hitting 
		 * this condition will trigger the expansion operation 
		 * */
		if (nextAvailableSlot.get() == array.length) {
			extendArray();
		}
		
		nextSlotGetLock.unlock();
		
		/* There is no race condition among interleaving writes in this locking model 
		 * E.g: Thread#1 gets slot#5. No expansion required. Releases the lock 
		 * Before Thread#1 acquires the shared lock, Thread#2 gets slot#6 but triggers expansion under writer lock
		 * Thread#2 writes to slot#6 in the new array. Thread#1 will acquire the lock in shared mode and writes to slot#5 in the (newly) expanded array. 
		 * Yes, for a brief moment, the array would have null at slot#5 and element at slot#6, which is OK and is not 
		 * a race condition. Note that for reads, (transient) null values are not returned to the caller 
		 * */
		rwLock.readLock().lock();
		array[currIndexSlot] = new AtomicReference<>(val);
		rwLock.readLock().unlock();
	}
	
	private AtomicReference<T>[] initArray(int size) {
		AtomicReference<T>[] newArray = new AtomicReference[size];
		for (int i = 0 ; i < size ; i++) {
			newArray[i] = new AtomicReference<>();
		}
		
		return newArray;
	}
	private void extendArray() {
		/* The expansion occurs under its own exclusive lock, modeled on read-write lock semantics. 
		 * We do not allow any interleaving operations on the array during expansion. 
		 * 
		 * This lock is separate from the one used in synchronizing access to slot getAndIncrement operations. The reasons for this design are the following:  
		 * 	*) Once the array is at its size limit and needs expansion, we do not want to have threads continue to get slot numbers. Because
		 *     this will complicate the expansion operation. E.g. ensuring the expanded array is >= the current max slot number that was there when we 
		 *     "committed" the expanded array. Therefore, we pause the threads for the expansion operation and its for this reason, this lock is separate from the 
		 *     reader-writer lock used to guard the actual expansion operation. 
		 *  *) The 2nd lock allows for concurrency among reads, updates and (non-expansion) writes. If we were to use a single lock for both the getAndIncrement 
		 *     and expansion operations, the inserts will synchronize with reads and updates because we would take the lock in exclusive mode for getAndIncrement 
		 *     which will conflict with shared locked read, iterator and update operations. 
		 * */
		rwLock.writeLock().lock();
		try {
			AtomicReference<T>[] newArray = initArray(array.length * 2);
			for (int i = 0 ; i < array.length ; i++) {
				newArray[i].set(array[i].get());
			}
			array = newArray;
		} finally {
			rwLock.writeLock().unlock();
		}
	}
	
	public int getNumberOfElements() {
		return nextAvailableSlot.get();
	}
	
	public T get(int i) throws Exception {
		rwLock.readLock().lock();
		try {
			if (i >= array.length) {
				return null;
			}
			return array[i].get();
		} finally {
			rwLock.readLock().unlock();
		}
	}
	
	public Iterator<T> iterator() {
		rwLock.readLock().lock();
		try {
			return new CustomIterator<>(array, nextAvailableSlot.get());
		} finally {
			rwLock.readLock().unlock();
		}
	}
	
	public void set(int i, T val) throws Exception {
		if (i >= array.length) {
			throw new Exception("out of bound");
		}
		
		rwLock.readLock().lock();
		array[i].set(val);
		rwLock.readLock().unlock();
	}
	
	private class CustomIterator<T> implements Iterator<T> {
		private AtomicReference<T>[] iteratorArray;
		private int currentIndex = 0;
		
		private CustomIterator(AtomicReference<T>[] array, int indexPtr) {
			/* Array and the next slot pointer could be in one of the two states 
			 *  *) indexPtr <= the array size. Which means we can just copy elements upto the indexPtr - 1. 
			 *  *) indexPtr > array size. This means there are to-be-inserted elements currently in-progress that require array expansion. In this case
			 *  we simply copy the entire existing array, since the expansion operation is blocked on the iterator obj creation operation 
			 *  */
			iteratorArray = new AtomicReference[Math.min(array.length, indexPtr)];
			for (int i = 0 ; i < iteratorArray.length ; i++) {
				/* We do this check to filter out any in-flight inserts within the existing sized array that are not committed yet */
				if (array[i].get() != null) {
					iteratorArray[i] = new AtomicReference<T>(array[i].get());
				}
			}
		}

		@Override
		public boolean hasNext() {
			if (currentIndex < iteratorArray.length && iteratorArray[currentIndex] != null) {
				return true;
			}
			return false;
		}

		@Override
		public T next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			return iteratorArray[currentIndex++].get();
		}		
	}
}