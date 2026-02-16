package springbeansrepo;

import java.time.Instant;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import test.SkipListIterator;

public class ConcurrentHashSetv1<E> {
	
	private record InsertionOrder<E>(E value, long order) {}
	
	private final Map<E, InsertionOrder<E>> elements = new ConcurrentHashMap<>();
	private final Set<InsertionOrder<E>> orderedElements = 
			new ConcurrentSkipListSet<>(Comparator.comparingLong(InsertionOrder::order));
	private final AtomicLong orderNumber = new AtomicLong();
	
	public static void main(String[] args) {
		ConcurrentHashSetv1<String> setv1 = new ConcurrentHashSetv1<>();
		for (int a = 10 ; a > 0 ; a--) {
			setv1.add(String.valueOf(a), false);
		}
		
		Thread skipListreader = new Thread(() -> {
			for (int a = 0 ; a < 10 ; a++) {
				try {
					System.out.println("=====================================================");
					Iterator<String> setIter = setv1.iterator();
					while (setIter.hasNext()) {
						System.out.println(Instant.now()+" "+"skipListreader"+":"+setIter.next());
					}
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		});
		
		Thread mapReader = new Thread(() -> {
			for (int a = 0 ; a < 10 ; a++) {
				try {
					System.out.println(Instant.now()+" "+"mapReader content");
					setv1.dumpMapContent();
					Thread.sleep(5000);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
		});
		
		Thread writer = new Thread(() -> {
			for (int i = 100 ; i > 90 ; i--) {
				setv1.add(String.valueOf(i), true);
			}
		});
		
		skipListreader.start();
		mapReader.start();
		writer.start();
		try {
			skipListreader.join();
			mapReader.start();
			writer.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public boolean add(E e, boolean waitOnCommit) {
		var added = new AtomicBoolean(false);
		elements.computeIfAbsent(e, k -> {
			var holder = new InsertionOrder<>(e, orderNumber.getAndIncrement());
			orderedElements.add(holder);
			added.set(true);
			if (waitOnCommit) {
				try {
					System.out.println("**********************************");
					System.out.println(Instant.now()+" "+"writer waiting to commit value"+":"+e.toString());
					Thread.sleep(10000);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
			return holder;
		});
		
		System.out.println(Instant.now()+" "+"writer committed value"+":"+e.toString());
		return added.get();
	}
	
	public Stream<E> stream() {
		return orderedElements.stream().map(InsertionOrder::value);
	}
	
	
	public Iterator<E> iterator() {
		return stream().iterator();
	}
	
	public void dumpMapContent() {
		for (Map.Entry<E, InsertionOrder<E>> element : elements.entrySet()) {
			System.out.println(element.getKey());
		}
	}
}