
package org.broadinstitute.gatk.tools.walkers.annotator;


import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.AnnotatorCompatible;
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
 * Number of reads at that position that conflict with the subjects with a Mendelian Violation
 *
 * <p>.</p>
 *
 *
 *
 */

public class ConflictingReadCount extends InfoFieldAnnotation implements RodRequiringAnnotation {

    public static final String CR_KEY = "CR";
    public static final String CR_PCT_KEY = "CR_PCTS";

    public Map<String, Object> annotate(final RefMetaDataTracker tracker,
                                        final AnnotatorCompatible walker,
                                        final ReferenceContext ref,
                                        final Map<String, AlignmentContext> stratifiedContexts,
                                        final VariantContext vc,
                                        final Map<String, PerReadAlleleLikelihoodMap> stratifiedPerReadAlleleLikelihoodMap) {

        Map<String,Object> attributeMap = new HashMap<String,Object>(1);

        int totalMismatched = 0;
        List<String> pcts = new ArrayList<>();
        for (String sn : stratifiedContexts.keySet())
        {
            AlignmentContext ctx = stratifiedContexts.get(sn);
            int[] bases = ctx.getBasePileup().getMappingFilteredPileup(20).getBaseCounts();

            int totalForSample = 0;
            for (int i=0;i<bases.length;i++){
                totalForSample += bases[i];
            }

            Genotype g = vc.getGenotype(sn);
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
            if (totalMismatchedForSample > 0)
                pcts.add(String.format("%.4f", (double) totalMismatchedForSample / (double) totalForSample));
            else
                pcts.add("0");
        }

        attributeMap.put(CR_KEY, totalMismatched);
        if (!pcts.isEmpty())
            attributeMap.put(CR_PCT_KEY, Utils.join(",", pcts));

        return attributeMap;
    }

    // return the descriptions used for the VCF INFO meta field
    public List<String> getKeyNames() { return Arrays.asList(CR_KEY); }

    public List<VCFInfoHeaderLine> getDescriptions() { return Arrays.asList(
            new VCFInfoHeaderLine(CR_KEY, 1, VCFHeaderLineType.Integer, "Number of reads, across all samples, that do not match the genotype called for a given sample."),
            new VCFInfoHeaderLine(CR_PCT_KEY, 1, VCFHeaderLineType.String, "A list of the percent of reads that conflict with the called genotypes for each sample."));
    }
}
