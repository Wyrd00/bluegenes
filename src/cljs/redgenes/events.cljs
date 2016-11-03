(ns redgenes.events
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [re-frame.core :as re-frame :refer [reg-event-db reg-event-fx reg-fx dispatch subscribe]]
            [redgenes.db :as db]
            [redgenes.sections.objects.handlers]
            [redgenes.components.search.events]
            [redgenes.components.databrowser.events]
            [redgenes.components.search.events :as search-full]
            [redgenes.sections.objects.handlers]
            [imcljs.search :as search]
            [imcljs.assets :as assets]
            [imcljs.user :as user]
            [day8.re-frame.http-fx]
            [day8.re-frame.forward-events-fx]
            [day8.re-frame.async-flow-fx]
            [ajax.core :as ajax]
            [cljs.core.async :refer [put! chan <! >! timeout close!]]
            [cljs-time.core :as t]
            [cljs-uuid-utils.core :as uuid]))


; Boot the application.
; 1 Fetch an anonymous token for the current mine
; 2 Fetch all assets for the current mine
(reg-event-fx
  :boot
  (fn []
    (let [db (assoc db/default-db :mines redgenes.mines/mines)]
      {:db         (assoc db/default-db :mines redgenes.mines/mines)
       :async-flow {:first-dispatch [:authentication/fetch-anonymous-token (get db :current-mine)]
                    :rules          [{:when     :seen?
                                      :events   :authentication/store-token
                                      :dispatch [:fetch-all-assets]
                                      :halt?    true}]}})))

; Store an authentication token for a given mine
(reg-event-db
  :authentication/store-token
  (fn [db [_ mine-kw token]]
    (assoc-in db [:mines mine-kw :service :token] token)))

; Fetch an anonymous token for a give
(reg-event-fx
  :authentication/fetch-anonymous-token
  (fn [{db :db} [_ mine-kw]]
    {:db           db
     :im-operation {:on-success [:authentication/store-token mine-kw]
                    :op         (partial user/session (get-in db [:mines mine-kw :service]))}}))

(reg-fx
  :im-operation
  (fn [{:keys [on-success on-failure response-format op params]}]
    (go (dispatch (conj on-success (<! (op)))))))

(reg-event-fx
  :unqueue
  (fn [{db :db}]
    (merge {:db (-> db
                    (assoc :active-panel (:active-panel (:queued db)))
                    (assoc :panel-params (:panel-params (:queued db)))
                    (dissoc db :queued))}
           (if (:and-then (:queued db))
             {:dispatch (:and-then (:queued db))}))))




(reg-event-fx
  :set-active-panel
  (fn [{db :db} [_ active-panel panel-params evt]]
    (if (:fetching-assets? db)
      ; Queue our route until the assets have been fetched
      {:db             (assoc db :queued {:active-panel active-panel
                                          :panel-params panel-params
                                          :and-then     evt})
       :forward-events {:register    :route-forwarder
                        :events      #{:finished-loading-assets}
                        :dispatch-to [:unqueue]}}
      ; Otherwise route immediately, and fire an optional post-route event
      (merge {:db (assoc db :active-panel active-panel
                            :panel-params panel-params)}
             (if evt {:dispatch evt})))))

(reg-event-db
  :good-who-am-i
  (fn [db [_ result]]
    (assoc db :who-am-i (:user result))))

(reg-event-fx
  :set-active-mine
  (fn [{:keys [db]} [_ value]]
    {:db         (-> (assoc db :mine-name value)
                     (assoc :saved-data {:items {}}))
     :dispatch-n (list [:fetch-all-assets] [:set-active-panel :home-panel])}))

(reg-event-fx
  :new-temporary-mine
  (fn [{:keys [db]} [_ new-url]]
    (let [url
          (if (clojure.string/starts-with? new-url "http://")
            (subs new-url 7)
            new-url)]
      (.log js/console "%curl" "color:hotpink;font-weight:bold;" (clj->js url))
      {:db
                 (assoc db :temporary-mine {:temporary-mine {
                                                             ;;we can make this more dynamic when we're grown up
                                                             :id      :temporary-mine
                                                             :common  "New Organism"
                                                             :status  {:status :na}
                                                             :output? true
                                                             :abbrev  "New Organism"
                                                             :mine
                                                                      {:name    "New Organism"
                                                                       :url     url
                                                                       :service {:root url}}}}
                           :mine-name :temporary-mine)
       :dispatch [:fetch-all-assets]})))


(reg-event-fx
  :log-in
  (fn [{:keys [db]} _]
    {:db         (assoc db :show-twirly true)
     :http-xhrio {:method          :get
                  :uri             (str @(subscribe [:mine-url]) "/service/user/whoami")
                  :params          {:token ""}
                  :timeout         8000
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [:good-who-am-i]
                  :on-failure      [:bad-http-result]}}))

(reg-event-db
  :async-assoc
  (fn [db [_ location-vec val]]
    (assoc-in db location-vec val)))

(reg-event-db
  :log-out
  (fn [db]
    (dissoc db :who-am-i)))

(reg-event-db
  :handle-suggestions
  (fn [db [_ results]]
    (assoc db :suggestion-results results)))

(reg-fx
  :suggest
  (fn [{:keys [c search-term]}]
    (if (= "" search-term)
      (dispatch [:handle-suggestions nil])
      (go (dispatch [:handle-suggestions (<! c)])))))

(reg-event-fx
  :bounce-search
  (fn [{db :db} [_ term]]
    (let [connection   {:root @(subscribe [:mine-url])}
          suggest-chan (search/quicksearch connection term)]
      (if-let [c (:search-term-channel db)] (close! c))
      {:db      (-> db
                    (assoc :search-term-channel suggest-chan)
                    (assoc :search-term term))
       :suggest {:c suggest-chan :search-term term}})))

(reg-event-fx
  :finished-loading-assets
  (fn [{db :db}]
    {:db         (assoc db :fetching-assets? false)
     :dispatch-n [[:cache/fetch-organisms]
                  [:saved-data/load-lists]
                  [:regions/select-all-feature-types]]}))

(reg-fx
  :fetch-assets
  (fn [[mine-kw service]]
    (let [c1        (assets/templates service)
          c2        (assets/lists service)
          c3        (assets/model service)
          c4        (assets/summary-fields service)
          locations {c1 [:assets :templates]
                     c2 [:assets :lists mine-kw]
                     c3 [:assets :model]
                     c4 [:assets :summary-fields]}]
      (go-loop [channels [c1 c2 c3 c4]]
               (let [[v p] (alts! channels)]
                 (if-not (and (nil? v) (empty? channels))
                   (let [remaining (remove #(= % p) channels)]
                     (dispatch [:test-progress-bar (* 100 (/ (- 4 (count remaining)) 4))])
                     (dispatch [:async-assoc (get locations p) v])
                     (if-not (empty? remaining)
                       (recur remaining)
                       (dispatch [:finished-loading-assets])))))))))

(reg-event-fx
  :fetch-all-assets
  (fn [{db :db}]
    (let [current-mine (get db :current-mine)]
      {:db           (assoc db :fetching-assets? true
                              :progress-bar-percent 0)
      :fetch-assets [current-mine (get-in db [:mines current-mine :service])]})))

(reg-event-db
  :test-progress-bar
  (fn [db [_ percent]]
    (assoc db :progress-bar-percent percent)))

(reg-event-db
  :cache/store-organisms
  (fn [db [_ res]]
    (assoc-in db [:cache :organisms] (:results res))))

(reg-event-fx
  :cache/fetch-organisms
  (fn [{db :db}]
    (let [model          (get-in db [:assets :model])
          organism-query {:from   "Organism"
                          :select ["name"
                                   "taxonId"
                                   "species"
                                   "shortName"
                                   "genus"
                                   "commonName"]}]
      {:db           db
       :im-operation {:op         (partial search/raw-query-rows
                                           {:root @(subscribe [:mine-url])}
                                           organism-query
                                           {:format "jsonobjects"})
                      :on-success [:cache/store-organisms]}})))