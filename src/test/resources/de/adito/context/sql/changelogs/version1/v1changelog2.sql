--liquibase formatted sql

-- changeset your_author:4 context:sql-version1
alter table person add column first_name varchar(255);