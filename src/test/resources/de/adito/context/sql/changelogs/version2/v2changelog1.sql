--liquibase formatted sql

-- changeset your_author:7 context:sql-special_version2
alter table person add column last_name varchar(255)