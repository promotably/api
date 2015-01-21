(ns api.lib.crypto
  (:refer-clojure :exclude [compare])
  (:require [clojurewerkz.scrypt.core :as sc])
  (:import [java.security MessageDigest SecureRandom]
           [javax.crypto SecretKeyFactory Cipher KeyGenerator SecretKey]
           [javax.crypto.spec PBEKeySpec SecretKeySpec]
           [org.apache.commons.codec.binary Base64]
           [java.util UUID]))

(defn bytes [s]
  (.getBytes s "UTF-8"))

(defn base64 [b]
  (Base64/encodeBase64String b))

(defn debase64 [s]
  (Base64/decodeBase64 (bytes s)))

(defn get-raw-key [seed]
  (let [keygen (KeyGenerator/getInstance "AES")
        sr (SecureRandom/getInstance "SHA1PRNG")]
    (.setSeed sr (bytes seed))
    (.init keygen 128 sr)
    (.. keygen generateKey getEncoded)))

(defn get-cipher [mode seed]
  (let [key-spec (SecretKeySpec. (get-raw-key seed) "AES")
        cipher (Cipher/getInstance "AES")]
    (.init cipher mode key-spec)
    cipher))

(defn aes-encrypt [text key]
  (let [bytes (bytes text)
        cipher (get-cipher Cipher/ENCRYPT_MODE key)]
    (base64 (.doFinal cipher bytes))))

(defn aes-decrypt [text key]
  (let [cipher (get-cipher Cipher/DECRYPT_MODE key)]
    (String. (.doFinal cipher (debase64 text)))))

(defn
  ^{:private true}
  hasher
  "Hashing digest action handler. Common types -> SHA1,SHA-256,MD5"
  [instance-type data salt]
  (let [_ (if-not salt
            (.toString data)
            (let [[s d] (map
                         (memfn toString)
                         [salt data])]
              (apply str [s d s])))
        hash-obj (doto (MessageDigest/getInstance instance-type)
                   .reset
                   (.update
                    (.getBytes _)))]
    (apply str
           (map (partial format "%02x")
                (.digest hash-obj)))))

(defn md5
  [data & salt]
  (hasher "MD5" data salt))

(defn sha1
  [data & salt]
  (hasher "SHA1" data salt))

(defn sha2
  [data & salt]
  (hasher "SHA-256" data salt))

(defn gen-pbkdf2
  [x salt]
  (let [k (PBEKeySpec. (.toCharArray x) (.getBytes salt) 1000 192)
        f (SecretKeyFactory/getInstance "PBKDF2WithHmacSHA1")]
    (->> (.generateSecret f k) (.getEncoded) (java.math.BigInteger.) (format "%x"))))

(defn s-encrypt
  "Encrypts a string value using scrypt.
   Arguments are:
   raw (string): a string to encrypt
   :n (integer): CPU cost parameter (default is 16384)
   :r (integer): RAM cost parameter (default is 8)
   :p (integer): parallelism parameter (default is 1)
   The output of SCryptUtil.scrypt is a string in the modified MCF format:
   $s0$params$salt$key
   s0     - version 0 of the format with 128-bit salt and 256-bit derived key
   params - 32-bit hex integer containing log2(N) (16 bits), r (8 bits), and p (8 bits)
   salt   - base64-encoded salt
   key    - base64-encoded derived key"
    [raw & {:keys [n r p]
             :or {n 16384 r 8 p 1}}]
  (sc/encrypt raw n r p))

(defn compare
  "Compare a raw string with an already encrypted string"
  [raw encrypted]
  (boolean
   (if (and raw encrypted)
    (sc/verify raw encrypted))))

(defn sha1-sign-hex
  "Using a signing key, compute the sha1 hmac of v and convert to hex."
  [sign-key v]
  (let [mac (javax.crypto.Mac/getInstance "HmacSHA1")
        secret (SecretKeySpec. (.getBytes sign-key), "HmacSHA1")]
    (.init mac secret)
    (apply str (map (partial format "%02x") (.doFinal mac (.getBytes v))))))

(defn encrypt-password
  [password]
  (let [salt (str (UUID/randomUUID))
        salted-pw (str salt password salt)
        scrypt-pw (s-encrypt salted-pw)]
    [scrypt-pw salt]))

(defn verify-password
  [password salt encrypted-pw]
  (let [salted-pw (str salt password salt)]
    (compare salted-pw encrypted-pw)))
