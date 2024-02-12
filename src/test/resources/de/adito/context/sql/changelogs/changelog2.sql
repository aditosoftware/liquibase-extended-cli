--liquibase formatted sql

--changeset your_author:3 context:sql-testing
alter table person add column gender varchar(2);