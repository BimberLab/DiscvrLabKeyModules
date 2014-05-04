CREATE TABLE sla.species (
    species varchar (200),

    datedisabled timestamp,
    createdby integer,
    created timestamp,
    modifiedby integer,
    modified timestamp,

    CONSTRAINT PK_species PRIMARY KEY (species)
);

INSERT INTO sla.species (species) values ('Rats');
INSERT INTO sla.species (species) values ('Hamsters');
INSERT INTO sla.species (species) values ('Guinea Pigs');
INSERT INTO sla.species (species) values ('Mice');
INSERT INTO sla.species (species) values ('Rabbits');
INSERT INTO sla.species (species) values ('Frogs');
INSERT INTO sla.species (species) values ('Birds');
INSERT INTO sla.species (species) values ('Fish');


CREATE TABLE sla.gender (
    gender varchar (200),

    datedisabled timestamp,
    createdby integer,
    created timestamp,
    modifiedby integer,
    modified timestamp,

    CONSTRAINT PK_gender PRIMARY KEY (gender)
);

INSERT INTO sla.gender (gender) values ('Male or Female');
INSERT INTO sla.gender (gender) values ('Female');
INSERT INTO sla.gender (gender) values ('Male');