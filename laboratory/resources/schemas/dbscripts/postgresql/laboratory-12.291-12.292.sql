alter table laboratory.samples rename column quantity to quantity_string;
ALTER TABLE laboratory.samples ADD quantity double precision;
