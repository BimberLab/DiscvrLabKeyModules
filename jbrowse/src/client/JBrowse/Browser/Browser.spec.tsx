import React from 'react';
import { mount } from 'enzyme';
import { mocked } from 'jest-mock';
import { describe, expect, jest, test } from '@jest/globals';

import { Ajax } from '@labkey/api';
import View from './Browser';

const mockData =
{
  "configuration": {},
  "assemblies":
  {
    "name": "hg38",
    "aliases": ["GRCh38"],
    "sequence": {
      "type": "ReferenceSequenceTrack",
      "trackId": "P6R5xbRqRr",
      "adapter": {
        "type": "BgzipFastaAdapter",
        "fastaLocation": {
          "uri": "https://jbrowse.org/genomes/GRCh38/fasta/hg38.prefix.fa.gz"
        },
        "faiLocation": {
          "uri": "https://jbrowse.org/genomes/GRCh38/fasta/hg38.prefix.fa.gz.fai"
        },
        "gziLocation": {
          "uri": "https://jbrowse.org/genomes/GRCh38/fasta/hg38.prefix.fa.gz.gzi"
        }
      }
    },
    "refNameAliases": {
      "adapter": {
        "type": "RefNameAliasAdapter",
        "location": {
          "uri": "https://s3.amazonaws.com/jbrowse.org/genomes/GRCh38/hg38_aliases.txt"
        }
      }
    }
  },
  "tracks": [
    {
      "type": "VariantTrack",
      "trackId": "clinvar_ncbi_hg38",
      "name": "ClinVar variants (NCBI)",
      "assemblyNames": ["hg38"],
      "category": ["Annotation"],
      "adapter": {
        "type": "VcfTabixAdapter",
        "vcfGzLocation": {
          "uri": "https://ftp.ncbi.nlm.nih.gov/pub/clinvar/vcf_GRCh38/clinvar.vcf.gz"
        },
        "index": {
          "location": {
            "uri": "https://ftp.ncbi.nlm.nih.gov/pub/clinvar/vcf_GRCh38/clinvar.vcf.gz.tbi"
          }
        }
      }
    },
    {
      "type": "FeatureTrack",
      "trackId": "ncbi_refseq_109_hg38_latest",
      "name": "NCBI RefSeq (GFF3Tabix)",
      "assemblyNames": ["hg38"],
      "category": ["Annotation"],
      "adapter": {
        "type": "Gff3TabixAdapter",
        "gffGzLocation": {
          "uri": "https://s3.amazonaws.com/jbrowse.org/genomes/GRCh38/ncbi_refseq/GRCh38_latest_genomic.sort.gff.gz"
        },
        "index": {
          "location": {
            "uri": "https://s3.amazonaws.com/jbrowse.org/genomes/GRCh38/ncbi_refseq/GRCh38_latest_genomic.sort.gff.gz.tbi"
          }
        }
      }
    }
  ],
  "connections": []
}

jest.mock('@labkey/api', () => {
    return {
        Ajax: {
            request: jest.fn()
        },
        ActionURL: {
            buildURL: jest.fn()
        }
    }
})
const mockedRequest = mocked(Ajax)

describe('JBrowse 2 Browser', () => {


    test('Renders error string if no config provided', async () => {

        const wrapper = mount(<View />);
        expect(wrapper.contains(<p>Error - no session provided.</p>)).toEqual(true);
    });


    test('Renders browser if config provided', async () => {
        jest.spyOn(URLSearchParams.prototype, "get").mockImplementation(() => "demo") // Set session to "demo" when queryParam.get is called.

        mockedRequest.request.mockReset();
        mockedRequest.request.mockImplementation(({ success }) => {
             success({ response: JSON.stringify(mockData) } as XMLHttpRequest, null);
             return {} as XMLHttpRequest;
         });
        const wrapper = mount(<View />);
        expect(wrapper.find('.MuiPaper-root')).toHaveLength(2)
    });

});
