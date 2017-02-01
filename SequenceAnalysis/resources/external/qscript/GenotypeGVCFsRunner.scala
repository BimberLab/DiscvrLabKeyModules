

class GenotypeGVCFsRunner extends QScript {

  qscript =>

  // Required arguments.  All initialized to empty values.
  @Input(doc="The reference file.", shortName="R")
  var referenceFile: File = _ // _ is scala shorthand for null

  @Input(doc="One or more gVCF files.", shortName="V", fullName="variant")
  var vcfFiles: List[File] = Nil

  @Output(fullName = "output", shortName = "o", doc = "the output file")
  var output: File = _

  @Argument(fullName="scatterCount", shortName="scatterCount", doc="the number of concurrent jobs", required=false)
  var scatterCount: Int = 1

  @Argument(fullName="includeNonVariantSites", shortName="includeNonVariantSites", doc="If provided, all sites will be output, not just variable.", required=false)
  var includeNonVariantSites: Boolean = _

  @Argument(fullName="max_alternate_alleles", shortName="max_alternate_alleles", doc="Maximum number of alternate alleles to genotype", required=false)
  var maxAltAlleles: Int = 12

  @Argument(fullName="stand_call_conf", shortName="stand_call_conf", doc="The minimum phred-scaled confidence threshold at which variants should be called", required=false)
  var standCallConf: Int = 30

  @Argument(fullName="annotateNda", shortName="nda", doc="Annotate number of alleles observed", required=false)
  var annotateNDA: Boolean = true

  @Argument(fullName="annotation", shortName="A", doc="one or more annotations to apply", required=false)
  var annotation: List[String] = Nil

  def script(){
    val g = new GenotypeGVCFs
    g.R = this.referenceFile
    g.V = this.vcfFiles
    g.out = this.output
    g.scatterCount = this.scatterCount
    g.includeNonVariantSites = this.includeNonVariantSites
    g.max_alternate_alleles = this.maxAltAlleles
    g.stand_call_conf = this.standCallConf
    g.annotateNDA = this.annotateNDA
    g.annotation = this.annotation

    add(g)
  }
}