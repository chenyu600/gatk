package org.broadinstitute.sting.oneoffprojects.walkers.association.modules.casecontrol;

import org.broadinstitute.sting.utils.pileup.PileupElement;
import org.broadinstitute.sting.utils.pileup.ReadBackedPileup;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: chartl
 * Date: 3/6/11
 * Time: 1:43 PM
 * To change this template use File | Settings | File Templates.
 */
public class MateMappingQuality extends ValueTest {

    public Collection<Number> map(ReadBackedPileup rbp) {
        ArrayList<Integer> mateMapQ = new ArrayList<Integer>(rbp.size());
        for ( PileupElement e : rbp ) {
            if ( e.getRead().getReadPairedFlag() && e.getRead().getAttribute("MQ") != null) {
                mateMapQ.add((Integer) e.getRead().getAttribute("MQ"));
            }
        }

        return (Collection) mateMapQ;
    }

    public boolean usePreviouslySeenReads() { return false; }
}
