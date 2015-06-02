(ns api.lib.traffic
  (:require
   [clojure.walk :refer [postwalk prewalk]]
   [clojure.set :as set]
   [clojure.tools.logging :as log]
   [clj-time.format]
   [clj-time.coerce :as t-coerce]
   [clj-time.core :as t]
   [api.config :as config]
   [api.kinesis :as kinesis]
   [api.lib.schema :refer :all]
   [api.lib.coercion-helper :refer [transform-map
                                    make-trans
                                    remove-nils
                                    custom-matcher
                                    underscore-to-dash-keys]]
   [schema.coerce :as sc]
   [schema.core :as s]
   [ring.util.codec :as codec]
   [clojure.walk :as walk]))

;; Organic Search: Non-paid visits from recognized search engines,
;; like Google, Bing, Yahoo, etc.

;; Referrals: Visits from links clicked on other websites.

;; Social Media: Visits from links clicked on social media sites, like
;; Facebook, Twitter, LinkedIn, etc.

;; Email Marketing: Visits from tracking links in your emails.

;; Paid Search: Visits from paid search ads that are either
;; automatically identified by HubSpot or set by tracking URLs.

;; Direct Traffic: Visits to your website with no referring source or
;; tracking URL. Usually visitors who type your website directly into
;; their browser.

;; Campaigns: Visits from campaigns that are being monitored
;; with specific tracking URL parameters other than those used for
;; Social Media, Email, or Paid Search.


;; GOOGLE QUERY STRINGS:

;; utm_source=XXX This is the source of the link Example: Search
;; Engine, Referring Domain, or name of email list

;; utm_medium=XXX This is the method of delivery. EX: Postcard, Email,
;; or Banner Ad

;; utm_campaign=XXX This is a name that helps you keep track of your
;; different campaign efforts Example: Fall_Drive, Christmas_Special

; utm_term=XXX Used to identify paid keywords. Example: speakers,
; monitors, shoes

;; utm_content=XXX This is for split testing or separating two ads
;; that go to the same URL

(def utm-terms [:utm_source :utm_campaign :utm_medium :utm_term :utm_content])

;; https://helpx.adobe.com/analytics/kb/list-social-networks.html
(def socials
  ["12seconds.tv"
   "4travel.jp"
   "advogato.org"
   "ameba.jp"
   "anobii.com"
   "asmallworld.net"
   "backtype.com"
   "badoo.com"
   "bebo.com"
   "bigadda.com"
   "bigtent.com"
   "biip.no"
   "blackplanet.com"
   "blog.seesaa.jp"
   "blogspot.com"
   "blogster.com"
   "blomotion.jp"
   "bolt.com"
   "brightkite.com"
   "buzznet.com"
   "cafemom.com"
   "care2.com"
   "classmates.com"
   "cloob.com"
   "collegeblender.com"
   "cyworld.co.kr"
   "cyworld.com.cn"
   "dailymotion.com"
   "delicious.com"
   "deviantart.com"
   "digg.com"
   "diigo.com"
   "disqus.com"
   "draugiem.lv"
   "facebook.com"
   "faceparty.com"
   "fc2.com"
   "flickr.com"
   "flixster.com"
   "fotolog.com"
   "foursquare.com"
   "friendfeed.com"
   "friendsreunited.com"
   "friendster.com"
   "fubar.com"
   "gaiaonline.com"
   "geni.com"
   "goodreads.com"
   "grono.net"
   "habbo.com"
   "hatena.ne.jp"
   "hi5.com"
   "hotnews.infoseek.co.jp"
   "hyves.nlibibo.comidenti.ca"
   "imeem.com"
   "intensedebate.com"
   "irc-galleria.net"
   "iwiw.hu"
   "jaiku.com"
   "jp.myspace.com"
   "kaixin001.com"
   "kaixin002.com"
   "kakaku.com"
   "kanshin.com"
   "kozocom.com"
   "last.fm"
   "linkedin.com"
   "livejournal.com"
   "matome.naver.jp"
   "me2day.net"
   "meetup.com"
   "mister-wong.com"
   "mixi.jp"
   "mixx.com"
   "mouthshut.com"
   "multiply.com"
   "myheritage.com"
   "mylife.com"
   "myspace.com"
   "myyearbook.com"
   "nasza-klasa.pl"
   "netlog.com"
   "nettby.no"
   "netvibes.com"
   "nicovideo.jp"
   "ning.com"
   "odnoklassniki.ru"
   "orkut.com"
   "pakila.jp"
   "photobucket.com"
   "pinterest.com"
   "plaxo.com"
   "plurk.com"
   "plus.google.com"
   "reddit.com"
   "renren.com"
   "skyrock.com"
   "slideshare.net"
   "smcb.jp"
   "smugmug.com"
   "sonico.com"
   "studivz.net"
   "stumbleupon.com"
   "t.163.com"
   "t.co"
   "t.hexun.com"
   "t.ifeng.com"
   "t.people.com.cn"
   "t.qq.com"
   "t.sina.com.cn"
   "t.sohu.com"
   "tabelog.com"
   "tagged.com"
   "taringa.net"
   "thefancy.com"
   "tripit.com"
   "trombi.com"
   "trytrend.jp"
   "tuenti.com"
   "tumblr.com"
   "twine.com"
   "twitter.com"
   "uhuru.jp"
   "viadeo.comvimeo.com"
   "vk.com"
   "vox.com"
   "wayn.com"
   "weibo.com"
   "weourfamily.com"
   "wer-kennt-wen.de"
   "wordpress.com"
   "xanga.com"
   "xing.com"
   "yammer.com"
   "yaplog.jp"
   "yelp.com"
   "youku.com"
   "youtube.com"
   "yozm.daum.net"
   "yuku.com"
   "zooomr.com"])

;; https://developers.google.com/analytics/devguides/collection/gajs/gaTrackingTraffic
(def engines
  [["Daum" "www.daum.net" "q"]
   ["Eniro" "www.eniro.se" "search_word"]
   ["Naver" "www.naver.com" "query"]
   ["Google" "www.google.com" "q"]
   ["Yahoo" "www.yahoo.com" "p"]
   ["MSN" "www.msn.com" "q"]
   ["Bing" "www.bing.com" "q"]
   ["AOL" "www.aol.com" "query"]
   ["AOL" "www.aol.com" "encquery"]
   ["Lycos" "www.lycos.com" "query"]
   ["Ask" "www.ask.com" "q"]
   ["Altavista" "www.altavista.com" "q"]
   ["Netscape" "search.netscape.com" "query"]
   ["CNN" "www.cnn.com" "SEARCH/query"]
   ["About" "www.about.com" "terms"]
   ["Mamma" "www.mamma.com" "query"]
   ["Alltheweb" "www.alltheweb.com" "q"]
   ["Voila" "www.voila.fr" "rdata"]
   ["Virgilio" "search.virgilio.it" "qs"]
   ["Live" "www.bing.com" "q"]
   ["Baidu" "www.baidu.com" "wd"]
   ["Alice" "www.alice.com" "qs"]
   ["Yandex" "www.yandex.com" "text"]
   ["Najdi" "www.najdi.org.mk" "q"]
   ["AOL" "www.aol.com" "q"]
   ["Mama" "www.mamma.com" "query"]
   ["Seznam" "www.seznam.cz" "q"]
   ["Search" "www.search.com" "q"]
   ["Wirtulana Polska" "www.wp.pl" "szukaj"]
   ["O*NET" "online.onetcenter.org" "qt"]
   ["Szukacz" "www.szukacz.pl" "q"]
   ["Yam" "www.yam.com" "k"]
   ["PCHome" "www.pchome.com" "q"]
   ["Kvasir" "www.kvasir.no" "q"]
   ["Sesam" "sesam.no" "q"]
   ["Ozu" "www.ozu.es" "q"]
   ["Terra" "www.terra.com" "query"]
   ["Mynet" "www.mynet.com" "q"]
   ["Ekolay" "www.ekolay.net" "q"]
   ["Rambler" "www.rambler.ru" "words"]])

(defn from-engine?
  [referer params]
  (->> engines
       (filter #(and referer
                     (.contains referer (second %))
                     ((keyword (last %)) params)))
       first))

(defn from-social?
  [referer]
  (->> socials
       (filter #(and referer (.contains referer %)))
       first))

;; Based on http://help.hubspot.com/articles/KCS_Article/Reports/How-does-HubSpot-categorize-visits-contacts-and-customers-in-the-Sources-Report
(defn source
  [{:keys [referer page] :as event}]
  (let [qs (if (and page (not= "" page)) (.getQuery (java.net.URL. page)))
        params (if qs (-> qs codec/form-decode walk/keywordize-keys))
        data {:referer referer
              :page page}
        utms (select-keys params utm-terms)
        engine (from-engine? referer params)
        social (from-social? referer)
        tagged? (seq utms)
        social? (or (= (:utm_medium utms) "social") social)
        email? (seq (filter #(re-find #"email" %) (vals utms)))
        has-referer? (not (or (nil? referer) (= "" referer)))
        paid? (or
               (seq (filter #(re-find #"ppc|cpc|adword" %) (vals utms)))
               (:gclid params)
               (and has-referer?
                    (seq utms)
                    (re-find #"google\.com" (or referer ""))))]

    (cond-> {:category :unknown :data (merge utms data)}

            (and (not social?) (not email?) paid?)
            (assoc :category :paid)

            (and (not social?) (not email?) (not paid?) tagged?)
            (assoc :category :campaign)

            (and (not social?) (not email?) (not paid?) engine)
            (assoc :category :organic-search
                   :data (assoc data :search-terms ((-> engine last keyword) params)))

            (and social? (not email?) (not paid?))
            (assoc :category :social)

            (and (not social?) email? (not paid?))
            (assoc :category :email)

            (and has-referer?
                 (not (or paid? social? engine email? tagged?)))
            (assoc :category :referral)

            (and (not tagged?) (not has-referer?))
            (assoc :category :direct))))



