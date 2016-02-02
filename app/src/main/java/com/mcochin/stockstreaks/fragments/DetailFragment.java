package com.mcochin.stockstreaks.fragments;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.mcochin.stockstreaks.R;
import com.mcochin.stockstreaks.data.StockContract;
import com.mcochin.stockstreaks.data.StockContract.StockEntry;
import com.mcochin.stockstreaks.pojos.events.LoadDetailFinishedEvent;
import com.mcochin.stockstreaks.services.DetailService;
import com.mcochin.stockstreaks.utils.Utility;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.greenrobot.event.EventBus;

/**
 * Fragment that contains more details of the list items in the main list.
 */
public class DetailFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor>, View.OnClickListener {
    public static final String TAG = DetailFragment.class.getSimpleName();

    public static final int ID_LOADER_DETAILS = 2;
    public static final String KEY_REPLY_BUTTON_VISIBLE = "replyButtonVisible";
    public static final String KEY_IS_DETAIL_REQUEST_LOADING= "isDetailRequestLoading";
    public static final String KEY_DETAIL_URI = "detailUri";


    public static final String[] DETAIL_PROJECTION = new String[]{
            StockEntry.COLUMN_SYMBOL,
            StockEntry.COLUMN_FULL_NAME,
            StockEntry.COLUMN_RECENT_CLOSE,
            StockEntry.COLUMN_CHANGE_DOLLAR,
            StockEntry.COLUMN_CHANGE_PERCENT,
            StockEntry.COLUMN_STREAK,
            StockEntry.COLUMN_PREV_STREAK,
            StockEntry.COLUMN_PREV_STREAK_END_PRICE,
            StockEntry.COLUMN_STREAK_YEAR_HIGH,
            StockEntry.COLUMN_STREAK_YEAR_LOW
    };

    //index must match projection
    public static final int INDEX_SYMBOL = 0;
    public static final int INDEX_FULL_NAME = 1;
    public static final int INDEX_RECENT_CLOSE = 2;
    public static final int INDEX_CHANGE_DOLLAR = 3;
    public static final int INDEX_CHANGE_PERCENT = 4;
    public static final int INDEX_STREAK = 5;
    public static final int INDEX_PREV_STREAK = 6;
    public static final int INDEX_PREV_STREAK_END_PRICE = 7;
    public static final int INDEX_STREAK_YEAR_HIGH = 8;
    public static final int INDEX_STREAK_YEAR_LOW = 9;

    private View mProgressWheel;
    private View mRetryButton;
    private View mExtrasInfo;

    private TextView mTextUpdateTime;
    private TextView mTextSymbol;
    private TextView mTextFullName;
    private TextView mTextRecentClose;
    private TextView mTextChangeDollar;
    private TextView mTextChangePercent;
    private TextView mTextStreak;
    private TextView mTextPrevStreakEndPrice;
    private TextView mTextPrevStreak;
    private TextView mTextStreakYearHigh;
    private TextView mTextStreakYearLow;
    private ImageView mImageStreakArrow;
    private ImageView mImagePrevStreakArrow;

    private Uri mDetailUri;
    private boolean mReplyButtonVisible;
    private boolean mIsDetailRequestLoading;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail_ref, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        mDetailUri = args.getParcelable(KEY_DETAIL_URI);

        if(savedInstanceState != null){
            mReplyButtonVisible = savedInstanceState.getBoolean(KEY_REPLY_BUTTON_VISIBLE);
            mIsDetailRequestLoading = savedInstanceState.getBoolean(KEY_IS_DETAIL_REQUEST_LOADING);
        }

        AppCompatActivity activity = (AppCompatActivity) getActivity();
        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        activity.setSupportActionBar(toolbar);
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            if(getResources().getBoolean(R.bool.is_phone)) {
                actionBar.setDisplayShowTitleEnabled(false);
                actionBar.setDisplayHomeAsUpEnabled(true);
            }else{
                toolbar.setVisibility(View.GONE);
            }
        }
        mTextUpdateTime = (TextView)view.findViewById(R.id.text_update_time);
        mTextSymbol = (TextView)view.findViewById(R.id.text_symbol);
        mTextFullName = (TextView)view.findViewById(R.id.text_full_name);
        mTextRecentClose = (TextView)view.findViewById(R.id.text_recent_close);
        mTextChangeDollar = (TextView)view.findViewById(R.id.text_change_dollar);
        mTextChangePercent = (TextView)view.findViewById(R.id.text_change_percent);
        mTextStreak = (TextView)view.findViewById(R.id.text_streak);
        mTextPrevStreak = (TextView)view.findViewById(R.id.text_streak_prev);
        mTextPrevStreakEndPrice = (TextView)view.findViewById(R.id.text_prev_streak_end_price);
        mTextStreakYearHigh = (TextView)view.findViewById(R.id.text_streak_year_high);
        mTextStreakYearLow = (TextView)view.findViewById(R.id.text_streak_year_low);
        mImageStreakArrow = (ImageView)view.findViewById(R.id.image_streak_arrow);
        mImagePrevStreakArrow = (ImageView)view.findViewById(R.id.image_prev_streak_arrow);

        mExtrasInfo = view.findViewById(R.id.detail_extras_info);
        mProgressWheel = view.findViewById(R.id.progress_wheel);
        mRetryButton = view.findViewById(R.id.button_retry);
        mRetryButton.setOnClickListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus eventBus = EventBus.getDefault();
        eventBus.registerSticky(this);

        if(mTextPrevStreak.getText().toString().isEmpty()) {
            fetchDetailsData();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id){
            case R.id.button_retry:
                mReplyButtonVisible = false;
                startDetailService();
                break;
        }
    }

    /**
     * Fetches the detail data from the db of the selected stock using a cursor loader.
     */
    private void fetchDetailsData(){
        showProgressWheel();

        LoaderManager loaderManager = getActivity().getSupportLoaderManager();
        loaderManager.restartLoader(ID_LOADER_DETAILS, null, this);
    }

    /**
     * Starts the a {@link DetailService} to perform a network request to retrieve the symbol's
     * history.
     */
    private void startDetailService(){
        mIsDetailRequestLoading = true;
        showProgressWheel();

        Intent serviceIntent = new Intent(getActivity(), DetailService.class);
        serviceIntent.putExtra(DetailService.KEY_DETAIL_SYMBOL,
                StockContract.getSymbolFromUri(mDetailUri));
        getActivity().startService(serviceIntent);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(getActivity(),
                mDetailUri,
                DETAIL_PROJECTION,
                null,
                null,
                null);
    }

    // This function is also guaranteed to be called prior to the release of the last data that was
    // supplied for this Loader." During onResume it is perfectly reasonable that the loader
    // releases its data and reloads during onResume. Yes, if you are seeing a behavior where the
    // loader may callback and you don't want that callback, then destroy the loader.
    // http://stackoverflow.com/questions/21031692/why-is-onloadfinished-called-again-after-fragment-resumed
    // I destroy the Loader when I finished getting the extras section, so it doesn't happen.
    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        if(data != null && data.moveToFirst()){
            String symbol = data.getString(INDEX_SYMBOL);
            String fullName = data.getString(INDEX_FULL_NAME);
            float recentClose = data.getFloat(INDEX_RECENT_CLOSE);
            float changeDollar = data.getFloat(INDEX_CHANGE_DOLLAR);
            float changePercent = data.getFloat(INDEX_CHANGE_PERCENT);
            int streak = data.getInt(INDEX_STREAK);
            int prevStreak = data.getInt(INDEX_PREV_STREAK);
            float prevStreakEndPrice = data.getFloat(INDEX_PREV_STREAK_END_PRICE);
            int streakYearHigh = data.getInt(INDEX_STREAK_YEAR_HIGH);
            int streakYearLow = data.getInt(INDEX_STREAK_YEAR_LOW);

            // Set update time
            Date lastUpdate = Utility.getLastUpdateTime(
                    getActivity().getContentResolver()).getTime();
            SimpleDateFormat sdf = new SimpleDateFormat(
                    getString(R.string.update_time_format_wide), Locale.US);
            String lastUpdateString = getString(R.string.placeholder_update_time,
                    sdf.format(lastUpdate));

            // Main Section
            // Add check here so when the service returns from calculating the prev streak info
            // it wont have to load main section again.
            if(!mTextUpdateTime.getText().toString().equals(lastUpdateString)) {
                mTextUpdateTime.setText(getString(R.string.placeholder_update_time,
                        sdf.format(lastUpdate)));

                mTextSymbol.setText(symbol);
                mTextFullName.setText(fullName);
                mTextRecentClose.setText(getString(R.string.placeholder_dollar,
                        Utility.roundTo2StringDecimals(recentClose)));
                mTextStreak.setText(getString(Math.abs(streak) == 1 ?
                        R.string.placeholder_day : R.string.placeholder_days, streak));

                // Get our dollar/percent change colors and set our stock arrow ImageView
                //Determine the color and the arrow image of the changes
                Pair<Integer, Integer> changeColorAndDrawableIds =
                        Utility.getChangeColorAndArrowDrawableIds(changeDollar);
                int color = ContextCompat.getColor(getActivity(), changeColorAndDrawableIds.first);
                mImageStreakArrow.setBackgroundResource(changeColorAndDrawableIds.second);

                mTextChangeDollar.setText(getString(R.string.placeholder_dollar,
                        Utility.roundTo2StringDecimals(changeDollar)));
                mTextChangePercent.setText(getString(R.string.placeholder_percent,
                        Utility.roundTo2StringDecimals(changePercent)));
                mTextChangeDollar.setTextColor(color);
                mTextChangePercent.setTextColor(color);
            }

            // Extras Section
            if(prevStreak != 0) {
                mIsDetailRequestLoading = false;

                mTextPrevStreak.setText(getString(Math.abs(prevStreak) == 1 ?
                        R.string.placeholder_day : R.string.placeholder_days, prevStreak));

                mTextPrevStreakEndPrice.setText(getString(R.string.placeholder_dollar,
                        Utility.roundTo2StringDecimals(prevStreakEndPrice)));

                mTextStreakYearHigh.setText(getString(Math.abs(streakYearHigh) == 1 ?
                        R.string.placeholder_day : R.string.placeholder_days, streakYearHigh));

                mTextStreakYearLow.setText(getString(Math.abs(streakYearLow) == 1 ?
                        R.string.placeholder_day : R.string.placeholder_days, streakYearLow));

                if (prevStreak > 0) {
                    mImagePrevStreakArrow.setBackgroundResource(R.drawable.ic_streak_up);
                } else if (prevStreak < 0) {
                    mImagePrevStreakArrow.setBackgroundResource(R.drawable.ic_streak_down);
                }
                showExtrasInfo();
                getActivity().getSupportLoaderManager().destroyLoader(ID_LOADER_DETAILS);

            }else if(mReplyButtonVisible){
                showRetryButton();

            }else if(!mIsDetailRequestLoading){
                startDetailService();
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    public void onEventMainThread(LoadDetailFinishedEvent event){
        // Make sure we don't process the event of another stock symbol. This can happen is we
        // switch to a different DetailFragment while the prev one is still loading.
        if(event.getSymbol().equals(getSymbol())) {
            mIsDetailRequestLoading = false;

            if(!event.isSuccessful()) {
                showRetryButton();
            }else{
                showExtrasInfo();
            }
        }
        EventBus.getDefault().removeStickyEvent(LoadDetailFinishedEvent.class);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_REPLY_BUTTON_VISIBLE, mReplyButtonVisible);
        outState.putBoolean(KEY_IS_DETAIL_REQUEST_LOADING, mIsDetailRequestLoading);
        super.onSaveInstanceState(outState);
    }

    private void showProgressWheel(){
        mProgressWheel.setVisibility(View.VISIBLE);
        mRetryButton.setVisibility(View.INVISIBLE);
        mExtrasInfo.setVisibility(View.INVISIBLE);
    }

    private void showRetryButton(){
        mReplyButtonVisible = true;
        mProgressWheel.setVisibility(View.INVISIBLE);
        mRetryButton.setVisibility(View.VISIBLE);
        mExtrasInfo.setVisibility(View.INVISIBLE);
    }

    private void showExtrasInfo(){
        mProgressWheel.setVisibility(View.INVISIBLE);
        mRetryButton.setVisibility(View.INVISIBLE);
        mExtrasInfo.setVisibility(View.VISIBLE);
    }

    public String getSymbol(){
        return StockContract.getSymbolFromUri(mDetailUri);
    }
}
