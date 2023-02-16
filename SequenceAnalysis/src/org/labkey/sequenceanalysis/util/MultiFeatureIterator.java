package org.labkey.sequenceanalysis.util;

import htsjdk.samtools.util.PeekableIterator;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureReader;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.iterator.CloseableIterator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;

/**
 * Created by bimber on 3/14/2017.
 *
 * Patterned heavily after HTSJDK MergingSamRecordIterator
 */
public class MultiFeatureIterator<TYPE extends Feature> implements AutoCloseable, CloseableIterator<TYPE>
{
    private final PriorityQueue<ComparableFeatureIterator<TYPE>> _queue;
    private final Collection<Iterator<TYPE>> _iterators = new ArrayList<>();

    private final Comparator<TYPE> _comparator;

    private boolean _initialized = false;

    public MultiFeatureIterator(Collection<FeatureReader<TYPE>> readers, Comparator<TYPE> comparator) throws IOException
    {
        _queue = new PriorityQueue<>(readers.size());
        _comparator = comparator;

        for (FeatureReader<TYPE> reader : readers)
        {
            _queue.add(new ComparableFeatureIterator<TYPE>(reader.iterator(), comparator));
        }
    }

    @Override
    public boolean hasNext()
    {
        startIterationIfRequired();
        return !_queue.isEmpty();
    }

    private void startIterationIfRequired()
    {
        if (_initialized)
            return;

        for (final Iterator<TYPE> iterator : _iterators)
        {
            addIfNotEmpty(new ComparableFeatureIterator<TYPE>(iterator, _comparator));
        }

        _initialized = true;
    }

    @Override
    public TYPE next()
    {
        startIterationIfRequired();

        final ComparableFeatureIterator<TYPE> iterator = _queue.poll();
        final TYPE record = iterator.next();
        addIfNotEmpty(iterator);

        return record;
    }

    private void addIfNotEmpty(ComparableFeatureIterator<TYPE> iterator)
    {
        if (iterator.hasNext())
        {
            _queue.offer(iterator);
        }
        else
        {
            iterator.close();
        }
    }

    @Override
    public void close()
    {
        for (ComparableFeatureIterator<TYPE> f : _queue)
        {
            try
            {
                f.close();
            }
            catch (Throwable e)
            {
                //ignore
            }
        }
    }

    private static class ComparableFeatureIterator<TYPE extends Feature> extends PeekableIterator<TYPE> implements Comparable<ComparableFeatureIterator<TYPE>>
    {
        private final Comparator<TYPE> _comparator;

        public ComparableFeatureIterator(Iterator<TYPE> iterator, Comparator<TYPE> comparator)
        {
            super(iterator);
            _comparator = comparator;
        }

        @Override
        public int compareTo(@NotNull ComparableFeatureIterator<TYPE> o)
        {
            if (_comparator.getClass() != o._comparator.getClass())
            {
                throw new IllegalStateException("Attempt to compare two ComparableFeatureIterator that have different classes");
            }
            else
            {
                TYPE record = this.peek();
                TYPE record2 = o.peek();
                return _comparator.compare(record, record2);
            }
        }
    }

    public static class PositionFeatureComparator<TYPE extends Feature> implements Comparator<TYPE>
    {
        @Override
        public int compare(TYPE o1, TYPE o2)
        {
            int ret = o1.getContig().compareTo(o2.getContig());
            if (ret != 0)
                return ret;

            ret = compareInts(o1.getStart(), o2.getStart());
            if (ret != 0)
                return ret;

            ret = compareInts(o1.getEnd(), o2.getEnd());
            return ret;
        }

        private int compareInts(int i1, int i2)
        {
            return i1 < i2?-1:(i1 > i2?1:0);
        }
    }
}
