(ns migrations.20150217084636-user-site-roles
  (:require [api.db :as db :refer [$db-config]]
            [api.lib.crypto :as cr]
            [clojure.java.jdbc :as jdbc])
  (:import))

(defn up
  "Migrates the database up to version 20150217084636."
  []
  (let [[pw salt] (cr/encrypt-password "p|20M0t4|3l`/")]
    (println "migrations.20150217084636-user-site-roles up...")
    (jdbc/with-db-connection [db-con @$db-config]
      (jdbc/db-do-commands
       db-con
       (format "INSERT INTO users (username, email, password, password_salt) VALUES
             ('global@promotably.com', 'global@promotably.com', '%s', '%s')" pw salt)
       (jdbc/create-table-ddl :roles
                              [:role_id "serial8 primary key"]
                              [:role_name "text NOT NULL"])
       (jdbc/create-table-ddl :permissions
                              [:permission_id "serial8 primary key"]
                              [:permission_name "text NOT NULL"])
       (jdbc/create-table-ddl :roles_permissions
                              [:roles_id "INTEGER NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE"]
                              [:permissions_id "INTEGER NOT NULL REFERENCES permissions (permission_id) ON DELETE CASCADE"])
       (jdbc/create-table-ddl :user_site_role
                              [:users_id "INTEGER NOT NULL REFERENCES users (id) ON DELETE CASCADE"]
                              [:sites_id "INTEGER NOT NULL REFERENCES sites (id) ON DELETE CASCADE"]
                              [:roles_id "INTEGER NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE"])
       "ALTER TABLE user_site_role ADD CONSTRAINT uniqueusersiterole UNIQUE (users_id, sites_id, roles_id)"
       "INSERT INTO roles (role_name) VALUES
         ('su'), ('admin')"
       "INSERT INTO permissions (permission_name) VALUES
         ('read'), ('write'), ('update'), ('delete')"
       "INSERT INTO roles_permissions (roles_id, permissions_id) VALUES
         (1, 1), (1, 2), (1, 3), (1, 4), (2, 1), (2, 2), (2, 3), (2, 4)"
       "CREATE OR REPLACE FUNCTION add_su_site_permissions() RETURNS trigger AS $$
      BEGIN
        INSERT INTO user_site_role (users_id, sites_id, roles_id) VALUES
         ((SELECT id FROM users WHERE email = 'global@promotably.com' LIMIT 1),
         NEW.id, 1);
      RETURN NEW;
      END;
      $$ LANGUAGE plpgsql;"
       "CREATE TRIGGER new_site_trigger AFTER INSERT ON sites FOR EACH ROW
      EXECUTE PROCEDURE add_su_site_permissions();"
       "CREATE OR REPLACE FUNCTION add_su_to_account() RETURNS trigger AS $$
      BEGIN
        INSERT INTO users_accounts (users_id, accounts_id) VALUES
        ((SELECT id FROM users WHERE email = 'global@promotably.com' LIMIT 1),
        NEW.id);
      RETURN NEW;
      END;
      $$ LANGUAGE plpgsql;"
       "CREATE TRIGGER new_account_trigger AFTER INSERT ON accounts FOR EACH ROW
      EXECUTE PROCEDURE add_su_to_account();"))))

(defn down
  "Migrates the database down from version 20150217084636."
  []
  (println "migrations.20150217084636-user-site-roles down...")
    (jdbc/with-db-connection [db-con @$db-config]
    (jdbc/db-do-commands
     db-con
     "DROP FUNCTION IF EXISTS add_su_site_permissions() CASCADE"
     "DROP FUNCTION IF EXISTS add_su_to_account() CASCADE"
     "DROP TABLE roles CASCADE"
     "DROP TABLE permissions CASCADE"
     "DROP TABLE roles_permissions CASCADE"
     "DROP TABLE user_site_role CASCADE")))
