package com.foobnix.pdf.search.activity;

import java.util.List;

import org.ebookdroid.core.codec.PageLink;
import org.ebookdroid.droids.mupdf.codec.TextWord;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import com.foobnix.android.utils.Dips;
import com.foobnix.android.utils.LOG;
import com.foobnix.android.utils.TxtUtils;
import com.foobnix.pdf.info.wrapper.AppState;
import com.foobnix.pdf.info.wrapper.MagicHelper;
import com.foobnix.pdf.search.activity.msg.InvalidateMessage;
import com.foobnix.pdf.search.activity.msg.MessageAutoFit;
import com.foobnix.pdf.search.activity.msg.MessageCenterHorizontally;
import com.foobnix.pdf.search.activity.msg.MessageEvent;
import com.foobnix.pdf.search.activity.msg.TextWordsMessage;
import com.foobnix.sys.ClickUtils;
import com.foobnix.sys.TempHolder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Scroller;

public class PageImaveView extends View {

    private Drawable imageDrawable;
    int drawableHeight, drawableWidth;
    GestureDetector gestureDetector;
    private Handler handler;
    Scroller scroller;
    ImageSimpleGestureListener imageGestureListener;

    Paint paintWrods = new Paint();

    private boolean isReadyForMove = false;
    private boolean isLognPress = false;
    private boolean isIgronerClick = false;

    float x, y, xInit, yInit, cx, cy, distance = 0;

    private int pageNumber;
    ClickUtils clickUtils;

    public PageImaveView(final Context context, final AttributeSet attrs) {
        super(context, attrs);

        handler = new Handler();
        scroller = new Scroller(getContext(), new AccelerateDecelerateInterpolator());
        imageGestureListener = new ImageSimpleGestureListener();
        gestureDetector = new GestureDetector(context, imageGestureListener);

        paintWrods.setColor(AppState.get().isInvert ? Color.BLUE : Color.YELLOW);
        paintWrods.setAlpha(60);
        paintWrods.setStrokeWidth(Dips.dpToPx(2));

        EventBus.getDefault().register(this);
        clickUtils = new ClickUtils();
    }

    public TextWord[][] getPageText() {
        return PageImageState.get().pagesText.get(pageNumber);
    }

    public List<PageLink> getPageLinks() {
        return PageImageState.get().pagesLinks.get(pageNumber);
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Matrix imageMatrix() {
        return PageImageState.get().getMatrix();
    }

    @Subscribe
    public void onInvalidate(InvalidateMessage event) {
        invalidate();
    }

    @Subscribe
    public void onAutoFit(MessageAutoFit event) {
        LOG.d("onAutoFit recive");
        if (pageNumber == event.getPage()) {
            LOG.d("onAutoFit recive", event.getPage(), pageNumber);
            autoFit();
            invalidate();
            isFirstZoomInOut = true;
        }
    }

    @Subscribe
    public void onCenterHorizontally(MessageCenterHorizontally event) {
        LOG.d("onCenterHorizontally recive");
        if (pageNumber == event.getPage()) {
            LOG.d("onAutoFit recive", event.getPage(), pageNumber);
            centerHorizontally();
            invalidate();
        }
    }

    @Subscribe
    public void onTextWords(TextWordsMessage event) {
        if (event.getPage() == pageNumber) {
            // pageText = event.getMessages();
            selectText(xInit, yInit, xInit, yInit);
        }
    }

    static volatile boolean isFirstZoomInOut = true;
    static volatile boolean prevLock = false;

    class ImageSimpleGestureListener extends GestureDetector.SimpleOnGestureListener {
        private final int DP_5 = Dips.dpToPx(10);

        @Override
        public boolean onDoubleTap(final MotionEvent e) {
            isIgronerClick = true;
            if (clickUtils.isClickCenter(e.getX(), e.getY())) {
                isLognPress = true;

                if (isFirstZoomInOut) {
                    // imageMatrix().reset();
                    imageMatrix().preTranslate(getWidth() / 2 - e.getX(), getHeight() / 2 - e.getY());
                    imageMatrix().postScale(2.5f, 2.5f, getWidth() / 2, getHeight() / 2);
                    isFirstZoomInOut = false;
                    prevLock = AppState.get().isLocked;
                    AppState.get().isLocked = false;
                    // invalidate();
                    invalidateAndMsg();
                    PageImageState.get().isAutoFit = false;

                } else {
                    AppState.get().isLocked = prevLock;
                    if (TempHolder.get().isTextFormat) {
                        AppState.get().isLocked = true;
                    }
                    isLognPress = true;
                    PageImageState.get().isAutoFit = true;
                    autoFit();
                    invalidateAndMsg();
                    isFirstZoomInOut = true;

                }
                EventBus.getDefault().post(new MessageEvent(MessageEvent.MESSAGE_DOUBLE_TAP, e.getX(), e.getY()));
                return true;
            }

            return true;
        };

        @Override
        public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
            if (AppState.get().selectedText != null) {
                return false;
            }
            if (AppState.get().isLocked) {
                return false;
            }
            if (isReadyForMove) {
                scroller.fling((int) e2.getX(), (int) e2.getY(), (int) velocityX / 3, (int) velocityY / 3, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
                handler.post(scrolling);
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (!AppState.get().longTapEnable) {
                return;
            }
            isLognPress = true;
            xInit = e.getX();
            yInit = e.getY();
            String selectText = selectText(xInit, yInit, e.getX(), e.getY());
            if (TxtUtils.isNotEmpty(selectText)) {
                if (AppState.get().isVibration) {
                    Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(50);
                }
            } else {
                AppState.get().selectedText = null;
            }
        }

        public boolean onTouchEvent(final MotionEvent event) {
            final int action = event.getAction() & MotionEvent.ACTION_MASK;

            if (action == MotionEvent.ACTION_DOWN) {
                AppState.get().selectedText = null;
                LOG.d("TEST", "action ACTION_DOWN");
                scroller.forceFinished(true);
                x = event.getX();
                y = event.getY();
                isReadyForMove = false;
                isLognPress = false;
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (event.getPointerCount() == 1) {
                    LOG.d("TEST", "action ACTION_MOVE 1");
                    final float dx = event.getX() - x;
                    final float dy = event.getY() - y;

                    if (isLognPress) {
                        selectText(event.getX(), event.getY(), xInit, yInit);
                    } else {

                        if (AppState.get().rotateViewPager == 0) {
                            if (Math.abs(dy) > Math.abs(dx / 1.5) && (Math.abs(dy) + Math.abs(dx) > DP_5)) {
                                isReadyForMove = true;
                                isIgronerClick = true;
                            }
                        } else {
                            if (Math.abs(dx) > Math.abs(dy / 1.5) && (Math.abs(dx) + Math.abs(dy) > DP_5)) {
                                isReadyForMove = true;
                                isIgronerClick = true;
                            }
                        }

                        if (isReadyForMove && !AppState.get().isLocked) {

                            imageMatrix().postTranslate(dx, dy);

                            PageImageState.get().isAutoFit = false;
                            invalidateAndMsg();

                            x = event.getX();
                            y = event.getY();
                        }
                    }
                }

                if (event.getPointerCount() == 2) {
                    isIgronerClick = true;
                    LOG.d("TEST", "action ACTION_MOVE 2");
                    if (cx == 0) {
                        cx = centerX(event);
                        cy = centerY(event);
                    }
                    final float nDistance = discance(event);

                    if (distance == 0) {
                        distance = nDistance;
                    }

                    final float scale = nDistance / distance;
                    distance = nDistance;
                    final float centerX = centerX(event);
                    final float centerY = centerY(event);

                    final float values[] = new float[9];
                    imageMatrix().getValues(values);

                    if (!AppState.get().isLocked) {
                        LOG.d("postScale", scale, values[Matrix.MSCALE_X]);
                        if (values[Matrix.MSCALE_X] > 0.3f || scale > 1) {
                            imageMatrix().postScale(scale, scale, centerX, centerY);
                        }
                    }
                    final float dx = centerX - cx;
                    final float dy = centerY - cy;
                    if (!AppState.get().isLocked) {
                        imageMatrix().postTranslate(dx, dy);
                    }
                    cx = centerX(event);
                    cy = centerY(event);

                    PageImageState.get().isAutoFit = false;
                    invalidateAndMsg();
                }
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                LOG.d("TEST", "action ACTION_POINTER_UP");
                // isDoubleTouch = true;
                int actionIndex = 1;
                if (Build.VERSION.SDK_INT > 7) {
                    actionIndex = event.getActionIndex();
                }
                LOG.d("TEST", "actionIndex " + actionIndex);
                if (actionIndex == 1) {
                    x = event.getX();
                    y = event.getY();
                } else {
                    x = event.getX(1);
                    y = event.getY(1);
                }
                cx = 0;
                distance = 0;

            } else if (action == MotionEvent.ACTION_UP) {

                LOG.d("TEST", "action ACTION_UP", "long: " + isLognPress);
                distance = 0;
                isReadyForMove = false;
                cx = 0;
                cy = 0;

                if (isLognPress) {
                    selectText(event.getX(), event.getY(), xInit, yInit);
                } else if (TempHolder.get().isTextFormat) {
                    selectText(event.getX(), event.getY(), event.getX(), event.getY());
                    if (!TxtUtils.isFooterNote(AppState.get().selectedText)) {
                        PageImageState.get().cleanSelectedWords();
                        AppState.get().selectedText = null;
                        invalidate();
                    }
                }

                PageLink pageLink = getPageLink(event.getX(), event.getY());
                if (pageLink != null) {
                    TempHolder.get().linkPage = pageLink.targetPage;
                }

                if (TxtUtils.isNotEmpty(AppState.get().selectedText)) {
                    EventBus.getDefault().post(new MessageEvent(MessageEvent.MESSAGE_SELECTED_TEXT));
                } else if (pageLink != null) {
                    EventBus.getDefault().post(new MessageEvent(MessageEvent.MESSAGE_GOTO_PAGE, pageLink.targetPage, pageLink.url));
                } else {
                    if (!isIgronerClick) {
                        EventBus.getDefault().post(new MessageEvent(MessageEvent.MESSAGE_PERFORM_CLICK, event.getX(), event.getY()));
                    }
                }
                isIgronerClick = false;
            } else if (action == MotionEvent.ACTION_CANCEL) {
                LOG.d("TEST", "action ACTION_CANCEL");
            }

            return true;
        }

    };

    public void invalidateAndMsg() {
        EventBus.getDefault().post(new InvalidateMessage());
    };

    Runnable scrolling = new Runnable() {

        @Override
        public void run() {
            if (scroller.isFinished()) {
                return;
            }
            final boolean more = scroller.computeScrollOffset();
            final int xx = scroller.getCurrX();
            final int yy = scroller.getCurrY();

            final float dx = xx - x;
            final float dy = yy - y;

            imageMatrix().postTranslate(dx, dy);
            y = yy;
            x = xx;
            invalidate();

            if (more) {
                handler.post(scrolling);
            }

        }
    };
    private Bitmap bitmap;

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LOG.d("ImagePageFragment", "onDetachedFromWindow");
        if (bitmap != null && !bitmap.isRecycled()) {
            LOG.d("recycle onDetachedFromWindow");
            bitmap.recycle();
            bitmap = null;
        }
        imageDrawable = null;
        EventBus.getDefault().unregister(this);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        imageGestureListener.onTouchEvent(event);
        return true;
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        try {
            canvas.drawColor(MagicHelper.ligtherColor(MagicHelper.getBgColor()));

            final int saveCount = canvas.getSaveCount();
            canvas.save();

            canvas.concat(imageMatrix());

            if (imageDrawable != null) {
                imageDrawable.draw(canvas);
            }

            if (PageImageState.get().isShowCuttingLine && AppState.get().isCut == false) {
                int offset = drawableWidth * AppState.get().cutP / 100;
                canvas.drawLine(offset, 0, offset, drawableHeight, paintWrods);
            }

            List<TextWord> selectedWords = PageImageState.get().getSelectedWords(pageNumber);
            if (selectedWords != null) {
                for (TextWord tw : selectedWords) {
                    drawWord(canvas, tw);
                }
            }

            canvas.restoreToCount(saveCount);
        } catch (Exception e) {
            LOG.e(e);
        }
    }

    public void addBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        imageDrawable = new BitmapDrawable(getResources(), bitmap);
        drawableHeight = bitmap.getHeight();
        drawableWidth = bitmap.getWidth();
        imageDrawable.setBounds(0, 0, drawableWidth, drawableHeight);

        autoFit();
        invalidate();
    }

    public void centerHorizontally() {
        float[] f = new float[9];
        imageMatrix().getValues(f);
        float y = f[Matrix.MTRANS_Y];
        imageMatrix().setTranslate((getWidth() - drawableWidth) / 2, y);
    }

    public void autoFit() {
        if (!PageImageState.get().isAutoFit) {
            return;
        }

        final int w = getWidth();
        final int h = getHeight();
        final float scaleH = (float) h / drawableHeight;
        final float scaleW = (float) w / drawableWidth;

        imageMatrix().reset();
        if (scaleH < scaleW) {
            LOG.d("image pre scale scaleH", scaleH);
            imageMatrix().preScale(scaleH, scaleH);
            imageMatrix().postTranslate(Math.abs(getWidth() - drawableWidth * scaleH) / 2, 0);
        } else {
            LOG.d("image pre scale scaleW", scaleW);
            imageMatrix().preScale(scaleW, scaleW);
            imageMatrix().postTranslate(0, Math.abs(getHeight() - drawableHeight * scaleW) / 2);
        }
    }

    private float centerX(final MotionEvent event) {
        return (event.getX() + event.getX(1)) / 2;
    }

    private float centerY(final MotionEvent event) {
        return (event.getY() + event.getY(1)) / 2;
    }

    private float discance(final MotionEvent event) {
        final float x1 = event.getX();
        final float y1 = event.getY();

        final float x2 = event.getX(1);
        final float y2 = event.getY(1);

        return (float) Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    public float getMyScale() {
        final int w = getWidth();
        final int h = getHeight();

        LOG.d("WxH", w, h);
        LOG.d("image WxH", drawableWidth, drawableHeight);

        final float scaleH = (float) h / drawableHeight;
        final float scaleW = (float) w / drawableWidth;
        float min = Math.min(scaleH, scaleW);
        LOG.d("scale min", min, scaleH, scaleW);
        return min;

    }

    public RectF transform(RectF origin) {
        RectF r = new RectF(origin);
        r.left = r.left * drawableWidth;
        r.right = r.right * drawableWidth;

        r.top = r.top * drawableHeight;
        r.bottom = r.bottom * drawableHeight;
        return r;
    }

    public void drawWord(Canvas c, TextWord t) {
        RectF o = transform(t);
        c.drawRect(o, paintWrods);
    }

    public static int MIN = Dips.dpToPx(15);

    public PageLink getPageLink(float x1, float y1) {
        List<PageLink> pageLinks = getPageLinks();
        if (pageLinks == null) {
            return null;
        }

        RectF tr = new RectF();
        imageMatrix().mapRect(tr);

        float x = x1 - tr.left;
        float y = y1 - tr.top;

        float[] f = new float[9];
        imageMatrix().getValues(f);

        float scaleX = f[Matrix.MSCALE_X];

        x = x / scaleX;
        y = y / scaleX;

        RectF tapRect = new RectF(x, y, x, y);

        for (PageLink link : pageLinks) {
            if (link == null) {
                continue;
            }
            RectF wordRect = transform(link.sourceRect);
            boolean intersects = RectF.intersects(wordRect, tapRect);
            if (intersects) {
                return link;
            }
        }

        return null;

    }

    public String selectText(float x1, float y1, float xInit, float yInit) {
        if (getPageText() == null) {
            LOG.d("get pag No page text", pageNumber);
            return null;
        }

        boolean single = Math.abs(x1 - xInit) < MIN && Math.abs(y1 - yInit) < MIN;

        RectF tr = new RectF();
        imageMatrix().mapRect(tr);

        float x = x1 - tr.left;
        float y = y1 - tr.top;

        xInit = xInit - tr.left;
        yInit = yInit - tr.top;

        float[] f = new float[9];
        imageMatrix().getValues(f);

        float scaleX = f[Matrix.MSCALE_X];

        x = x / scaleX;
        y = y / scaleX;

        xInit = xInit / scaleX;
        yInit = yInit / scaleX;

        RectF tapRect = new RectF(xInit, yInit, x, y);
        if (yInit > y) {
            tapRect.sort();
        }

        PageImageState.get().cleanSelectedWords();

        StringBuilder build = new StringBuilder();

        boolean isHyphenWorld = false;
        TextWord prevWord = null;
        for (TextWord line[] : getPageText()) {
            final TextWord current[] = line;
            for (TextWord textWord : current) {
                if (textWord.left <= 0 || textWord.top <= 0) {
                    continue;
                }

                RectF wordRect = transform(textWord);
                if (single) {
                    boolean intersects = RectF.intersects(wordRect, tapRect);
                    if (intersects || isHyphenWorld) {
                        LOG.d("ADD TEXT", textWord);

                        if (prevWord != null && prevWord.w.endsWith("-") && !isHyphenWorld) {
                            build.append(prevWord.w.replace("-", ""));
                            PageImageState.get().addWord(pageNumber, prevWord);
                        }

                        if (!isHyphenWorld) {
                            PageImageState.get().addWord(pageNumber, textWord);
                        }

                        if (isHyphenWorld && TxtUtils.isNotEmpty(textWord.getWord())) {
                            PageImageState.get().addWord(pageNumber, textWord);
                            isHyphenWorld = false;
                        }
                        if (textWord.getWord().endsWith("-")) {
                            isHyphenWorld = true;
                        }
                        build.append(textWord.getWord() + " ");
                    }
                } else {
                    if (y > yInit) {
                        if (wordRect.top < tapRect.top && wordRect.bottom > tapRect.top && wordRect.right > tapRect.left) {
                            PageImageState.get().addWord(pageNumber, textWord);
                            build.append(textWord.getWord() + TxtUtils.space());
                        } else if (wordRect.top < tapRect.bottom && wordRect.bottom > tapRect.bottom && wordRect.left < tapRect.right) {
                            PageImageState.get().addWord(pageNumber, textWord);
                            build.append(textWord.getWord() + TxtUtils.space());
                        } else if (wordRect.top > tapRect.top && wordRect.bottom < tapRect.bottom) {
                            PageImageState.get().addWord(pageNumber, textWord);
                            build.append(textWord.getWord() + TxtUtils.space());
                        }
                    } else if (RectF.intersects(wordRect, tapRect)) {
                        PageImageState.get().addWord(pageNumber, textWord);
                        if (AppState.get().selectingByLetters) {
                            build.append(textWord.w);
                        } else {
                            build.append(textWord.w.trim() + " ");
                        }
                    }
                }

                if (TxtUtils.isNotEmpty(textWord.w)) {
                    prevWord = textWord;
                }

            }
            String k;
            if (AppState.get().selectingByLetters && current.length >= 2 && !(k = current[current.length - 1].getWord()).equals(" ") && !k.equals("-")) {
                build.append(" ");
            }
        }

        String txt = TxtUtils.filterString(build.toString());
        AppState.get().selectedText = txt;
        invalidate();
        return txt;
    }

}
