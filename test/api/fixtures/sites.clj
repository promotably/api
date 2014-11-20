(table :sites
       (fixture :site-1
                :name "site name"))

(table :users
       (fixture :user-1
                :first-name "John"
                :last-name "Doe"
                :site-id :sites/site-1))


