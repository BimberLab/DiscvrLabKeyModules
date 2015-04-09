import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.gatk._
import org.broadinstitute.gatk.utils.variant.{GATKVCFIndexType, GATKVCFUtils}
import org.broadinstitute.gatk.tools.walkers.haplotypecaller.ReferenceConfidenceMode

class HaplotypeCaller extends QScript {

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
  @Argument(fullName="standard_min_confidence_threshold_for_calling", shortName="stand_call_conf", doc="the minimum phred-scaled Qscore threshold to separate high confidence from low confidence calls", required=false)
  var callConf: Double = -1

  @Argument(fullName="standard_min_confidence_threshold_for_emitting", shortName="stand_emit_conf", doc="the minimum phred-scaled Qscore threshold to emit low confidence calls", required=false)
  var emitConf: Double = -1

  @Argument(fullName="emitRefConfidence", shortName="ERC", doc="Mode for emitting reference confidence scores", required = false)
  var emitRefConfidence: ReferenceConfidenceMode = ReferenceConfidenceMode.NONE

  @Argument(fullName="dontUseSoftClippedBases", shortName="dontUseSoftClippedBases", doc="Do not analyze soft clipped bases in the reads", required = false)
  var dontUseSoftClippedBases: Boolean = false

  @Argument(fullName="variant_index_type",shortName = "variant_index_type",doc="Type of IndexCreator to use for VCF/BCF indices",required=false)
  var variant_index_type: GATKVCFIndexType = GATKVCFUtils.DEFAULT_INDEX_TYPE

  @Argument(fullName="variant_index_parameter",shortName = "variant_index_parameter",doc="Parameter to pass to the VCF/BCF IndexCreator",required=false)
  var variant_index_parameter: Int = GATKVCFUtils.DEFAULT_INDEX_PARAMETER

  def script(){
    val snps = new HaplotypeCaller
    snps.R = this.referenceFile
    snps.I = this.bamFiles
    snps.out = this.output
    snps.scatterCount = this.scatterCount

    //optional
    snps.stand_call_conf = this.callConf
    snps.stand_emit_conf = this.emitConf
    snps.emitRefConfidence = this.emitRefConfidence
    snps.dontUseSoftClippedBases = this.dontUseSoftClippedBases
    snps.variant_index_type = this.variant_index_type
    snps.variant_index_parameter = this.variant_index_parameter

    add(snps)
  }
}