# lacinia2datomic

A Clojure library designed to convert lacinia schema to datomic schema.

## Usage

``` clojure
(require '[lacinia2datomic.core :refer [lacinia->datomic]])

(def lacinia-schema {:enums {:role {:values ["ADMIN" "NORMAL"]}}
                     :objects {:user {:fields {:email {:type 'String}
                                               :name {:type 'String}
                                               :role {:type :role}}}}})

(clojure.pprint/pprint
 (lacinia->datomic lacinia-schema
                   {:exclude [:tokens]
                    :index {:user #{:name :role :email :phone :createdAt :updatedAt}}
                    :unique {:user #{:email :phone :facebookUserId}}}))


```

## License

Copyright © 2017 Tienson Qin

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
