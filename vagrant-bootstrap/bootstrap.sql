CREATE USER p_user WITH PASSWORD 'pr0m0';
CREATE DATABASE promotably_dev;
GRANT ALL PRIVILEGES ON DATABASE promotably_dev to p_user;
\c promotably_dev
CREATE EXTENSION "uuid-ossp";
CREATE TABLE migrations(version varchar(255));
GRANT ALL PRIVILEGES ON TABLE migrations TO p_user;
