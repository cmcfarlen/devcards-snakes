(ns snakes.core
  (:require
   [om.core :as om :include-macros true]
   [goog.events :as events]
   [cljs.test :as t :refer  [report] :include-macros true]
   [cljs.core.async :as async :refer [<! >!]]
   [sablono.core :as sab :include-macros true]
   [devcards.core :as dc])
  (:require-macros
   [devcards.core :as dc :refer [defcard deftest defcard-om]]
   [cljs.test :refer [is testing async]]
   [cljs.core.async.macros :refer [go]]))

(enable-console-print!)

(defcard example-board
  {:width 5
   :height 5
   :food [[2 2]]
   :length 2
   :snake [[1 2] [1 3]]
   })

(defn tile-at
  [{:keys [food snake]} pt]
  (cond
   (= pt (first snake))
   :head

   (some #(= pt %) food)
   :food

   (some #(= pt %) snake)
   :snake

   :else
   :empty
   ))

(deftest test-tile-at
  (testing "tile-at returns the tile type at a position"
    (let [bd {:width 5
              :height 5
              :food [[2 2]]
              :length 2
              :snake [[1 2] [1 3]]}]
      (is (= :food (tile-at bd [2 2])))
      (is (= :head (tile-at bd [1 2])))
      (is (= :snake (tile-at bd [1 3])))
      (is (= :empty (tile-at bd [3 3]))))))

(defn render-tile
  [bd pt]
  [:div.cell {:key (apply str pt)
              :class (name (tile-at bd pt))}])

(defn render-board
  [{:keys [width height] :as bd}]
  [:div.board
   (for [y (range height)]
     [:div.row {:key (str "row" y)}
      (for [x (range width)]
        (render-tile bd [x y]))]) ])

(defcard test-render-board
  "Render a crappy html version of the board"
  (sab/html (render-board {:width 10
                           :height 10
                           :food [[1 2]]
                           :snake [[2 2] [2 3] [3 3]]})))

(defn create-board
  "Create a new board"
  [size]
  (let [m (quot size 2)]
    {:width size
     :height size
     :food []
     :length 1
     :snake [[m m]]
     :dir [1 0]}))

(defcard
  (create-board 10))

(defcard
  (let [bd (create-board 15)]
    (sab/html [:div
               (render-board (create-board 15))
               (dc/edn bd)])))

(defn wrap-value
  [v m]
  (cond
   (= v m)
   0

   (> v m)
   (wrap-value (- v m) m)

   (< v 0)
   (wrap-value (+ m v) m)

   :else
   v))

(defn wrap+
  [a b m]
  [(wrap-value (+ (first a) (first b)) (first m))
   (wrap-value (+ (second a) (second b)) (second m))])

(deftest test-wrap-value
  (testing "wrap values"
    (is (= 0 (wrap-value 5 5)))
    (is (= 1 (wrap-value 6 5)))
    (is (= 1 (wrap-value 11 5)))
    (is (= 4 (wrap-value -1 5)))
    (is (= 3 (wrap-value -2 5)))
    (is (= 3 (wrap-value -7 5)))
    (is (= 3 (wrap-value 3 5))))
  (testing "wrap+"
    (is (= [1 1] (wrap+ [0 0] [1 1] [5 5])))
    (is (= [0 1] (wrap+ [0 0] [5 1] [5 5])))
    (is (= [0 0] (wrap+ [4 4] [1 1] [5 5])))))

(defn add-food
  [bd]
  (let [pt [(rand-int (:width bd)) (rand-int (:height bd))]]
    (if (= :empty (tile-at bd pt))
      (update bd :food conj pt)
      (recur bd))))

(defn step
  [{:keys [width height dir food snake length dead] :as bd}]
  (if-not dead
    (let [new-head (wrap+ dir (first snake) [width height])]
      (cond->
        (assoc bd :snake
               (take length (cons new-head snake )))

        (= :food (tile-at bd new-head))
        (->
         (update :food (fn [f] (remove #(= new-head %) f)))
         (update :length inc)
         add-food)

        (some #(= new-head %) snake)
        (assoc :dead true)))
    bd))

(defn step-dir
  [bd dir]
  (step (assoc bd :dir dir)))

(defcard take-step
  (let [bd (update (create-board 5) :food conj [3 2])]
    (sab/html [:div.grid
               (for [b (take 10 (iterate step bd))]
                 [:div.horizontal (render-board b)]) ])))

(defcard play-game
  (fn [data _]
    (sab/html
     [:div
      (render-board @data)
      [:div [:button {:on-click (fn [] (swap! data step))} "step"]]
      [:div [:button {:on-click (fn [] (swap! data step-dir [0 -1]))} "up"]]
      [:div [:button {:on-click (fn [] (swap! data step-dir [0 1]))} "down"]]
      [:div [:button {:on-click (fn [] (swap! data step-dir [-1 0]))} "left"]]
      [:div [:button {:on-click (fn [] (swap! data step-dir [1 0]))} "right"]]
      [:div [:button {:on-click (fn [] (swap! data add-food))} "food"]]
      [:div [:button {:on-click (fn [] (reset! data (add-food (create-board 10))))} "reset"]]]))
  (add-food (create-board 10))
  {:inspect-data true :history true}
  )

;; with keyboard input

(defn listen [e type]
  "Listen for the event type and put the event on a channel.  If the channel closes, unlisten to the event"
  (let [c (async/chan)
        evkey (atom nil)
        lf (fn [e]
             (async/put! c e #(when-not %
                                (events/unlistenByKey @evkey)))) ]
    (reset! evkey (events/listen e type lf))
    c))


(defcard-om keyboard-input
  "Use vi move keys to move the guy around (arrows don't work here)."
  (fn [data owner]
    (reify
      om/IDidMount
      (did-mount [_]
        (let [c (listen js/window "keyup")]
          (om/set-state! owner :chan c)
          (go
            (loop []
              (when-let [e (<! c)]
                (case (.-keyCode e)
                  74
                  (om/transact! data #(step-dir % [0 1]))

                  75
                  (om/transact! data #(step-dir % [0 -1]))

                  72
                  (om/transact! data #(step-dir % [-1 0]))

                  76
                  (om/transact! data #(step-dir % [1 0]))

                  (.log js/console (str "key: " (.-keyCode e)))
                  )
                (recur))))))
      om/IWillUnmount
      (will-unmount [_]
        (if-let [c (om/get-state owner :chan)]
          (async/close! c)))
      om/IRender
      (render [_]
        (sab/html
         [:div
          (render-board @data)
          [:div [:button {:on-click (fn [] (om/update! data (add-food (create-board 10))))} "reset"]]
          ]))))
  (add-food (create-board 10)))

;; timed game

(defn timed-repeat-chan
  "return a channel that receives v every rate milliseconds"
  [v rate]
  (let [c (async/chan)]
    (go
      (loop []
        (<! (async/timeout rate))
        (when (>! c v)
          (recur))))
    c))

(defn snake-game
  [size]
  (fn [data owner]
    (reify
      om/IDidMount
      (did-mount [_]
        (let [c (listen js/window "keyup")
              t (timed-repeat-chan :step 200)]
          (om/set-state! owner :keys c)
          (om/set-state! owner :steps t)
          (go
            (loop []
              (when-let [e (<! c)]
                (case (.-keyCode e)
                  (74 40)
                  (om/update! data :dir [0 1])

                  (75 38)
                  (om/update! data :dir [0 -1])

                  (72 37)
                  (om/update! data :dir [-1 0])

                  (76 39)
                  (om/update! data :dir [1 0])

                  82
                  (om/update! data (add-food (create-board size)))

                  true #_(.log js/console (str "key: " (.-keyCode e))))
                (recur))))
          (go
            (loop []
              (when-let [e (<! t)]
                (om/transact! data step)
                (recur))))))
      om/IWillUnmount
      (will-unmount [_]
        (doseq [k [:keys :steps]]
          (if-let [c (om/get-state owner k)]
            (async/close! c))))
      om/IRender
      (render [_]
        (sab/html
         [:div.game
          (render-board @data)])))))

(defcard-om timed-game
  (snake-game 10)
  (add-food (create-board 10)))

(defonce game-state (atom (add-food (create-board 15))))

(defn main []
  ;; conditionally start the app based on wether the #main-app-area
  ;; node is on the page
  (when-let [node (.getElementById js/document "main-app-area")]
    (dc/start-devcard-ui!)
    (om/root (snake-game 15) game-state {:target node})))

(main)

;; remember to run lein figwheel and then browse to
;; http://localhost:3449/cards.html

