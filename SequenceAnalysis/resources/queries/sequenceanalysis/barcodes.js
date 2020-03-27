var COMPLEMENT_BASES = {'A': 'T', 'T': 'A', 'G': 'C', 'C': 'G'};

function beforeUpsert(row, errors) {
    if (row.sequence){
        row.sequence = row.sequence.toUpperCase();

        var reverseSeq = row.sequence.split('').reverse().join('');
        row.reverse_complement = reverseSeq.replace(/[ATCG]/g, function($0){
            return COMPLEMENT_BASES[$0]
        });
    }
}

function beforeInsert(row, errors) {
    beforeUpsert(row, errors);
}

function beforeUpdate(row, oldRow, errors) {
    beforeUpsert(row, errors);
}