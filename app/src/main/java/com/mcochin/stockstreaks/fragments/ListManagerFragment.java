package com.mcochin.stockstreaks.fragments;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import com.mcochin.stockstreaks.custom.MyApplication;
import com.mcochin.stockstreaks.data.ListEventQueue;
import com.mcochin.stockstreaks.data.ListManipulator;
import com.mcochin.stockstreaks.data.StockContract.StockEntry;
import com.mcochin.stockstreaks.data.StockProvider;
import com.mcochin.stockstreaks.pojos.Stock;
import com.mcochin.stockstreaks.pojos.events.AppRefreshFinishedEvent;
import com.mcochin.stockstreaks.pojos.events.InitLoadFromDbFinishedEvent;
import com.mcochin.stockstreaks.pojos.events.LoadMoreFinishedEvent;
import com.mcochin.stockstreaks.pojos.events.LoadSymbolFinishedEvent;
import com.mcochin.stockstreaks.pojos.events.WidgetRefreshDelegateEvent;
import com.mcochin.stockstreaks.services.MainService;
import com.mcochin.stockstreaks.utils.Utility;
import com.mcochin.stockstreaks.widget.StockWidgetProvider;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class ListManagerFragment extends Fragment {
    public static final String TAG = ListManagerFragment.class.getSimpleName();

    private ListManipulator mListManipulator;
    private EventListener mEventListener;

    /**
     * This is true if the list is loading a few on the bottom
     */
    private static volatile boolean mLoadingMore;

    public interface EventListener {
        void onLoadFromDbFinished(InitLoadFromDbFinishedEvent event);

        void onLoadMoreFinished(LoadMoreFinishedEvent event);

        void onLoadSymbolFinished(LoadSymbolFinishedEvent event);

        void onRefreshFinished(AppRefreshFinishedEvent event);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);  // keep the fragment instance

        mListManipulator = new ListManipulator();
    }

    @Override
    public void onPause() {
        if (mListManipulator.isListUpdated()) {
            new AsyncTask<Context, Void, Void>() {
                @Override
                protected Void doInBackground(Context... params) {
                    mListManipulator.permanentlyDeleteLastRemoveItem(params[0]);
                    mListManipulator.saveShownListState(params[0]);
                    //Update widget to reflect changes
                    params[0].sendBroadcast(new Intent(StockWidgetProvider.ACTION_DATA_UPDATED));

                    return null;
                }
            }.execute(getActivity());
        }

        super.onPause();
    }

    /**
     * Initializes the list by loading data from where the user left off from the last session.
     */
    public void initFromDb() {
        // Get load list of symbols to query
        new AsyncTask<ContentResolver, Void, Void>() {
            @Override
            protected Void doInBackground(ContentResolver... params) {
                Cursor cursor = null;
                try {
                    ContentResolver cr = params[0];
                    int shownPositionBookmark = Utility.getShownPositionBookmark(cr);

                    // Query db for data up to the list position bookmark;
                    cursor = cr.query(
                            StockEntry.CONTENT_URI,
                            ListManipulator.STOCK_PROJECTION,
                            StockProvider.LIST_POSITION_SELECTION,
                            new String[]{Integer.toString(shownPositionBookmark)},
                            StockProvider.ORDER_BY_LIST_POSITION_ASC_ID_DESC);

                    // Extract Stock data from cursor
                    if (cursor != null) {
                        int cursorCount = cursor.getCount();
                        if (cursorCount > 0) {
                            mListManipulator.setShownListCursor(cursor);
                            mListManipulator.setLoadList(getLoadListFromDb(cr));
                            mListManipulator.addToLoadListPositionBookmark(cursorCount);
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                ListEventQueue.getInstance().post(new InitLoadFromDbFinishedEvent(
                        MyApplication.getInstance().getSessionId()));
            }
        }.execute(getActivity().getContentResolver());
    }

    /**
     * Initializes the list by refreshing the list.
     *
     * @param attachSymbol An option to query a symbol once the list has done refreshing.
     */
    public void initFromRefresh(final String attachSymbol) {
        MyApplication.getInstance().setRefreshing(true);
        // Get load list of symbols to query
        new AsyncTask<Context, Void, Void>() {
            @Override
            protected Void doInBackground(Context... params) {
                refreshList(params[0]);

                if (attachSymbol != null) {
                    loadSymbol(attachSymbol);
                }
                return null;
            }
        }.execute(getActivity());
    }

    /**
     * Refreshes the list. Should be called from a background thread.
     *
     * @param context
     */
    private void refreshList(Context context) {
        if (mListManipulator.isListUpdated()) {
            mListManipulator.saveShownListState(context);
        }
        // Start service to refresh app
        Intent serviceIntent = new Intent(getActivity(), MainService.class);
        serviceIntent.setAction(MainService.ACTION_APP_REFRESH);
        getActivity().startService(serviceIntent);
    }

    /**
     * Starts a service to perform a network request to load data for the specified symbol.
     *
     * @param symbol
     */
    public void loadSymbol(String symbol) {
        // Start service to retrieve stock info
        Intent serviceIntent = new Intent(getActivity(), MainService.class);
        serviceIntent.putExtra(MainService.KEY_LOAD_SYMBOL_QUERY, symbol);
        serviceIntent.setAction(MainService.ACTION_LOAD_SYMBOL);
        getActivity().startService(serviceIntent);
    }

    /**
     * Loads the next few symbols in the user's list.
     */
    public void loadMore() {
        String[] moreSymbols = mListManipulator.getMoreToLoad();

        if (moreSymbols != null) {
            mLoadingMore = true;

            // Start service to load a few
            Intent serviceIntent = new Intent(getActivity(), MainService.class);
            serviceIntent.setAction(MainService.ACTION_LOAD_MORE);
            serviceIntent.putExtra(MainService.KEY_LOAD_MORE_QUERY, moreSymbols);
            getActivity().startService(serviceIntent);

        } else {
            mLoadingMore = false;
        }
    }

    /**
     * Should be called from a background thread.
     *
     * @return a list of ALL the symbols from the db. This will serve as our load list.
     */
    private String[] getLoadListFromDb(ContentResolver cr) {
        Cursor cursor = null;
        String[] loadList = null;
        try {
            final String[] projection = new String[]{StockEntry.COLUMN_SYMBOL};
            final int indexSymbol = 0;

            // Query db for just symbols.
            cursor = cr.query(
                    StockEntry.CONTENT_URI,
                    projection,
                    null,
                    null,
                    StockProvider.ORDER_BY_LIST_POSITION_ASC_ID_DESC);

            // Grab symbols from cursor and put them in array
            if (cursor != null) {
                int cursorCount = cursor.getCount();
                loadList = new String[cursorCount];

                for (int i = 0; i < cursorCount; i++) {
                    cursor.moveToPosition(i);
                    loadList[i] = cursor.getString(indexSymbol);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return loadList;
    }

    /**
     * A callback for the when loading from db as initialization of the stock list has finished.
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.POSTING)
    public void onInitLoadFromDbFinished(InitLoadFromDbFinishedEvent event) {
        EventBus.getDefault().removeStickyEvent(InitLoadFromDbFinishedEvent.class);

        if (mEventListener != null) {
            mEventListener.onLoadFromDbFinished(event);
        }
    }

    /**
     * A callback for the when a symbol has finished loading from the network.
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLoadSymbolFinished(final LoadSymbolFinishedEvent event) {
        EventBus.getDefault().removeStickyEvent(LoadSymbolFinishedEvent.class);
        // We use async task for the benefit of them executing sequentially in a single
        // background thread. And in order to prevent using the synchronized keyword in the main
        // thread which may block it.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (event.isSuccessful()) {
                    mListManipulator.addItemToTop(event.getStock());
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (mEventListener != null) {
                    mEventListener.onLoadSymbolFinished(event);
                }
            }
        }.execute();
    }

    /**
     * A callback for the when the "more" symbols have finished dynamically loaded from the network.
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onLoadMoreFinished(final LoadMoreFinishedEvent event) {
        if (!event.getSessionId().equals(MyApplication.getInstance().getSessionId())) {
            return;
        }
        EventBus.getDefault().removeStickyEvent(LoadMoreFinishedEvent.class);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if (event.isSuccessful()) {
                    //Add on bottom but before the loading item
                    for (Stock stock : event.getStockList()) {
                        mListManipulator.addItemToPosition(mListManipulator.getCount() - 1, stock);
                    }
                    // Remove loading item if it exists
                    mListManipulator.removeLoadingItem();
                }
                mLoadingMore = false;
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (mEventListener != null) {
                    mEventListener.onLoadMoreFinished(event);
                }
            }
        }.execute();
    }

    /**
     * A callback for the when the app has finished refreshing the list.
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAppRefreshFinished(final AppRefreshFinishedEvent event) {
        EventBus.getDefault().removeStickyEvent(AppRefreshFinishedEvent.class);

        new AsyncTask<ContentResolver, Void, Void>() {
            @Override
            protected Void doInBackground(ContentResolver... params) {
                if (event.isSuccessful()) {
                    mListManipulator.setShownListCursor(null);
                    // Give list to ListManipulator
                    mListManipulator.setLoadList(getLoadListFromDb(params[0]));

                    for (Stock stock : event.getStockList()) {
                        mListManipulator.addItemToBottom(stock);
                    }
                }
                MyApplication.getInstance().setRefreshing(false);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (mEventListener != null) {
                    mEventListener.onRefreshFinished(event);
                }
            }
        }.execute(getActivity().getContentResolver());
    }

    /**
     * A callback for the when the the widget wants the app to refresh the list instead of itself.
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWidgetRefreshDelegate(final WidgetRefreshDelegateEvent event) {
        EventBus.getDefault().removeStickyEvent(WidgetRefreshDelegateEvent.class);

        new AsyncTask<Context, Void, Void>() {
            @Override
            protected Void doInBackground(Context... params) {
                refreshList(params[0]);
                return null;
            }
        }.execute(getActivity());
    }

    public void setEventListener(EventListener eventListener) {
        mEventListener = eventListener;
    }

    public ListManipulator getListManipulator() {
        return mListManipulator;
    }

    /**
     * @return true if the app is currently loading "more", false otherwise.
     */
    public boolean isLoadingMore() {
        return mLoadingMore;
    }
}
