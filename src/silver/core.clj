(ns silver.core
  (:require [clojure.spec :as s]
            [clojure.spec.test :as st]
            [clojure.test.check.generators])
  (:import (java.math BigDecimal
                      BigInteger)))

; Type hints are for emphasis, they don't actually enforce anything
; Define a record to TODO:
(defrecord Order
  [^BigInteger id
   ^String     user
   ^BigDecimal quantity
   ^BigDecimal price
   type])

; Remove the map construction method. This prevents
; creating an incomplete order, at least by this method.
; Orders should only be constructed using make-order
(ns-unmap *ns* '->Order)

; Valid order types are :buy and :sell
(def ^:private type? #{:buy :sell})

; Define some specs for an Order
(s/def ::id pos-int?)
(s/def ::user string?)
(s/def ::quantity (s/and decimal? pos? #(not(zero? %))))
(s/def ::price (s/and decimal? pos? #(not(zero? %))))
(s/def ::type #(contains? type? %))

; Client-supplied fields when making an order. :id is system generated
(s/def ::order (s/keys :req-un [::user ::quantity ::price ::type]))

; For example, when defining functions to accept orders
(s/def ::order? #(instance? Order %))

(s/def ::map? map?)

(s/fdef make-order
        :args (s/cat :arg ::order)
        :ret ::order?)

; Something thread-safe to hold order ids, which we
; increment as we use them
(def ^:private order-id (atom (bigint 0)))

(defn- get-order-id
  "Make the next order id"
  []
  (swap! order-id inc))

; Switch on instrumentation. This would be enabled only when running
; regression tests and disabled in production deployment
; (st/instrument 'make-order)

(defn make-order
  "Create an Order, ensuring correct precise number scale"
  [{:keys [user quantity price type]}]
  (Order. (get-order-id)
          user
          (.setScale quantity 2 java.math.RoundingMode/HALF_UP)
          (.setScale price 4 java.math.RoundingMode/HALF_UP)
          type))

; The current orders in the system. Order instances
; keyed by their :id
(def ^:no-doc orders (atom {}))

; Keep sells and buys separately. This seems like the best
; thing, seeing as we maintain them in opposite sort orders
; and will likely show them distinctly on any GUI.
; Maps Order price to the Orders aggregated by that price
; These are sorted maps, in opposite order; buy vs sell.
(def  ^:no-doc buys (atom (sorted-map-by >)))
(def  ^:no-doc sells (atom (sorted-map-by <)))

(s/fdef place-order!
        :args (s/cat :arg ::order?)
        :ret ::id)

(s/fdef cancel-order!
        :args (s/cat :arg ::order?)
        :ret ::id)

(s/fdef get-order-board
        :args (s/cat :arg ::type)
        :ret ::map?)

(defn- make-board-entry
  "Create an order board entry, aggregating to that given, if any"
  [order board-entry]
  (if board-entry
    {:quantity (+ (:quantity board-entry) (:quantity order))
     :price (:price board-entry)
     :type (:type board-entry)}
    {:quantity (:quantity order)
     :price (:price order)
     :type (:type order)}))

(defn- post-to-board!
  "Update the given live order board to reflect the given order"
  [board order]
  (let [price (:price order)]
    (swap! board
           #(assoc %1 price (make-board-entry order (%1 price))))))

(defn- remove-from-board!
  "Update the given live order board to reflect the cancelled Order.
  If removing the last Order the board entry is removed"
  [board order]
  (let [price (:price order)
        board-entry (@board price)
        new-board-entry (assoc board-entry :quantity
                                           (- (:quantity board-entry)
                                              (:quantity order)))]
    (if (zero? (:quantity new-board-entry))
      (swap! board dissoc price)
      (swap! board assoc price new-board-entry))
    true))

; Note - this does not update the current orders and their board
; summary atomically. In an implementation that did actual i/o
; this would be unsatisfactory and we would implement using refs
; and a transaction
(defn place-order!
  "Place an order into the system live orders"
  [order]
  (let [{:keys [id type]} order]
    (when (contains? @orders id)
      (throw (Exception. (str "Duplicate order " id))))
    (swap! orders assoc id order)
    (post-to-board!
      (condp = type
      :buy buys
      :sell sells
      (throw (Exception. (str "Illegal order type " type)))) ;belt+braces, since already spec'd
      order)
    id))

; When cancelling, removing the same Order twice would be
; disasterous for our live boards. Had we used refs then (and
; see also place-order!) we could use a transaction approach.
; This is an optimistic, retry-based method but it makes no sense
; to retry if two threads cancel the same order; furthermore
; we would need to fail gracefully for the other thread(s).
; Instead, and considering our present atom-based strategy, we'll
; use a lock. While this is heavier weight, we consider that
; cancelling orders is much less common and the fail case
; is natural and silent. We're in and out of the lock quickly and
; the consistency semantics are the same as for place-order!
(defn cancel-order!
  "Cancel an order given its :id
  Returns true if the order existed and was cancelled, false otherwise"
  [order]
  (let [id (:id order)]
    (with-local-vars [real-order (@orders id)] ; only remains bound if order is safely successfully removed
      (locking @orders
        (var-set real-order (@orders id))
        (swap! orders dissoc id))
      (if-let [type (:type @real-order)]
        (remove-from-board!
          (condp = type
            :buy buys
            :sell sells
            (throw (Exception. (str "Illegal order type " type)))) ;belt+braces, since already spec'd
          @real-order)
        false))))

(defn get-order-board
  "Get the specified order board"
  [type]
  (condp = type
    :buy @buys
    :sell @sells
    (throw (Exception. (str "Illegal order type " type))))) ;belt+braces, since already spec'd

(defn lookup-order
  "Look up an order by its id"
  [id]
  (@orders id))
