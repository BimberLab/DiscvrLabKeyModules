import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.gatk._

class IndelRealignerRunner extends QScript {

  qscript =>

  // Required arguments.  All initialized to empty values.
  @Input(doc="The reference file for the bam files.", shortName="R")
  var referenceFile: File = _ // _ is scala shorthand for null

  @Input(doc="One or more bam files.", shortName="I")
  var bamFiles: List[File] = Nil

  @Output(fullName = "output", shortName = "o", doc = "the output file")
  var output: File = _

  @Output(fullName = "targetIntervals", shortName = "targetIntervals", doc = "the intervals file output from RealignerTargetCreator")
  var targetIntervals: File = _

  @Argument(fullName="scatterCount", shortName="scatterCount", doc="the number of concurrent jobs", required=false)
  var scatterCount: Int = 1

  def script(){
    val realigner = new IndelRealigner
    realigner.R = this.referenceFile
    realigner.I = this.bamFiles
    realigner.out = this.output
    realigner.targetIntervals = this.targetIntervals
    realigner.scatterCount = this.scatterCount

    add(realigner)
  }
}