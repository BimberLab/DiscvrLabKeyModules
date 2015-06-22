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

  // The following arguments are all optional.
  @Argument(fullName="dontUseSoftClippedBases", shortName="dontUseSoftClippedBases", doc="Do not analyze soft clipped bases in the reads", required = false)
  var dontUseSoftClippedBases: Boolean = false

  def script(){
    val snps = new HaplotypeCaller
    snps.R = this.referenceFile
    snps.I = this.bamFiles
    snps.out = this.output
    snps.scatterCount = this.scatterCount

    //optional
    snps.emitRefConfidence = ReferenceConfidenceMode.GVCF
    snps.dontUseSoftClippedBases = this.dontUseSoftClippedBases

    snps.variant_index_type = GATKVCFIndexType.LINEAR
    snps.variant_index_parameter = 128000

    add(snps)
  }
}