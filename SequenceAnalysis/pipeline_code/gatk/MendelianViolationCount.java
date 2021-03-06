package org.broadinstitute.gatk.tools.walkers.annotator;



import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.Genotype;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFHeaderLine;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.broadinstitute.gatk.engine.GenomeAnalysisEngine;
import org.broadinstitute.gatk.engine.samples.Gender;
import org.broadinstitute.gatk.engine.samples.Sample;
import org.broadinstitute.gatk.engine.samples.SampleDB;
import org.broadinstitute.gatk.engine.walkers.Walker;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.AnnotatorCompatible;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.InfoFieldAnnotation;
import org.broadinstitute.gatk.tools.walkers.annotator.interfaces.RodRequiringAnnotation;
import org.broadinstitute.gatk.utils.Utils;
import org.broadinstitute.gatk.utils.collections.Pair;
import org.broadinstitute.gatk.utils.contexts.AlignmentContext;
import org.broadinstitute.gatk.utils.contexts.ReferenceContext;
import org.broadinstitute.gatk.utils.genotyper.PerReadAlleleLikelihoodMap;
import org.broadinstitute.gatk.utils.refdata.RefMetaDataTracker;

import java.util.*;

/**
 * Number of subjects with a Mendelian Violation
 *
 * <p>This annotation uses the likelihoods of the genotype calls to assess whether a site is transmitted from parents to offspring according to Mendelian rules. The output is the likelihood of the site being a Mendelian violation, which can be tentatively interpreted either as an indication of error (in the genotype calls) or as a possible <em><de novo/em> mutation. The higher the output value, the more likely there is to be a Mendelian violation. Note that only positive values indicating likely MVs will be annotated; if the value for a given site is negative (indicating that there is no violation) the annotation is not written to the file.</p>
 *
 * <h3>Caveats</h3>
 * <ul>
 *     <li>The calculation uses a hard cutoff of 10 for the phred scale genotype quality.  If a given sample is below this threshold, the genotype is assumed to be no call.</li>
 *     <li>This annotation requires a valid pedigree file.</li>
 * </ul>
 *
 *
 */

public class MendelianViolationCount extends InfoFieldAnnotation implements RodRequiringAnnotation {

    private final static Logger logger = LogManager.getLogger(MendelianViolationCount.class);
    public static final String MV_NUM = "MV_NUM";
    public static final String MV_SAMPLES = "MV_SAMPLES";
    private double minGenotypeQuality = 10.0;
    private SampleDB sampleDB = null;
    private int nonAutosomePositions = 0;

    private Boolean isVariantAnnotator = null;

    @Override
    public void initialize(AnnotatorCompatible walker, GenomeAnalysisEngine toolkit, Set<VCFHeaderLine> headerLines) {
        super.initialize(walker, toolkit, headerLines);

        if (((VariantAnnotator)walker).minGenotypeQualityP > 0.0)
        {
            minGenotypeQuality = ((VariantAnnotator)walker).minGenotypeQualityP;
        }
        logger.info("MendelianViolation minimum genotype quality: " + minGenotypeQuality);

        if ( sampleDB == null ) {
            sampleDB = ((Walker) walker).getSampleDB();
        }
    }

    public Map<String, Object> annotate(final RefMetaDataTracker tracker, final AnnotatorCompatible walker, final ReferenceContext ref, final Map<String, AlignmentContext> stratifiedContexts, final VariantContext vc, final Map<String, PerReadAlleleLikelihoodMap> stratifiedPerReadAlleleLikelihoodMap) {
        // Can only be called from VariantAnnotator
        isVariantAnnotator = logWalkerIdentityCheck(walker, isVariantAnnotator);
        if (!isVariantAnnotator){
            return null;
        }

        //not autosome
        if (vc.getContig().equalsIgnoreCase("chrUn")){
            nonAutosomePositions++;
            return null;
        }

        Map<String,Object> attributeMap = new HashMap<>();
        if (sampleDB != null) {
            int totalViolations = 0;
            Set<String> violations = new HashSet<>();
            for (String sn : sampleDB.getSampleNames()) {
                int count = countViolations(sampleDB.getSample(sn), vc, minGenotypeQuality);
                totalViolations += count;
                if (count > 0)
                {
                    violations.add(sn);
                }
            }

            attributeMap.put(MV_NUM, totalViolations);
            attributeMap.put(MV_SAMPLES, Utils.join(",", violations.toArray(new String[violations.size()])));
        }

        return attributeMap;
    }

    public static boolean logWalkerIdentityCheck(AnnotatorCompatible walker, Boolean isVariantAnnotator) {
        //check has already been performed
        if (isVariantAnnotator != null){
            return isVariantAnnotator;
        }

        if ( !(walker instanceof VariantAnnotator) ) {
            if ( walker != null )
                logger.warn("Annotation will not be calculated, must be called from VariantAnnotator, not " + walker.getClass().getName());
            else
                logger.warn("Annotation will not be calculated, must be called from VariantAnnotator");

            return false;
        }

        return true;
    }

    public static int countViolations(Sample subject, VariantContext vc, double minGenotypeQuality)
    {
        Genotype gChild = vc.getGenotype(subject.getID());
        if (gChild == null || !gChild.isCalled()){
            return 0;  //cant make call
        }

        //Count lowQual. Note that if min quality is set to 0, even values with no quality associated are returned
        if (minGenotypeQuality > -1 && gChild.getGQ() < minGenotypeQuality) {
            return 0; //cannot make determination
        }

        //until we have improved calling of sex chromosomes, skip this situation
        //chrY is overwhelmingly no-call for females, so we can proceed w/ the check here
        boolean isChrX = vc.getContig().equalsIgnoreCase("X") || vc.getContig().equalsIgnoreCase("chrX");
        if (isChrX && subject.getGender().equals(Gender.MALE)){
            return 0;
        }

        Genotype gMom = vc.getGenotype(subject.getMaternalID());
        if (gMom == null)
        {
            gMom = new NoCallGenotype(subject.getMaternalID());
        }
        Genotype gDad = vc.getGenotype(subject.getPaternalID());
        if (gDad == null)
        {
            gDad = new NoCallGenotype(subject.getPaternalID());
        }

        //If the family is all homref, not too interesting
        if (!(gMom.isHomRef() && gDad.isHomRef() && gChild.isHomRef()))
        {
            if (!gMom.isCalled() && !gDad.isCalled()) {
                return 0;
            }
            else if (isViolation(gMom, gDad, gChild, minGenotypeQuality)){
                return 1;
            }
        }

        return 0;
    }

    private static boolean isViolation(final Genotype gMom, final Genotype gDad, final Genotype gChild, double minGenotypeQuality) {
        //1 parent is no call
        if (!gMom.isCalled()){
            if (gDad.getGQ() < minGenotypeQuality) {
                return false;
            }

            return (gDad.isHomRef() && gChild.isHomVar()) || (gDad.isHomVar() && gChild.isHomRef()) || (!gDad.getAlleles().contains(gChild.getAllele(0)) && !gDad.getAlleles().contains(gChild.getAllele(1)));
        }
        else if (!gDad.isCalled()){
            if (gMom.getGQ() < minGenotypeQuality) {
                return false;
            }

            return (gMom.isHomRef() && gChild.isHomVar()) || (gMom.isHomVar() && gChild.isHomRef()) || (!gMom.getAlleles().contains(gChild.getAllele(0)) && !gMom.getAlleles().contains(gChild.getAllele(1)));
        }

        //Both parents have genotype information
        return !(gMom.getAlleles().contains(gChild.getAlleles().get(0)) && gDad.getAlleles().contains(gChild.getAlleles().get(1)) ||
                gMom.getAlleles().contains(gChild.getAlleles().get(1)) && gDad.getAlleles().contains(gChild.getAlleles().get(0)));
    }

    // return the descriptions used for the VCF INFO meta field
    public List<String> getKeyNames() { return Arrays.asList(MV_NUM, MV_SAMPLES); }

    public List<VCFInfoHeaderLine> getDescriptions() { return Arrays.asList(
            new VCFInfoHeaderLine(MV_NUM, 1, VCFHeaderLineType.Integer, "Number of mendelian violations across all samples."),
            new VCFInfoHeaderLine(MV_SAMPLES, 1, VCFHeaderLineType.String, "Samples where a mendelian violation was observed.")
    ); }

    public static class NoCallGenotype extends Genotype {
        private Genotype _orig = null;
        private List<Allele> _alleles = Arrays.asList(Allele.NO_CALL, Allele.NO_CALL);

        public NoCallGenotype(String sampleName)
        {
            super(sampleName, ".");
        }

        @Override
        public List<Allele> getAlleles()
        {
            return _alleles;
        }

        @Override
        public Allele getAllele(int i)
        {
            return _alleles.get(i);
        }

        @Override
        public boolean isPhased()
        {
            return false;
        }

        @Override
        public int getDP()
        {
            return _orig == null ? 0 : _orig.getDP();
        }

        @Override
        public int[] getAD()
        {
            return _orig == null ? new int[0] : _orig.getAD();
        }

        @Override
        public int getGQ()
        {
            return _orig == null ? 50 : _orig.getGQ();
        }

        @Override
        public int[] getPL()
        {
            return _orig == null ? new int[0] : _orig.getPL();
        }

        @Override
        public Map<String, Object> getExtendedAttributes()
        {
            return _orig == null ? null : _orig.getExtendedAttributes();
        }
    }
}
