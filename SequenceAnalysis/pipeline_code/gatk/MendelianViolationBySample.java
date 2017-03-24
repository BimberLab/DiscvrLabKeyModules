package org.broadinstitute.gatk.tools.walkers.annotator;

import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.GenotypeBuilder;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFormatHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import org.apache.log4j.Logger;
import org.broadinstitute.gatk.engine.samples.SampleDB;
import org.broadinstitute.gatk.engine.walkers.Walker;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.AnnotatorCompatible;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.GenotypeAnnotation;
import org.broadinstitute.gatk.utils.contexts.AlignmentContext;
import org.broadinstitute.gatk.utils.contexts.ReferenceContext;
import org.broadinstitute.gatk.utils.genotyper.PerReadAlleleLikelihoodMap;
import org.broadinstitute.gatk.utils.refdata.RefMetaDataTracker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by bimber on 3/13/2017.
 */
public class MendelianViolationBySample extends GenotypeAnnotation {
    private final static Logger logger = Logger.getLogger(MendelianViolationBySample.class);
    private double minGenotypeQuality = 10.0;
    private SampleDB sampleDB = null;
    private boolean walkerIdentityCheckWarningLogged = false;

    public static final String MV_KEY = "MV";

    @Override
    public List<String> getKeyNames() {
        return null;
    }

    @Override
    public void annotate(RefMetaDataTracker tracker, AnnotatorCompatible walker, ReferenceContext ref, AlignmentContext stratifiedContext, VariantContext vc, Genotype g, GenotypeBuilder gb, PerReadAlleleLikelihoodMap alleleLikelihoodMap) {
        // Can only be called from VariantAnnotator
        walkerIdentityCheckWarningLogged = MendelianViolationCount.logWalkerIdentityCheck(walker, walkerIdentityCheckWarningLogged);
        if (walkerIdentityCheckWarningLogged){
            return;
        }

        if (((VariantAnnotator)walker).minGenotypeQualityP > 0) {
            minGenotypeQuality = ((VariantAnnotator)walker).minGenotypeQualityP;
        }

        if ( sampleDB == null ) {
            sampleDB = ((Walker) walker).getSampleDB();
        }

        if (sampleDB != null) {
            int totalViolations = MendelianViolationCount.countViolations(sampleDB.getSample(g.getSampleName()), vc, minGenotypeQuality);
            gb.attribute(MV_KEY, totalViolations);
            //if (totalViolations > 0){
            //    gb.filter("");
            //}
        }
    }

    @Override
    public List<VCFFormatHeaderLine> getDescriptions() {
        return Arrays.asList(new VCFFormatHeaderLine(MV_KEY, 1, VCFHeaderLineType.Integer, "Number of mendelian violations observed for this sample."));
    }
}
