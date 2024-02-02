--liquibase formatted sql

-- changeset your_author:2 context:sql-production,sql-special
alter table person add column initials varchar(2);
