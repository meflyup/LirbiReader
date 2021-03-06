package com.foobnix.ui2;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import com.foobnix.android.utils.LOG;
import com.foobnix.dao2.FileMeta;
import com.foobnix.ext.EbookMeta;
import com.foobnix.pdf.info.AppSharedPreferences;
import com.foobnix.pdf.info.ExtUtils;
import com.foobnix.pdf.info.IMG;
import com.foobnix.pdf.info.io.SearchCore;
import com.foobnix.pdf.info.wrapper.AppState;
import com.foobnix.sys.ImageExtractor;
import com.foobnix.ui2.adapter.FileMetaAdapter;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.media.session.MediaSessionCompat;

public class BooksService extends IntentService {
    private MediaSessionCompat mediaSessionCompat;

    public BooksService() {
        super("BooksService");
        AppState.get().load(this);
        handler = new Handler();

    }

    Handler handler;

    public static String INTENT_NAME = "BooksServiceIntent";
    public static String ACTION_SEARCH_ALL = "ACTION_SEARCH_ALL";

    public static String RESULT_SEARCH_FINISH = "RESULT_SEARCH_FINISH";
    public static String RESULT_BUILD_LIBRARY = "RESULT_BUILD_LIBRARY";
    public static String RESULT_SEARCH_COUNT = "RESULT_SEARCH_COUNT";

    private List<FileMeta> itemsMeta = new LinkedList<FileMeta>();

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (ACTION_SEARCH_ALL.equals(intent.getAction())) {
            LOG.d(ACTION_SEARCH_ALL);

            IMG.clearDiscCache();
            IMG.clearMemoryCache();
            ImageExtractor.clearErrors();

            List<Uri> recent = AppSharedPreferences.get().getRecent();
            List<FileMeta> starsAndRecent = AppDB.get().deleteAllSafe();

            long time = Integer.MAX_VALUE;
            for (Uri uri : recent) {
                FileMeta item = new FileMeta(uri.getPath());
                item.setIsRecent(true);
                item.setIsStarTime(time--);
                starsAndRecent.add(item);
            }
            for (FileMeta m : starsAndRecent) {
                if (m.getCusType() != null && FileMetaAdapter.DISPLAY_TYPE_DIRECTORY == m.getCusType()) {
                    m.setIsSearchBook(false);
                } else {
                    m.setIsSearchBook(true);
                }
            }

            AppSharedPreferences.get().cleanRecent();

            itemsMeta.clear();

            handler.post(timer);

            for (final String path : AppState.get().searchPaths.split(",")) {
                if (path != null && path.trim().length() > 0) {
                    final File root = new File(path);
                    if (root.isDirectory()) {
                        LOG.d("Searcin in " + root.getPath());
                        SearchCore.search(itemsMeta, root, ExtUtils.seachExts);
                    }
                }
            }
            for (FileMeta meta : itemsMeta) {
                meta.setIsSearchBook(true);
            }

            itemsMeta.addAll(starsAndRecent);
            AppDB.get().saveAll(itemsMeta);

            handler.removeCallbacks(timer);

            sendFinishMessage();

            handler.post(timer2);

            for (FileMeta meta : itemsMeta) {
                File file = new File(meta.getPath());
                FileMetaCore.get().upadteBasicMeta(meta, file);
            }

            AppDB.get().updateAll(itemsMeta);

            for (FileMeta meta : itemsMeta) {
                EbookMeta ebookMeta = FileMetaCore.get().getEbookMeta(meta.getPath());
                FileMetaCore.get().udpateFullMeta(meta, ebookMeta);
            }

            AppDB.get().updateAll(itemsMeta);

            itemsMeta.clear();

            handler.removeCallbacks(timer2);
            sendFinishMessage();
        }

    }

    Runnable timer = new Runnable() {

        @Override
        public void run() {
            sendProggressMessage();
            handler.postDelayed(timer, 250);
        }
    };

    Runnable timer2 = new Runnable() {

        @Override
        public void run() {
            sendBuildingLibrary();
            handler.postDelayed(timer2, 250);
        }
    };

    private void sendFinishMessage() {
        Intent intent = new Intent(INTENT_NAME).putExtra(Intent.EXTRA_TEXT, RESULT_SEARCH_FINISH);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void sendProggressMessage() {
        Intent itent = new Intent(INTENT_NAME).putExtra(Intent.EXTRA_TEXT, RESULT_SEARCH_COUNT).putExtra(Intent.EXTRA_INDEX, itemsMeta.size());
        LocalBroadcastManager.getInstance(this).sendBroadcast(itent);
    }

    private void sendBuildingLibrary() {
        Intent itent = new Intent(INTENT_NAME).putExtra(Intent.EXTRA_TEXT, RESULT_BUILD_LIBRARY);
        LocalBroadcastManager.getInstance(this).sendBroadcast(itent);
    }

}
