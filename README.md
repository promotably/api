# API

The Promotably API Server

## Usage

### Cider

Make sure your ~/.lein/profiles.clj has at least:

     $ cat ~/.lein/profiles.clj
     {:user {:plugins [[lein-midje "3.0.0"]
                      [cider/cider-nrepl "0.8.0"]]}}

### Database

NOTE: If you are using the devbox/schmetterling repo for a development VM, you don't need to do this.

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

### AWS Credentials

Before you can run the server or the integration tests you need AWS credentials.
Once you have an id and key you can save the credentials to `~/.aws/credentials`:

```
[promotably]
aws_access_key_id = <ID>
aws_secret_access_key = <KEY>
```

### Running the Server
```
lein run
```

### Testing

Unit testing:  

```
ENV=test lein midje "api.unit.*"
```

Integration testing:  

```
KINESIS_A=dev-PromotablyAPIEvents KINESIS_B=dev-PromoStream RDS_HOST=localhost RDS_PORT=5432 RDS_USER=p_user RDS_DB_NAME=promotably_dev RDS_PW=pr0m0 REDIS_HOST=localhost REDIS_PORT=6379 ENV=integration lein midje "api.integration.*"
```

Integration testing depends on AWS credentials as per [doc](https://github.com/mcohen01/amazonica).

### Development

Use the usual in emacs: `cider-jack-in`.  Open core.clj and load the file in cider.  Then eval the desired lines at the bottom of the file:

```
  (System/setProperty "ENV" "dev")
  (System/setProperty "ENV" "localdev")
  (System/getProperty "ENV")

  (prn api.system/current-system)
  (go {:port 3000 :repl-port 55555})
  (stop)
```

## Request Signing

Authenticated requests to the Promotably API include a cryptographic
signature to allow the API to validate requests.  The reqestor
includes an HTTP header ('Promotably-Auth') that has
been computed as follows.

First, the requestor computes a cryptographic hash (an HMAC) of the
request body using the SHA1 algorithm.

    body-hash = HEXDIGEST(HMAC(request body))

The requestor then computes a string of header lines to be included in
the signature string.  The included header lines are sorted
alphabetically, and put in Header-Name: Header-Value + newline ("\n")
form, and concatenated together.  For example, if the requestor wants
to include the User-Agent header and the Accept header, the string
might be:

   Accept: */*\nUser-Agent: curl/7.10.6 (i386-redhat-linux-gnu) libcurl/7.10.6 OpenSSL/0.9.7a ipv6 zlib/1.1.4\n

Next, the requestor computes a string of query string fields to be
included in the signature string.  The included query string field
names are sorted alphabetically, and put in field-name=field-value
+ ampersand ("&") form, and concatenated together.  For example, if
the requestor's URL looked like:

    http://api.promotably.com/v1/track?site-id=26b28c70-2144-4427-aee3-b51031b08426&event-name=_trackProductView&product-id=WW1

Then, the string might be:

   event-name=_trackProductView&product-id=WW1&site-id=26b28c70-2144-4427-aee3-b51031b08426

Note that the requestor need not specify the totality of all query
string fields available in the URL; nor any at all, for that matter.

Then the requestor computes a signature string by concatenating the
following elements:

    0. requestor's site id + newline ("\n")
    1. requestor's API secret key + newline ("\n")
    2. http host (as presented in HTTP request) + newline ("\n")
    3. http verb (as presented in HTTP request) + newline ("\n")
    4. request URL (excluding query string, and then percent-encoded) + newline ("\n")
    5. datetime in iso8601 format (YYYYMMDDTHHMMSSZ) + newline ("\n")
    6. body-hash + newline ("\n").  If no body is present, just newline.
    7. signed-header-lines + newline ("\n")
    8. signed-query-string-fields + newline ("\n")

A string hexdigest is computed from the signature string:

    signature = HEXDIGEST(HMAC(signature-string))

The requestor then constructs a valid authorization value for use in
the Promotably-Auth header by concatenating the following
strings:

    1. "hmac-sha1/" +
    2. names (not values) of signed headers, sorted alphabetically and
       separated by commas (",").  Eg., "Accept,User-Agent"
    3. "/"
    4. names (not values) of signed query string fields, sorted alphabetically and
       separated by commas (",").  Eg., "event-name,product-id,site-id"
    5. "/"
    6. datetime in iso8601 format (YYYYMMDDTHHMMSSZ)
    7. "/"
    8. signature (as computed above)

The resulting string is then url encoded, and the value of the
Promotably-Auth header is set to the result.

Alternatively, the requestor may append the authorization value as an
additional query string parameter.  Eg.,
"&promotably-auth=..."  If so, the uri used in computation
of the signature string (above) does not include the
promotably-auth parameter.  If transmitted in the query
string, this query parameter must the be the last one present in the
query string.

## Tracking API

As shoppers explore and interact with our customers' e-commerce sites,
their behvaior reveals intent and other information that Signal96 uses
for product recommendation, dynamic promotion creation and display,
and other uses.  This shopper behavior is transmitted to our tracking
API by a javascript library residing on the e-commerce site.

### Tracking endpoint

#### SUMMARY

JSONP endpoint for tracking shopper interaction with customer
e-commerce sites.

ENDPOINT: GET https://api.promotably.com/v1/track

AUTHENTICATION: signature

RESPONSE STATUS: 200

RESPONSE BODY: none

#### QUERY PARAMETERS

    * site-id - Required.  String.  Identifies customer site.
    * event-type - Required.  String.  Event name.
    * <event type dependent> - See below.

#### NOTES

Accepted event types include:

    * _trackProductView
    * _trackProductAdd
    * _trackCartView
    * _trackCheckout
    * _trackThankYou

#### _trackProductView

Accepted query string parameters include:

    * shopper-id - Zero or one.  String.  Persistent ID of shopper, if known.
    * product-id - Required.  String.
    * title - Required.  String.
    * short-description - Optional.  String.
    * modified-at - Optional.  Viz http://goo.gl/yUwaVG, http://goo.gl/M45cmt
    * description - Optional.  String.
    * variation[] - Zero or more.  String. Value of each query param is
                    of the form "variation-id,variation-value".  For
                    example: ...&variation[]=1,2&variation[]=2,9...

#### _trackProductAdd

Accepted query string parameters include:

    * shopper-id - Zero or one.  String.  Persistent ID of shopper, if known.
    * product-id - Required.  String.
    * quantity - Required.  Positive integer > 0 expressed as a string.
    * variation[] - Zero or more.  String. Value of each query param is
                    of the form "variation-id,variation-value".  For
                    example: ...&variation[]=1,2&variation[]=2,9...

#### _trackCartView

Accepted query string parameters include:

    * shopper-id - Zero or one.  String.  Persistent ID of shopper, if known.
    * cart-item[] - Zero or more.  String. Value of each query
                    param is of the form "product-id,product-title,product-type,variation-id,variation,quantity"

For example:  ...&cart-item[]=11,Widget,simple,0,0,1&cart-item[]=98,Thingie,simple,1,7,2...

#### _trackCheckout

Accepted query string parameters include:

    * shopper-id - Zero or one.  String.  Persistent ID of shopper, if known.
    * billing-address - Zero or one.  String.  Value of each query
                        param is of the form
                        "name,address-1,address-2,city,state,postcode,phone"
    * shipping-address - Zero or one.  String.  Value of each query
                         param is of the form
                         "name,address-1,address-2,city,state,postcode,phone"
    * applied-coupon[] - Zero or more.  String.
    * cart-item[] - Zero or more.  String. Value of each query param
                    is of the form
                    "product-id,product-title,product-type,variation-id,variation,quantity".
                    For example:
                    ...&cart-item[]=11,Widget,simple,0,0,1&cart-item[]=98,Thingie,simple,1,7,2...

#### _trackThankYou

Accepted query string parameters include:

    * shopper-id - Zero or one.  String.  Persistent ID of shopper, if known.
    * shopper-email - Zero or one.  String.
    * order-id - Required.  String unique identifier for order.
    * billing-address - Zero or one.  String.  Value of each query
                        param is of the form
                        "firstname,lastname,address-1,address-2,city,state,postcode,country,phone"
    * billing-email - Zero or one.  String.
    * shipping-address - Zero or one.  String.  Value of each query
                         param is of the form
                         "name,address-1,address-2,city,state,postcode,phone"
    * applied-coupon[] - Zero or more.  String.
    * cart-item[] - Zero or more.  String. Value of each query param
                    is of the form
                    "product-id,product-title,product-type,variation-id,variation,quantity,line-subtotal,line-total".
                    For example:
                    ...&cart-item[]=11,Widget,simple,0,0,1&cart-item[]=98,Thingie,simple,1,7,2,10.00,20.00...

## COUPON API

Promotably augments our customers' e-commerce platforms with a robust,
flexible and intuitive system for creating, managing, using and
tracking promotions and coupons.  Promotably integrations with customer
e-commerce platforms are usually accomplished with plugins, which
enhance (or sometimes replace) native e-commerce platform capabilities
with Promotably's SaaS coupon system.

Our plugins call the Promotably Coupon API to query and validate
coupons as well as to calculate discounts.

### Query Endpoint

#### SUMMARY

Called by e-commerce platform plugins (the server side component) to
check if a given coupon exists, and to get its parameters.

ENDPOINT: GET https://api.signal96.com/v1/promos/query/<coupon-code>

AUTHENTICATION: signature

RESPONSE STATUS: 200

RESPONSE BODY: JSON.  See below.

#### QUERY PARAMETERS

    * site-id - Required.  String.  Identifies customer site.

#### NOTES

The response has the JSON form:

{
    "amount":"20",
    "apply-before-tax": true,
    "code":"TWENTYOFF",
    "current-usage-count": 18,
    "exclude-product-categories":["underpants"],
    "exclude-product-ids":[]
    "exclude-sale-items":true,
    "expiry-date": "2007-01-31T23:59:59Z",
    "free-shipping": false,
    "id":"a2e628a8-1ef5-4efa-9b8b-ffdc99725485",
    "incept-date": "2006-01-31T23:59:59Z",
    "individual-use": true,
    "limit-usage-to-x-items":1,
    "max-usage-count": 100,
    "minimum-cart-amount": 100,
    "minimum-product-amount": 0,
    "product-categories":["widgets","thingies"],
    "product-ids":[1,34,99],
    "type":"percent_product",
    "usage-limit-per-user": -1,
}

* type: Enum.  One of "percent_product", "amount_product",
  "percent_cart", "amount_cart".

* minimum-product-amount: Float.  If product-ids is not empty, to
  qualify for this promo, the product to which it applies must exceed
  this amount in value.  If product-categories is not empty, the
  qualify for this promo, the cart must contain an item from an
  allowed category which exceeds this amount in value.  If both of
  those fields are empty, the shopper's cart must have at least one
  item that exceeds this amount in value.

* minimum-cart-amount: Float.  The total value of the shopper's cart
  must exceed this amount in value to qualify for this promo.  

TODO: Response should include nonce value for subsequent requests?

### Validation Endpoint

#### SUMMARY

Called by e-commerce platform plugins (the server side component) to
check if a given coupon is valid for use.

ENDPOINT: POST https://api.signal96.com/v1/promos/validation/<coupon-code>

AUTHENTICATION: signature

REQUEST BODY: JSON.  See below.

RESPONSE STATUS: 201

RESPONSE BODY: JSON.  See below.

#### QUERY PARAMETERS

    * site-id - Required.  String.  Identifies customer site.

#### NOTES

The request has the JSON form:

{
    "code":"TWENTYOFF",
    "shopper-id": "12-AB33",
    "shopper-email": "cvillecsteele@gmail.com",
    "applied-coupons":["FIFTYOFF","FREESHIP"],
    "cart-items":[
        {"product-id":1,
         "product-title":"Widget",
         "product-type":"simple",
         "product-categories":["Hammers","Tools"],
         "variation-id":0,
         "variation":0,
         "quantity":2,
         "line-total":10,
         "line-subtotal":20,
         "line-tax":0,
         "line-subtotal-tax":0
        },
        {"product-id":10,
         "product-title":"Undies",
         "product-type":"simple",
         "product-categories":["Clothes","Unmentionables"],
         "variation-id":0,
         "variation":0,
         "quantity":1,
         "line-total":10,
         "line-subtotal":10,
         "line-tax":0,
         "line-subtotal-tax":0
        }
    ],
    "product-ids-on-sale":[11,89]
}

The response has the JSON form:

{
    "code":"TWENTYOFF",
    "id":"a2e628a8-1ef5-4efa-9b8b-ffdc99725485",
    "valid":false,
    "message":"That coupon has expired."
}

TODO: Do we need a variation of this endpoint for doing a validation
against a particular product instead of the whole cart?

### Discount Endpoint

?

#### SUMMARY

Called by e-commerce platform plugins (the server side component) to
calculate the discount for a given coupon.

ENDPOINT: POST https://api.signal96.com/v1/promos/calculation/<coupon-code>

AUTHENTICATION: signature

REQUEST BODY: JSON.  See below.

RESPONSE STATUS: 201

RESPONSE BODY: JSON.  See below.

#### QUERY PARAMETERS

    * site-id - Required.  String.  Identifies customer site.

#### NOTES

The request has the JSON form:

{
    "code":"TWENTYOFF",
    "shopper-id": "12-AB33",
    "shopper-email": "cvillecsteele@gmail.com",
    "applied-coupons":["FIFTYOFF","FREESHIP"],
    "cart-items":[
        {"product-id":1,
         "product-title":"Widget",
         "product-type":"simple",
         "product-categories":["Hammers","Tools"],
         "variation-id":0,
         "variation":0,
         "quantity":2,
         "line-total":10,
         "line-subtotal":20,
         "line-tax":0,
         "line-subtotal-tax":0
        },
        {"product-id":10,
         "product-title":"Undies",
         "product-type":"simple",
         "product-categories":["Clothes","Unmentionables"],
         "variation-id":0,
         "variation":0,
         "quantity":1,
         "line-total":10,
         "line-subtotal":10,
         "line-tax":0,
         "line-subtotal-tax":0
        }
    ],
    "product-ids-on-sale":[11,89]
}

The response has the JSON form:

{
    "code":"TWENTYOFF",
    "id":"a2e628a8-1ef5-4efa-9b8b-ffdc99725485",
    "valid":true,
    "message":"Thanks for shopping!",
    "discount-amount": 2.00,
    "discounted-item":true,
    "discounted-line":false,
    "discounted-cart":false,
    "discounted-product-id":1,
    "number-discounted-items":1
}

TODO: Need to tighten up definition and verbiage around cart items,
products, line items, etc...

## License

Copyright © 2014 Promotably

