package concurrent.collections;

import java.util.AbstractSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicMarkableReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentLinkedHashSet<E> extends AbstractSet<E> {
	/* Tuple containing the value and insertion order stamp */
	private record RecordTuple<E>(AtomicMarkableReference<E> value, long insertionOrderStamp) {}
	private final AtomicLong insetionOrder = new AtomicLong();
	private final Set<RecordTuple<E>> orderedElements = 
			new ConcurrentSkipListSet<>(Comparator.comparingLong(RecordTuple::insertionOrderStamp));
	private final Map<E, RecordTuple<E>> elements = new ConcurrentHashMap<>();
	private final ReadWriteLock rwLock = new ReentrantReadWriteLock(true);

	public static void main(String[] args) {
		ConcurrentLinkedHashSet<String> set = new ConcurrentLinkedHashSet<>();
		for (int a = 0 ; a < 10 ; a++) {
			set.add(String.valueOf(a));
		}
		
		Iterator<String> setIter = set.iterator();
		while (setIter.hasNext()) {
			System.out.print(setIter.next()+" ");
		}
		
		System.out.println("removing 6 "+set.remove("6"));
		System.out.println("removing 6 "+set.remove("6"));
		System.out.println("contains 6 "+set.contains("6"));
		System.out.println("size "+set.size());
		
		setIter = set.iterator();
		while (setIter.hasNext()) {
			System.out.print(setIter.next()+" ");
		}
		
		System.out.println();
		String[] EMPTY_STRING_ARRAY = {};
		String[] arr = set.toArray(EMPTY_STRING_ARRAY);
		System.out.println("arr length:"+arr.length);
		for (int i = 0 ; i < arr.length ; i++) {
			System.out.print(arr[i]+" ");
		}
 	}

	@Override
	public int size() {
		return elements.size();
	}

	@Override
	public Iterator<E> iterator() {
		return new CustomIterator<>(orderedElements);
	}
	
	@Override
	public boolean contains(Object e) {
		RecordTuple<E> element = elements.get(e);
		if (element != null && !element.value.isMarked()) {
			return true;
		}
		return false;
	}
	
	@Override
	public boolean add(E e) {
		try {
			rwLock.readLock().lock();
			elements.computeIfAbsent(e, k -> {
				var holder = new RecordTuple<>(new AtomicMarkableReference<>(e, true), insetionOrder.getAndIncrement());
				orderedElements.add(holder);
				return holder;
			});
			
			return elements.get(e).value.attemptMark(e, false); //linearization point
		} finally {
			rwLock.readLock().unlock();
		}
	}
	
	@Override
	public boolean remove(Object e) {
		try {
			rwLock.writeLock().lock();
			if (!contains(e)) {
				return false;
			}
			
			boolean success = false;
			
			RecordTuple<E> tuple = elements.get(e);
			if (tuple != null) {
				success = tuple.value.attemptMark(tuple.value.getReference(), true);
				if (success) {
					elements.remove(e);
					orderedElements.remove(tuple);
				}
			}
			return success;
		} finally {
			rwLock.writeLock().unlock();
		}
	}
	
	private class CustomIterator<E> implements Iterator<E> {
		private RecordTuple<E>[] tuplesArray;
		private int currentIndex = 0;
		
		public CustomIterator(Set<RecordTuple<E>> orderedElements) {
			Object[] obj = orderedElements.toArray();
			tuplesArray = new RecordTuple[obj.length];
			
			for (int a = 0 ; a < obj.length ; a++) {
				tuplesArray[a] = (RecordTuple<E>) obj[a];
			}
		}

		@Override
		public boolean hasNext() {
			while (currentIndex < tuplesArray.length) {
				RecordTuple<E> element = tuplesArray[currentIndex];
				if (!element.value.isMarked()) {
					return true;
				}
				currentIndex++;
			}			
			return false;
		}

		@Override
		public E next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			return tuplesArray[currentIndex++].value.getReference();
		}
	}
}