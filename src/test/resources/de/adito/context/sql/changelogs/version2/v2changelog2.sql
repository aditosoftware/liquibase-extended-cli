--liquibase formatted sql

-- changeset your_author:8 context:sql-version2,sql-production
alter table person add column middle_name varchar(255);