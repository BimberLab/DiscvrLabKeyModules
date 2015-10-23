
package org.broadinstitute.gatk.tools.walkers.annotator;


import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.AnnotatorCompatible;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.GenotypeAnnotation;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.InfoFieldAnnotation;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.RodRequiringAnnotation;
import org.broadinstitute.gatk.utils.BaseUtils;
import org.broadinstitute.gatk.utils.Utils;
import org.broadinstitute.gatk.utils.contexts.AlignmentContext;
import org.broadinstitute.gatk.utils.contexts.ReferenceContext;
import org.broadinstitute.gatk.utils.genotyper.PerReadAlleleLikelihoodMap;
import org.broadinstitute.gatk.utils.refdata.RefMetaDataTracker;

import java.util.*;

/**
 * Number of reads at that position that conflict with any of the genotype calls
 *
 * <p>.</p>
 *
 *
 *
 */

public class ConflictingReadCountBySample extends GenotypeAnnotation implements RodRequiringAnnotation {

    public static final String CR_KEY = "GCR";
    public static final String CR_PCT_KEY = "GCR_PCT";

    @Override
    public void annotate(final RefMetaDataTracker tracker,
                                  final AnnotatorCompatible walker,
                                  final ReferenceContext ref,
                                  final AlignmentContext stratifiedContext,
                                  final VariantContext vc,
                                  final Genotype g,
                                  final GenotypeBuilder gb,
                                  final PerReadAlleleLikelihoodMap alleleLikelihoodMap){

        if (g.isNoCall())
            return;

        int totalMismatched = 0;
        int[] bases = stratifiedContext.getBasePileup().getMappingFilteredPileup(20).getBaseCounts();

        int totalForSample = 0;
        for (int i=0;i<bases.length;i++){
            totalForSample += bases[i];
        }

        for (Allele a : g.getAlleles()){
            if (a.isCalled() && a.length() == 1 && !a.isSymbolic()){
                bases[BaseUtils.simpleBaseToBaseIndex(a.getBases()[0])] = 0;
            }
        }

        int totalMismatchedForSample = 0;
        for (int i=0;i<bases.length;i++){
            totalMismatchedForSample += bases[i];
        }
        totalMismatched += totalMismatchedForSample;
        String pct;
        if (totalMismatchedForSample > 0)
            pct = String.format("%.4f", (double) totalMismatchedForSample / (double) totalForSample);
        else
            pct = "0";


        gb.attribute(CR_KEY, totalMismatched);
        gb.attribute(CR_PCT_KEY, pct);
    }

    // return the descriptions used for the VCF INFO meta field
    public List<String> getKeyNames() { return Arrays.asList(CR_KEY); }

    public List<VCFFormatHeaderLine> getDescriptions() { return Arrays.asList(
            new VCFFormatHeaderLine(CR_KEY, 1, VCFHeaderLineType.Integer, "Number of reads, across all samples, that do not match the genotype called for a given sample."),
            new VCFFormatHeaderLine(CR_PCT_KEY, 1, VCFHeaderLineType.String, "A list of the percent of reads that conflict with the called genotypes for each sample."));
    }
}
