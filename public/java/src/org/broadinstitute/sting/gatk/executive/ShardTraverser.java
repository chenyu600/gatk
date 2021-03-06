package org.broadinstitute.sting.gatk.executive;

import org.apache.log4j.Logger;
import org.broadinstitute.sting.gatk.datasources.providers.LocusShardDataProvider;
import org.broadinstitute.sting.gatk.datasources.providers.ShardDataProvider;
import org.broadinstitute.sting.gatk.datasources.reads.Shard;
import org.broadinstitute.sting.gatk.io.ThreadLocalOutputTracker;
import org.broadinstitute.sting.gatk.traversals.TraversalEngine;
import org.broadinstitute.sting.gatk.walkers.Walker;
import org.broadinstitute.sting.utils.exceptions.ReviewedStingException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
/**
 * User: hanna
 * Date: Apr 29, 2009
 * Time: 4:40:38 PM
 * BROAD INSTITUTE SOFTWARE COPYRIGHT NOTICE AND AGREEMENT
 * Software and documentation are copyright 2005 by the Broad Institute.
 * All rights are reserved.
 *
 * Users acknowledge that this software is supplied without any warranty or support.
 * The Broad Institute is not responsible for its use, misuse, or
 * functionality.
 */
/**
 * Carries the walker over a given shard, in a callable interface.
 */
public class ShardTraverser implements Callable {
    final private HierarchicalMicroScheduler microScheduler;
    final private Walker walker;
    final private Shard shard;
    final private TraversalEngine traversalEngine;
    final private ThreadLocalOutputTracker outputTracker;
    private OutputMergeTask outputMergeTask;

    /** our log, which we want to capture anything from this class */
    final protected static Logger logger = Logger.getLogger(ShardTraverser.class);

    /**
     * Is this traversal complete?
     */
    private boolean complete = false;

    public ShardTraverser( HierarchicalMicroScheduler microScheduler,
                           TraversalEngine traversalEngine,
                           Walker walker,
                           Shard shard,
                           ThreadLocalOutputTracker outputTracker) {
        this.microScheduler = microScheduler;
        this.walker = walker;
        this.traversalEngine = traversalEngine;
        this.shard = shard;
        this.outputTracker = outputTracker;
    }

    public Object call() {
        try {
            traversalEngine.startTimersIfNecessary();
            final long startTime = System.currentTimeMillis();

            Object accumulator = walker.reduceInit();
            final WindowMaker windowMaker = new WindowMaker(shard,microScheduler.getEngine().getGenomeLocParser(),
                    microScheduler.getReadIterator(shard),
                    shard.getGenomeLocs(),
                    microScheduler.engine.getSampleDB().getSampleNames()); // todo: microScheduler.engine is protected - is it okay to user it here?

            for(WindowMaker.WindowMakerIterator iterator: windowMaker) {
                final ShardDataProvider dataProvider = new LocusShardDataProvider(shard,iterator.getSourceInfo(),microScheduler.getEngine().getGenomeLocParser(),iterator.getLocus(),iterator,microScheduler.reference,microScheduler.rods);
                accumulator = traversalEngine.traverse( walker, dataProvider, accumulator );
                dataProvider.close();
            }

            windowMaker.close();
            outputMergeTask = outputTracker.closeStorage();

            final long endTime = System.currentTimeMillis();

            microScheduler.reportShardTraverseTime(endTime-startTime);

            return accumulator;
        } catch(Throwable t) {
            // Notify that an exception has occurred and rethrow it.
            throw microScheduler.notifyOfTraversalError(t);
        } finally {
            synchronized(this) {
                complete = true;
                notifyAll();
            }
        }
    }

    /**
     * Has this traversal completed?
     * @return True if completed, false otherwise.
     */
    public boolean isComplete() {
        synchronized(this) {
            return complete;
        }
    }

   /**
     * Waits for any the given OutputMerger to be ready for merging.
     */
    public void waitForComplete() {
        try {
            synchronized(this) {
                if( isComplete() )
                    return;
                wait();
            }
        }
        catch( InterruptedException ex ) {
            throw new ReviewedStingException("Interrupted while waiting for more output to be finalized.",ex);
        }
    }

    /**
     * Gets the output merge task associated with the given shard.
     * @return OutputMergeTask if one exists; null if nothing needs to be merged.
     */
    public OutputMergeTask getOutputMergeTask() {
        return outputMergeTask;
    }
}
