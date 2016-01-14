package com.mcochin.stockstreaks;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import com.mcochin.stockstreaks.data.StockContract;
import com.mcochin.stockstreaks.fragments.DetailFragment;

/**
 * Activity for the phone to show the details of a stock.
 */
public class DetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        if(savedInstanceState == null) {
            Uri detailUri = getIntent().getData();

            // Activity launched with pending intent always gives a null savedInstanceState?
            // When we click "LOAD MORE" in widget it will be a null uri
            if(detailUri == null){
                finish();
            }
            insertFragmentIntoDetailContainer(detailUri);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Checks to see if app is opened from widget
        Uri detailUri = intent.getData();
        if(detailUri != null){
            insertFragmentIntoDetailContainer(detailUri);
        }
    }

    private void insertFragmentIntoDetailContainer(Uri detailUri){
        DetailFragment detailFragment = new DetailFragment();
        Bundle args = new Bundle();
        args.putParcelable(DetailFragment.KEY_DETAIL_URI, detailUri);
        detailFragment.setArguments(args);

        getSupportFragmentManager().beginTransaction()
                .add(R.id.detail_container, detailFragment, DetailFragment.TAG)
                .commit();
    }
}
