-- Database-per-service: each service owns its schema and migrates it with Flyway on start-up.
create database orders;
create database inventory;
create database payments;
create database notifications;
