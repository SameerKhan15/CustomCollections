package concurrent.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/* Author: Sameer Khan
 * 
 * Locking Scheme Design: 
 * Overall, any operations that require structural modification to the array are serialized.
 * Structural modifications are: 1) expanding array size, 2) shifting elements	
 * 
 * a) INSERTS: Interleaving operations not requiring array expansion are concurrent in nature.
 *   		   Array expansion is a serialized operation and will block any N-1 parallel INSERTS.
 *  
 * b) POP: Serialized operation, since it requires shifting elements.
 * c) GetNumberOfElements: Concurrent operation.
 * d) GET: Concurrent operation. 
 * e) SET: Concurrent operation.
 * f) Iterator: Concurrent operation.
 */
public class DynamicArray<T> {
	
	private volatile AtomicReference<T>[] array;
	private final int ARRAY_INITIAL_SIZE = 10;
	
	// Pointer to next available slot# for an insert 
	private AtomicInteger nextAvailableSlot = new AtomicInteger(0); 
	
	private final ReadWriteLock rwLock; 
	
	public DynamicArray() {
		array = initArray(ARRAY_INITIAL_SIZE);
		rwLock = new ReentrantReadWriteLock(true);
	}
	
	/* 
	 * Inserts an element to the next available slot in the array. 
	 * If array requires expansion, its done as part of the insert operation.
	 * N interleaving inserts not requiring array expansion are concurrent in nature.
	 */
	public void insert(T val) {
		// Shared lock to prevent array structural changes during an insert
		rwLock.readLock().lock();
		
		try {
			// No two interleaving inserts will have the same slot number.
			int currIndexSlot = nextAvailableSlot.getAndIncrement();
			
			/* 
			 * The array length check is on greater-or-equal_to condition because multiple threads can enter insert method 
			 *  and result in index slots being higher than existing array length.
			 * 
			 * All interleaving insert threads meeting this condition will enter #extendArray method. 
			 */
			if (currIndexSlot >= array.length) {
				extendArray(currIndexSlot);
			}
			
			array[currIndexSlot] = new AtomicReference<>(val);
		} finally {
			rwLock.readLock().unlock();
		}
	}
	
	/*
	 * Pops an element at the specified index location IF its a "valid" location.
	 * Valid location is defined as the slot where an element already exists.
	 * If the specified location is invalid, an exception is thrown.
	 * Pop is a serialized operation since it requires left-shifting elements.
	 */
	public void pop(int i) throws Exception {
		if (i < 0) {
			throw new Exception("Invalid index value:"+i);
		}
		
		rwLock.writeLock().lock();
		try {
			if (i > (getNumberOfElements() - 1)) {
				throw new Exception(String.format("Index location %d is greater than the last element slot", i));
			}
			
			for (int a = i+1 ; a < getNumberOfElements() ; a++) {
				array[a - 1] = array[a];
			}
			array[nextAvailableSlot.decrementAndGet()] = new AtomicReference<>();
		} finally {
			rwLock.writeLock().unlock();
		}
	}
	
	private AtomicReference<T>[] initArray(int size) {
		AtomicReference<T>[] newArray = new AtomicReference[size];
		for (int i = 0 ; i < size ; i++) {
			newArray[i] = new AtomicReference<>();
		}
		
		return newArray;
	}
	
	private void extendArray(int currIndexSlot) {
		/*
		 * The array extension is a serialized operation. Therefore the shared lock needs to be upgraded to exclusive lock.
		 * 
		 * Since there isn't (atomic) upgrade semantics available in java for this, we first release the shared lock and then acquire an exclusive lock.
		 *  This is safe from effects of any race conditions where the thread releasing the shared lock may not get the exclusive lock in the first attempt. 
		 * 
		 * The thread acquiring the exclusive lock checks the array condition again (while holding the exclusive lock) in order to examine 
		 *  whether an another thread have already expanded the array such that the allocation for this insert can be satisfied without further expansion.
		 * 
		 * For expansion, the thread (under exclusive lock) doubles the array from its allocated index slot number.
		 * 	It down-grades the lock to a shared state post expansion operation.
		 * 
		 * For non-expansion (where some other thread already expanded the array), 
		 *  the operation becomes a no-op and the thread simply down-grades the lock to a shared state.
		 */
		rwLock.readLock().unlock();
		rwLock.writeLock().lock();
		try {
			if (currIndexSlot >= array.length) {
				AtomicReference<T>[] newArray = initArray(currIndexSlot * 2);
				for (int i = 0 ; i < array.length ; i++) {
					newArray[i].set(array[i].get());
				}
				array = newArray;
			}
		} finally {
			rwLock.readLock().lock();
			rwLock.writeLock().unlock();
		}
	}
	
	/* 
	 * Returns the number of elements currently in the array. 
	 * This is a concurrent operation.
	 */
	public int getNumberOfElements() {
		return nextAvailableSlot.get();
	}
	
	/*
	 * Returns the element present at the specified location.
	 * If there isn't an element present at the specified location but the location is within the array's current length, it returns null.
	 * If the specified location is outside of the array's current length, an exception is thrown.
	 */
	public T get(int i) throws Exception {
		rwLock.readLock().lock();
		try {
			if (i >= array.length) {
				throw new Exception("Array out of bound at location: "+i);
			}
			return array[i].get();
		} finally {
			rwLock.readLock().unlock();
		}
	}
	
	/*
	 * Iterator takes an snapshot of the array as-of the time of invocation. 
	 * 	Snapshot occurs under a shared lock in order to prevent structural changes.
	 */
	public Iterator<T> iterator() {
		rwLock.readLock().lock();
		try {
			return new CustomIterator<>(array, nextAvailableSlot.get());
		} finally {
			rwLock.readLock().unlock();
		}
	}
	
	/*
	 * The set operation updates the specified slot with the supplied element. 
	 * Since this is an update operation, an element has to exists at the specified location. 
	 * 	Throws an exception if its not the case.
	 * 
	 * Set is a concurrent operation.
	 */
	public void set(int i, T val) throws Exception {
		try {
			rwLock.readLock().lock();
			
			if (i >= getNumberOfElements()) {
				throw new Exception("no element exists at loc: "+i);
			}
			
			array[i].set(val);
		} finally {
			rwLock.readLock().unlock();
		}
	}
	
	private class CustomIterator<T> implements Iterator<T> {
		private AtomicReference<T>[] iteratorArray;
		private int currentIndex = 0;
		
		private CustomIterator(AtomicReference<T>[] array, int indexPtr) {
			/* 
			 * Array and the next slot pointer could be in one of the two states 
			 *  -) indexPtr <= the array size. Which means we can just copy elements upto the indexPtr - 1. 
			 *  -) indexPtr > array size. This means there are to-be-inserted elements (currently in-flight) that require array expansion. 
			 *     In this case we simply copy the entire existing array. 
			 *     The expansion operation would be blocked on this operation because it would need exclusive lock. 
			 */
			iteratorArray = new AtomicReference[Math.min(array.length, indexPtr)];
			for (int i = 0 ; i < iteratorArray.length ; i++) {
				// We do this check to filter out any in-flight inserts within the existing sized array that are not committed yet. 
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