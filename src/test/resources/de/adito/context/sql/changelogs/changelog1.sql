--liquibase formatted sql

--changeset your_author:1 context:sql-production
alter table person add column telephone varchar(255);

-- changeset your_author:2 context:sql-development
alter table person add column birth_year number;
