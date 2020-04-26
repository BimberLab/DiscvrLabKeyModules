#!/bin/bash

set -e
set -x

BAM=$1
FASTA=$2
MASK_BED=$3
BCFTOOLS=${4:-bcftools}

VCF_CALLS=calls.vcf.gz
THREADS=1
if [ ! -z $SEQUENCEANALYSIS_MAX_THREADS ];then
    THREADS=$SEQUENCEANALYSIS_MAX_THREADS
fi

REPORT=`basename $BAM .bam`".consensus.report.txt"
truncate -s 0 $REPORT

OUT=`basename $BAM .bam`".consensus.fasta"
OUT_IUPAC=`basename $BAM .bam`".consensus.iupac.fasta"

# call variants
echo 'Calling variants'
$BCFTOOLS mpileup -Ou -d 20000 -f $FASTA $BAM | $BCFTOOLS call --ploidy 1 --threads $THREADS -mv -Oz -o $VCF_CALLS
$BCFTOOLS index $VCF_CALLS
COUNT1=`$BCFTOOLS view -H $VCF_CALLS | wc -l`
echo 'Variants called: '$COUNT1
echo -e 'VariantsCalled\t'$COUNT1 >> $REPORT

# normalize indels
echo 'Normalize indels'
VCF_NORM=calls.norm.bcf
$BCFTOOLS norm -f $FASTA --threads $THREADS -Ob -o $VCF_NORM $VCF_CALLS
$BCFTOOLS index -f $VCF_NORM
COUNT2=`$BCFTOOLS view -H $VCF_NORM | wc -l`
echo 'Variants remaining: '$COUNT2
echo -e 'VariantsAfterNorm\t'$COUNT2 >> $REPORT

# filter adjacent indels within 5bp
echo 'Filtering indel clusters.  Note: this is not currently used in the consensus.'
VCF_INDEL_FILTER=calls.norm.flt-indels.bcf
$BCFTOOLS filter --IndelGap 5 -Ob -o $VCF_INDEL_FILTER $VCF_NORM
$BCFTOOLS index -f $VCF_INDEL_FILTER
COUNT3=`$BCFTOOLS view -H $VCF_INDEL_FILTER | wc -l`
echo 'Variants that would remain: '$COUNT3
echo -e 'VariantsAfterIndelFilter\t'$COUNT3 >> $REPORT

#At the moment, do not user the filtered version:
VCF_FOR_CONSENSUS=$VCF_NORM
$BCFTOOLS consensus -f $FASTA -m $MASK_BED -o $OUT $VCF_FOR_CONSENSUS
$BCFTOOLS consensus -f $FASTA -m $MASK_BED -o $OUT_IUPAC --iupac-codes $VCF_FOR_CONSENSUS

rm $VCF_CALLS
rm ${VCF_CALLS}.csi

rm $VCF_NORM
rm ${VCF_NORM}.csi

rm $VCF_INDEL_FILTER
rm ${VCF_INDEL_FILTER}.csi