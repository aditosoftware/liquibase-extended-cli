--liquibase formatted sql
			
--changeset Liquibase User:1
--preconditions onFail:WARN 
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM example_table
create table example_table (  
	id int primary key,
	firstname varchar(50),
	lastname varchar(50) not null,
	state char(2)
)