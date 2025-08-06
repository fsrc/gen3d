(ns gen3d.functions.login
  (:require
    [anr.fire.functions :as functions]
    [anr.fire.admin :as f]
    [cljs.core :as c]))


(def on-create-user
  (functions/on-create-user 
    (fn [^js user]
      (-> (f/db)
          (f/col :users)
          (f/doc (.-uid user))
          (f/write-doc 
            {:display-name (.-displayName user)
             :email (.-email user)
             :photo-url (.-photoURL user)}
            false)))))

  
