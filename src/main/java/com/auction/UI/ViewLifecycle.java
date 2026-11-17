package com.auction.UI;

public interface ViewLifecycle {
    void onViewShown();
    void onViewHidden();
    default void dispose(){};
}
