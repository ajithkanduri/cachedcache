package sharan.com.top10downloader;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private ListView listApps;
    private String feedURL = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topfreeapplications/limit=%d/xml";
    private int feedLimit = 10;
    private String feedcachedUrl = "INVALIDATED";
    public static final String STATE_URL = "feedURL";
    public static final String STATE_LIMIT = "feedLimit";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listApps = (ListView) findViewById(R.id.xmlListView);
        if (savedInstanceState != null) {
            feedURL = savedInstanceState.getString(STATE_URL);
            feedLimit = savedInstanceState.getInt(STATE_LIMIT);
        }
        enableHttpResponseCache();

        downloadURL(String.format(feedURL, feedLimit));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.feeds_menu, menu);
        if (feedLimit == 10) {
            menu.findItem(R.id.mnu10).setChecked(true);
        } else {
            menu.findItem(R.id.mnu25).setChecked(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.mnuFree: {
                feedURL = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topfreeapplications/limit=%d/xml";
                break;
            }
            case R.id.mnuPaid: {
                feedURL = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/toppaidapplications/limit=%d/xml";
                break;
            }
            case R.id.mnuSongs: {
                feedURL = "http://ax.itunes.apple.com/WebObjects/MZStoreServices.woa/ws/RSS/topsongs/limit=%d/xml";
                break;
            }
            case R.id.mnu10:
            case R.id.mnu25: {
                if (!item.isChecked()) {
                    item.setChecked(true);
                    feedLimit = 35 - feedLimit;
                    Log.d(TAG, "onOptionsItemSelected: " + item.getTitle() + " setting feedLimit to " + feedLimit);
                } else {
                    Log.d(TAG, "onOptionsItemSelected: feedLimit is unchanged");
                }
                break;
            }
            case R.id.mnuRefresh: {
                feedcachedUrl = "INVALID";
                break;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
        downloadURL(String.format(feedURL, feedLimit));
        return true;

    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_URL, feedURL);
        outState.putInt(STATE_LIMIT, feedLimit);
        super.onSaveInstanceState(outState);
    }

    private void downloadURL(String feedURL) {
        if (!feedURL.equalsIgnoreCase(feedcachedUrl)) {
            Log.d(TAG, "downloadURL: starting Asynctask");
            DownloadData downloadData = new DownloadData();
            downloadData.execute(feedURL);
            feedcachedUrl = feedURL;
            Log.d(TAG, "downloadURL: done");
        } else {
            Log.d(TAG, "downloadURL: URL not changed");
        }
    }

    private void enableHttpResponseCache() {
        try {
            long httpCacheSize = 5 * 1024 * 1024;
            File httpCacheDir = new File(getCacheDir(), "http");
            Class.forName("android.net.http.HttpResponseCache").getMethod("install", File.class, long.class).invoke(null, httpCacheDir, httpCacheSize);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//
    private File getTempFile(Context context,String feedURL){
        File file=null;
        try{
            String fileName= Uri.parse(feedURL).getLastPathSegment();
            file=File.createTempFile(fileName,null,context.getCacheDir());
        }catch (IOException e)
        {
            e.printStackTrace();
        }
        return file;

    }


    private class DownloadData extends AsyncTask<String, Void, String> {
        private static final String TAG = "DownloadData";

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Log.d(TAG, "onPostExecute: parameter is " + s);
            ParseApplications parseApplications = new ParseApplications();
            parseApplications.parse(s);

//            ArrayAdapter<FeedEntry> arrayAdapter=new ArrayAdapter<FeedEntry>(MainActivity.this, R.layout.list_view, parseApplications.getApplications());
//            listApps.setAdapter(arrayAdapter);
            FeedAdapter feedAdapter = new FeedAdapter(MainActivity.this, R.layout.list_record, parseApplications.getApplications());
            listApps.setAdapter(feedAdapter);
        }

        @Override
        protected String doInBackground(String... strings) {
            Log.d(TAG, "doInBackground: starts with " + strings[0]);
            String rssFeed = downloadXML(strings[0]);
            if (rssFeed == null) {
                Log.e(TAG, "doInBackground: Error Downloading");
            }
            return rssFeed;
        }

        private String downloadXML(String urlpath) {
            StringBuilder xmlResult = new StringBuilder();
            try {
                URL url = new URL(urlpath);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                File cacheDir;
                BufferedReader cacheReader=null;
                Context context=MainActivity.this;
                long currentTimeMillis = System.currentTimeMillis();
                long lastUpdated=0;
                long lastModified=connection.getHeaderFieldDate("lastModified", currentTimeMillis);
                if((lastModified-lastUpdated)>90000){
                    int responsecode = connection.getResponseCode();
                    Log.d(TAG, "downloadXML: The Response Code is:" + responsecode);
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    cacheDir=getTempFile(context,urlpath);
                    cacheReader=new BufferedReader(new FileReader(cacheDir));
                    lastUpdated=connection.getHeaderFieldDate("lastUpdated",currentTimeMillis);
                    System.out.println("helpme"+lastModified+":"+lastUpdated);
                    int charsread;
                    char[] inputBuffer = new char[500];
                    while (true) {
                        charsread = bufferedReader.read(inputBuffer);

                        if (charsread < 0) {
                            break;
                        }
                        if (charsread > 0) {
                            xmlResult.append(String.copyValueOf(inputBuffer, 0, charsread));
                        }
                    }
                    bufferedReader.close();
                    return xmlResult.toString();
                }
                else {
                    Log.d(TAG, "downloadXML: Cache is being used");
                    System.out.println("helpme"+lastModified+":"+lastUpdated);

                    int charsread;
                    char[] inputBuffer = new char[500];
                    while (true) {
                        charsread = cacheReader.read(inputBuffer);

                        if (charsread < 0) {
                            break;
                        }
                        if (charsread > 0) {
                            xmlResult.append(String.copyValueOf(inputBuffer, 0, charsread));
                        }
                    }
                    cacheReader.close();
                    return xmlResult.toString();
                }

            } catch (MalformedURLException e) {
                Log.e(TAG, "downloadXML: Invalid URL" + e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, "downloadXML: IO Exception reading Data " + e.getMessage());
            } catch (SecurityException e) {
                Log.e(TAG, "downloadXML: Security Exception needs permission " + e.getMessage());
                e.printStackTrace();
            }
            return null;

        }
    }
}
