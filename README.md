# API

The Promotably API Server

## Usage

### Cider

Make sure your ~/.lein/profiles.clj has at least:

     $ cat ~/.lein/profiles.clj
     {:user {:plugins [[lein-midje "3.0.0"]
                      [cider/cider-nrepl "0.7.0-SNAPSHOT"]]}}

### Database

* This server uses PostgreSQL 9.3. If you're looking for a really easy way to run a postgresql database on your mac, checkout [postgres.app](http://postgresapp.com/)
* Also, the postgresql extension uuid-ossp is required. Once you have your database up, do the following setup:
```
CREATE USER p_user WITH PASSWORD 'pr0m0';
CREATE DATABASE promotably_dev;
GRANT ALL PRIVILEGES ON DATABASE promotably_dev to p_user;
\c promotably_dev
CREATE EXTENSION "uuid-ossp";
CREATE TABLE migrations(version varchar(255));
GRANT ALL PRIVILEGES ON TABLE migrations TO p_user;
```

### Migrations

API uses [drift for migrations](https://github.com/macourtney/drift).

#### To run migrations:
```
lein migrate
```

#### To generate a new migration file:
```
lein create-migration <the name of the migration>
```

This places a migration file in the src/migrations directory

### Running the Server
```
lein ring server
```

## License

Copyright © 2014 Promotably
