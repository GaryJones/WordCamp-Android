package org.wordcamp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.loopj.android.http.JsonHttpResponseHandler;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.wordcamp.adapters.WCDetailAdapter;
import org.wordcamp.db.DBCommunicator;
import org.wordcamp.networking.WPAPIClient;
import org.wordcamp.objects.WordCampDB;
import org.wordcamp.objects.session.Session;
import org.wordcamp.objects.speakersnew.SpeakerNew;
import org.wordcamp.objects.wordcamp.WordCamps;
import org.wordcamp.utils.ImageUtils;
import org.wordcamp.utils.WordCampUtils;
import org.wordcamp.wcdetails.SessionsFragment;
import org.wordcamp.wcdetails.SpeakerFragment;
import org.wordcamp.wcdetails.WordCampOverview;
import org.wordcamp.widgets.SlidingTabLayout;

import java.lang.reflect.Field;

/**
 * Created by aagam on 26/1/15.
 */
public class WordCampDetailActivity extends AppCompatActivity implements SessionsFragment.SessionFragmentListener,
        SpeakerFragment.SpeakerFragmentListener {

    public WCDetailAdapter adapter;
    public Toolbar toolbar;
    public ViewPager mPager;
    public WordCampDB wcdb;
    public int wcid;
    public DBCommunicator communicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wcdb = (WordCampDB) getIntent().getSerializableExtra("wc");
        wcid = wcdb.getWc_id();
        setContentView(R.layout.activity_wordcamp_detail);
        communicator = new DBCommunicator(this);
        communicator.start();
        initGUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        communicator.close();
    }

    private void initGUI() {
        ViewCompat.setElevation(findViewById(R.id.header), getResources().getDimension(R.dimen.toolbar_elevation));
        toolbar = (Toolbar) findViewById(R.id.toolbar);

        adapter = new WCDetailAdapter(getSupportFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(adapter);
        mPager.setOffscreenPageLimit(2);
        final int tabHeight = getResources().getDimensionPixelSize(R.dimen.tab_height);
        findViewById(R.id.pager_wrapper).setPadding(0, ImageUtils.getActionBarSize(this) + tabHeight, 0, 0);

        SlidingTabLayout slidingTabLayout = (SlidingTabLayout) findViewById(R.id.sliding_tabs);
        slidingTabLayout.setCustomTabView(R.layout.tab_view, android.R.id.text1);

        slidingTabLayout.setSelectedIndicatorColors(getResources().getColor(R.color.accent));
        slidingTabLayout.setDistributeEvenly(true);
        slidingTabLayout.setViewPager(mPager);

        toolbar.setTitle(wcdb.getWc_title());
        setToolBarEllisize();
        toolbar.setSubtitle(WordCampUtils.getProperDate(wcdb));
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    }

    private void setToolBarEllisize() {
        TextView titleTextView = null;

        try {
            Field f = toolbar.getClass().getDeclaredField("mTitleTextView");
            f.setAccessible(true);
            titleTextView = (TextView) f.get(toolbar);
            titleTextView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_attending:
                if (!wcdb.isMyWC) {
                    int recv = communicator.addToMyWC(wcid);
                    item.setIcon(R.drawable.ic_star_white_36dp);
                    wcdb.isMyWC = true;
                } else {
                    communicator.removeFromMyWCSingle(wcid);
                    item.setIcon(R.drawable.ic_star_outline_white_36dp);
                    wcdb.isMyWC = false;
                }
                break;
            case R.id.action_refresh:
                updateWordCampData();
                break;
            case android.R.id.home:
                finish();
                break;
            case R.id.item_menu_website:
                startWebIntent();
                break;
        }

        return true;
    }

    private void startWebIntent() {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(wcdb.getUrl()));
        browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(browserIntent);
    }

    private void updateWordCampData() {
        String webURL = wcdb.getUrl();

        fetchSpeakersAPI(webURL);
        getSessionsFragment().startRefreshSession();
//        fetchSessionsAPI(webURL);
//        fetchOverviewAPI();
    }

    private void fetchOverviewAPI() {
        WPAPIClient.getSingleWC(wcid, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
                super.onSuccess(statusCode, headers, response);
                Gson g = new Gson();
                WordCamps wc = g.fromJson(response.toString(), WordCamps.class);
                communicator.updateWC(wc);

                WordCampOverview overview = getOverViewFragment();
                if (overview != null) {
                    overview.updateData(wc);
                }
            }


            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                //Don't know why response is received here sometimes

                if (errorResponse != null) {
                    Gson g = new Gson();
                    WordCamps wc = g.fromJson(errorResponse.toString(), WordCamps.class);
                    communicator.updateWC(wc);

                    WordCampOverview overview = getOverViewFragment();
                    if (overview != null) {
                        overview.updateData(wc);
                    }
                }
                Toast.makeText(getApplicationContext(), "Updated overview", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchSessionsAPI(String webURL) {
        WPAPIClient.getWordCampSchedule(webURL, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                super.onSuccess(statusCode, headers, response);
                Gson gson = new Gson();
                for (int i = 0; i < response.length(); i++) {
                    try {
                        org.wordcamp.objects.speakersnew.Session session = gson.fromJson(response.getJSONObject(i).toString(), org.wordcamp.objects.speakersnew.Session.class);
                        if (communicator != null) {
                            communicator.addSession(session, wcid);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                Toast.makeText(getApplicationContext(), "Updated sessions "/* + response.length()*/, Toast.LENGTH_SHORT).show();
                stopRefreshSession();
                if (response.length() > 0) {
                    updateSessionContent();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                stopRefreshSession();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                stopRefreshSession();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                super.onFailure(statusCode, headers, responseString, throwable);
                stopRefreshSession();
            }
        });
    }

    private void stopRefreshSession() {
        SessionsFragment fragment = getSessionsFragment();
        if (fragment != null) {
            fragment.stopRefreshSession();
        }
    }

    private void stopRefreshSpeaker() {
        SpeakerFragment fragment = getSpeakerFragment();
        if (fragment != null) {
            fragment.stopRefreshSpeaker();
        }

        updateSessionContent();
    }

    private void updateSessionContent() {
        SessionsFragment fragment = getSessionsFragment();
        if (fragment != null) {
            fragment.updateData();
        }
    }

    private void fetchSpeakersAPI(String webURL) {
        WPAPIClient.getWordCampSpeakers(webURL, new JsonHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                super.onSuccess(statusCode, headers, response);
                try {
                    addUpdateSpeakers(response);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                stopRefreshSpeaker();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                super.onFailure(statusCode, headers, throwable, errorResponse);
                stopRefreshSpeaker();
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                super.onFailure(statusCode, headers, responseString, throwable);
                stopRefreshSpeaker();
            }
        });
    }

    public void addUpdateSpeakers(JSONArray array) throws JSONException {
        Gson gson = new Gson();
        for (int i = 0; i < array.length(); i++) {
            SpeakerNew skn = gson.fromJson(array.getJSONObject(i).toString(), SpeakerNew.class);
            communicator.addSpeaker(skn, wcid);
        }

        if (array.length() > 0) {
            SpeakerFragment fragment = getSpeakerFragment();
            if (fragment != null) {
                fragment.updateSpeakers(communicator.getAllSpeakers(wcid));
            }
        }
        Toast.makeText(getApplicationContext(), "Updated speakers", Toast.LENGTH_SHORT).show();
        stopRefreshSpeaker();
    }

    public SpeakerFragment getSpeakerFragment() {
        return (SpeakerFragment) adapter.getItemAt(2);
    }

    public SessionsFragment getSessionsFragment() {
        return (SessionsFragment) adapter.getItemAt(1);
    }

    public WordCampOverview getOverViewFragment() {
        return (WordCampOverview) adapter.getItemAt(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_wc_detail, menu);
        if (wcdb.isMyWC) {
            MenuItem attending = menu.findItem(R.id.action_attending);
            attending.setIcon(R.drawable.ic_star_white_36dp);
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (communicator == null) {
            communicator = new DBCommunicator(this);
        } else {
            communicator.restart();
            updateSessionContent();
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        if (communicator != null)
            communicator.close();
    }

    @Override
    public void startRefreshSessions() {
        //Even we are refreshing sessions,
        // we will fetch Speakers as we get Sessions from there

        if(getSpeakerFragment()!=null){
            getSpeakerFragment().startRefreshSession();
        }
    }

    @Override
    public void startRefreshSpeakers() {
        String webURL = wcdb.getUrl();
        fetchSessionsAPI(webURL);
        fetchSpeakersAPI(webURL);
        getSessionsFragment().startRefreshingBar();
    }
}
