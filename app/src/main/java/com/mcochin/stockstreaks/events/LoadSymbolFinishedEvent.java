package com.mcochin.stockstreaks.events;

import com.mcochin.stockstreaks.pojos.Stock;

/**
 * Created by Marco on 12/24/2015.
 */
public class LoadSymbolFinishedEvent {
    private Stock mStock;
    private boolean mSuccessful;

    public LoadSymbolFinishedEvent(Stock stock, boolean successful){
        mStock = stock;
        mSuccessful = successful;
    }

    public Stock getStock() {
        return mStock;
    }

    public void setStock(Stock stock) {
        mStock = stock;
    }

    public boolean isSuccessful() {
        return mSuccessful;
    }

    public void setSuccessful(boolean successful) {
        mSuccessful = successful;
    }
}