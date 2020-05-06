#!/bin/bash

set -e
set -x

BAM=$1
FASTA=$2
MASK_BED=$3
MIN_DEPTH=$4
BCFTOOLS=${5:-bcftools}

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
$BCFTOOLS mpileup -Ou -d 20000 -f $FASTA $BAM | $BCFTOOLS call -c --ploidy 1 --threads $THREADS -v -Oz -o $VCF_CALLS
$BCFTOOLS index -t $VCF_CALLS
COUNT1=`$BCFTOOLS view -H $VCF_CALLS | wc -l`
echo 'Variants called: '$COUNT1
echo -e 'VariantsCalled\t'$COUNT1 >> $REPORT

# normalize indels
echo 'Normalize indels'
VCF_NORM=calls.norm.vcf.gz
$BCFTOOLS norm -f $FASTA --threads $THREADS -Oz -o $VCF_NORM $VCF_CALLS
$BCFTOOLS index -t -f $VCF_NORM
COUNT2=`$BCFTOOLS view -H $VCF_NORM | wc -l`
echo 'Variants remaining: '$COUNT2
echo -e 'VariantsAfterNorm\t'$COUNT2 >> $REPORT

# filter on depth
echo 'Filtering indel clusters.  Note: this is not currently used in the consensus.'
VCF_DEPTH_FILTER=calls.norm.filter.vcf.gz
$BCFTOOLS filter -e "DP < $MIN_DEPTH" -Oz -o $VCF_DEPTH_FILTER $VCF_NORM
$BCFTOOLS index -t -f $VCF_DEPTH_FILTER
COUNT3=`$BCFTOOLS view -H $VCF_DEPTH_FILTER | wc -l`
echo 'Variants remaining: '$COUNT3
echo -e 'VariantsAfterDpethFilter\t'$COUNT3 >> $REPORT

# filter adjacent indels within 5bp
echo 'Filtering indel clusters.  Note: this is not currently used in the consensus.'
VCF_INDEL_FILTER=calls.norm.flt-indels.vcf.gz
$BCFTOOLS filter --IndelGap 5 -Oz -o $VCF_INDEL_FILTER $VCF_DEPTH_FILTER
$BCFTOOLS index -t -f $VCF_INDEL_FILTER
COUNT3=`$BCFTOOLS view -H $VCF_INDEL_FILTER | wc -l`
echo 'Variants that would remain: '$COUNT3
echo -e 'VariantsAfterIndelFilter\t'$COUNT3 >> $REPORT

#At the moment, do not use the indel-filtered version:
VCF_FOR_CONSENSUS=`basename $BAM .bam`".calls.vcf.gz"
mv $VCF_DEPTH_FILTER $VCF_FOR_CONSENSUS
mv ${VCF_DEPTH_FILTER}.tbi ${VCF_FOR_CONSENSUS}.tbi

$BCFTOOLS consensus -f $FASTA -m $MASK_BED -o $OUT $VCF_FOR_CONSENSUS
$BCFTOOLS consensus -f $FASTA -m $MASK_BED -o $OUT_IUPAC --iupac-codes $VCF_FOR_CONSENSUS

rm $VCF_CALLS
rm ${VCF_CALLS}.tbi

rm $VCF_NORM
rm ${VCF_NORM}.tbi

rm $VCF_INDEL_FILTER
rm ${VCF_INDEL_FILTER}.tbi