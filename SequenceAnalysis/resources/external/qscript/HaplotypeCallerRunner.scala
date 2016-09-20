import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.gatk._
import org.broadinstitute.gatk.engine.GATKVCFUtils
import org.broadinstitute.gatk.utils.variant.GATKVCFIndexType
import org.broadinstitute.gatk.tools.walkers.haplotypecaller.ReferenceConfidenceMode

class HaplotypeCallerRunner extends QScript {

  qscript =>

  // Required arguments.  All initialized to empty values.
  @Input(doc="The reference file for the bam files.", shortName="R")
  var referenceFile: File = _ // _ is scala shorthand for null

  @Input(doc="One or more bam files.", shortName="I")
  var bamFiles: List[File] = Nil

  @Output(fullName = "output", shortName = "o", doc = "the output file")
  var output: File = _

  @Argument(fullName="scatterCount", shortName="scatterCount", doc="the number of concurrent jobs", required=false)
  var scatterCount: Int = 1

  @Argument(fullName="max_alternate_alleles", shortName="max_alternate_alleles", doc="Maximum number of alternate alleles to genotype", required=false)
  var maxAltAlleles: Int = 6

  @Argument(fullName="annotation", shortName="A", doc="one or more annotations to apply", required=false)
  var annotation: List[String] = Nil

  // The following arguments are all optional.
  @Argument(fullName="dontUseSoftClippedBases", shortName="dontUseSoftClippedBases", doc="Do not analyze soft clipped bases in the reads", required = false)
  var dontUseSoftClippedBases: Boolean = false

  def script(){
    val hc = new HaplotypeCaller
    hc.R = this.referenceFile
    hc.I = this.bamFiles
    hc.out = this.output
    hc.annotation = this.annotation
    hc.scatterCount = this.scatterCount
    hc.maxAltAlleles = this.maxAltAlleles

    //optional
    hc.emitRefConfidence = ReferenceConfidenceMode.GVCF
    hc.dontUseSoftClippedBases = this.dontUseSoftClippedBases

    add(hc)
  }
}