package com.fc.lens.overlay;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class GlensSession extends VoiceInteractionSession {
    private static final String TAG = "GlensSession";
    private Bitmap screenshotBitmap;

    private static List<TextData> accData = new ArrayList<>();

    public static class TextData {
        public String text;
        public Rect bounds;
        public TextData(String t, Rect b) { text = t; bounds = b; }
    }

    // Data per-baris OCR
    private static class OcrWord {
        String text;
        Rect bounds;
        OcrWord(String t, Rect b) { text = t; bounds = b; }
    }

    public static void sendTextData(String text, Rect bounds) {
        accData.add(new TextData(text, bounds));
    }

    // State global untuk custom selection
    private FrameLayout rootLayout;
    private List<OcrWord> ocrWords = new ArrayList<>();
    private List<View> highlightViews = new ArrayList<>();
    private SelectionOverlayView selectionOverlay;
    private PopupWindow currentPopup;

    public GlensSession(Context context) {
        super(context);
        OcrBridge.INSTANCE.init(context);
    }

    @Override
    public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        Context ctx = getContext();
        FrameLayout root = new FrameLayout(ctx);
        root.setBackgroundColor(Color.parseColor("#CC000000"));

        TextView tv = new TextView(ctx);
        tv.setText("Membaca layar...");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(200, 30, 30, 30));
        bg.setCornerRadius(24f);
        tv.setBackground(bg);

        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-2, -2);
        p.gravity = Gravity.CENTER;
        root.addView(tv, p);
        setContentView(root);
    }

    @Override
    public void onHandleScreenshot(Bitmap screenshot) {
        super.onHandleScreenshot(screenshot);
        if (screenshot != null) {
            this.screenshotBitmap = screenshot;
            Log.d(TAG, "Screenshot: " + screenshot.getWidth() + "x" + screenshot.getHeight());

            OcrBridge.INSTANCE.recognize(screenshot,
                items -> buildUI(items),
                e -> Log.e(TAG, "OCR Gagal", e)
            );
        }
    }

    private int getStatusBarHeight() {
        Resources res = getContext().getResources();
        int id = res.getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? res.getDimensionPixelSize(id) : 0;
    }

    private int getNavBarHeight() {
        Resources res = getContext().getResources();
        int id = res.getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? res.getDimensionPixelSize(id) : 0;
    }

    private int dp(Context ctx, int value) {
        return (int) TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value,
            ctx.getResources().getDisplayMetrics());
    }

    private void buildUI(List<OcrBridge.OcrItem> ocrItems) {
        Context ctx = getContext();
        rootLayout = new FrameLayout(ctx);
        ocrWords.clear();

        int statusH = getStatusBarHeight();
        int navH = getNavBarHeight();
        int ssW = screenshotBitmap.getWidth();
        int ssH = screenshotBitmap.getHeight();

        int calcCropTop = statusH;
        int calcCropHeight = ssH - statusH - navH;
        if (calcCropHeight <= 0) calcCropHeight = ssH;
        if (calcCropTop < 0) calcCropTop = 0;

        final int cropTop = calcCropTop;
        final int cropHeight = calcCropHeight;

        Bitmap cropped = Bitmap.createBitmap(screenshotBitmap, 0, cropTop, ssW, cropHeight);

        ImageView bgImg = new ImageView(ctx);
        bgImg.setImageBitmap(cropped);
        bgImg.setScaleType(ImageView.ScaleType.FIT_XY);
        rootLayout.addView(bgImg, new FrameLayout.LayoutParams(-1, -1));

        // Dark overlay tipis
        View darkOverlay = new View(ctx);
        darkOverlay.setBackgroundColor(Color.argb(60, 0, 0, 0));
        rootLayout.addView(darkOverlay, new FrameLayout.LayoutParams(-1, -1));

        // Hint
        TextView hint = new TextView(ctx);
        hint.setText("Seret jari untuk memilih teks (bebas ke segala arah)");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(13);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(ctx, 20), dp(ctx, 10), dp(ctx, 20), dp(ctx, 10));
        GradientDrawable hintBg = new GradientDrawable();
        hintBg.setColor(Color.argb(180, 0, 0, 0));
        hintBg.setCornerRadius(dp(ctx, 20));
        hint.setBackground(hintBg);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2);
        hp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hp.topMargin = dp(ctx, 40);
        rootLayout.addView(hint, hp);

        // Tombol tutup
        TextView closeBtn = new TextView(ctx);
        closeBtn.setText("✕");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(20);
        closeBtn.setGravity(Gravity.CENTER);
        int closeSize = dp(ctx, 44);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(Color.argb(180, 0, 0, 0));
        closeBg.setCornerRadius(closeSize / 2f);
        closeBg.setShape(GradientDrawable.OVAL);
        closeBtn.setBackground(closeBg);
        closeBtn.setOnClickListener(v -> hide());
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(closeSize, closeSize);
        cp.gravity = Gravity.TOP | Gravity.END;
        cp.topMargin = dp(ctx, 16);
        cp.rightMargin = dp(ctx, 16);
        rootLayout.addView(closeBtn, cp);

        setContentView(rootLayout);

        rootLayout.post(() -> {
            int winW = rootLayout.getWidth();
            int winH = rootLayout.getHeight();
            float scaleX = (float) winW / ssW;
            float scaleY = (float) winH / cropHeight;

            Log.d(TAG, "Window: " + winW + "x" + winH);
            Log.d(TAG, "Scale: " + scaleX + " x " + scaleY);

            // Kumpulkan semua kata ke list ocrWords
            for (OcrBridge.OcrItem item : ocrItems) {
                Rect b = item.getBounds();
                if (b == null) continue;

                int adjTop = b.top - cropTop;
                if (adjTop < 0 || adjTop > cropHeight) continue;

                int sL = (int) (b.left * scaleX);
                int sT = (int) (adjTop * scaleY);
                int sW = (int) (b.width() * scaleX);
                int sH = (int) (b.height() * scaleY);

                if (sW <= 0 || sH <= 0) continue;

                ocrWords.add(new OcrWord(item.getText(), new Rect(sL, sT, sL + sW, sT + sH)));
            }

            // Accessibility data
            for (TextData td : accData) {
                if (td.bounds == null || td.text == null) continue;
                int adjTop = td.bounds.top - cropTop;
                if (adjTop < 0) continue;

                int sL = (int) (td.bounds.left * scaleX);
                int sT = (int) (adjTop * scaleY);
                int sW = (int) (td.bounds.width() * scaleX);
                int sH = (int) (td.bounds.height() * scaleY);
                if (sW <= 0 || sH <= 0) continue;

                ocrWords.add(new OcrWord(td.text, new Rect(sL, sT, sL + sW, sT + sH)));
            }

            accData.clear();
            Log.d(TAG, "Total words: " + ocrWords.size());

            // Overlay untuk gambar selection box
            selectionOverlay = new SelectionOverlayView(ctx);
            rootLayout.addView(selectionOverlay, new FrameLayout.LayoutParams(-1, -1));

            // Setup custom touch handler untuk drag-to-select
            setupDragSelection(ctx);
        });
    }

    /**
     * Custom drag-to-select: bisa ke segala arah (atas, bawah, kiri, kanan, diagonal).
     */
    private void setupDragSelection(Context ctx) {
        selectionOverlay.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    selectionOverlay.startX = event.getX();
                    selectionOverlay.startY = event.getY();
                    selectionOverlay.currentX = event.getX();
                    selectionOverlay.currentY = event.getY();
                    clearSelection();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    selectionOverlay.currentX = event.getX();
                    selectionOverlay.currentY = event.getY();

                    Rect sel = selectionOverlay.getSelectionRect();
                    highlightWordsInRect(sel);

                    selectionOverlay.invalidate();
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    Rect finalSel = selectionOverlay.getSelectionRect();
                    // Hilangkan kotak selection, tapi pertahankan highlight
                    selectionOverlay.startX = selectionOverlay.startY = 0;
                    selectionOverlay.currentX = selectionOverlay.currentY = 0;
                    selectionOverlay.invalidate();

                    // Kalau drag-nya cukup besar, tampilkan menu Copy
                    if (finalSel.width() > 20 || finalSel.height() > 20) {
                        showCopyMenu(ctx, finalSel);
                    } else {
                        clearSelection();
                    }
                    return true;
            }
            return false;
        });
    }

    /**
     * Highlight semua kata yang berpotongan dengan selection rect.
     */
    private void highlightWordsInRect(Rect selectionRect) {
        clearHighlights();

        for (OcrWord word : ocrWords) {
            if (Rect.intersects(selectionRect, word.bounds)) {
                View highlight = new View(getContext());
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(Color.argb(100, 66, 133, 244));
                bg.setCornerRadius(4f);
                highlight.setBackground(bg);

                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    word.bounds.width(), word.bounds.height());
                lp.leftMargin = word.bounds.left;
                lp.topMargin = word.bounds.top;
                rootLayout.addView(highlight, lp);
                highlightViews.add(highlight);
            }
        }
    }

    private void clearHighlights() {
        for (View hv : highlightViews) {
            rootLayout.removeView(hv);
        }
        highlightViews.clear();
    }

    private void clearSelection() {
        clearHighlights();
        if (currentPopup != null && currentPopup.isShowing()) {
            currentPopup.dismiss();
            currentPopup = null;
        }
    }

    /**
     * Munculkan floating menu Copy di atas area selection.
     * Teks di-copy preserving urutan baris & spasi.
     */
    private void showCopyMenu(Context ctx, Rect selectionRect) {
        // Kumpulkan kata yang terpilih
        List<OcrWord> selected = new ArrayList<>();
        for (OcrWord word : ocrWords) {
            if (Rect.intersects(selectionRect, word.bounds)) {
                selected.add(word);
            }
        }

        if (selected.isEmpty()) {
            clearHighlights();
            return;
        }

        // ⭐ Sortir: dari atas ke bawah, kiri ke kanan (preserving urutan baca)
        Collections.sort(selected, (a, b) -> {
            // Group by baris (jika Y center berdekatan = 1 baris)
            int aRow = a.bounds.centerY();
            int bRow = b.bounds.centerY();
            int rowThreshold = Math.max(a.bounds.height(), b.bounds.height()) / 2;

            if (Math.abs(aRow - bRow) > rowThreshold) {
                return Integer.compare(aRow, bRow); // beda baris → urut Y
            }
            return Integer.compare(a.bounds.left, b.bounds.left); // sama baris → urut X
        });

        // ⭐ Gabungkan teks: spasi antar kata dalam 1 baris, newline antar baris
        StringBuilder sb = new StringBuilder();
        int lastRow = -1;
        for (OcrWord word : selected) {
            int currentRow = word.bounds.centerY();
            int rowThreshold = word.bounds.height() / 2;

            if (sb.length() == 0) {
                sb.append(word.text);
            } else if (lastRow >= 0 && Math.abs(currentRow - lastRow) > rowThreshold) {
                // Beda baris → newline
                sb.append("\n").append(word.text);
            } else {
                // Same baris → spasi
                sb.append(" ").append(word.text);
            }
            lastRow = currentRow;
        }

        String textToCopy = sb.toString();

        // Buat popup menu
        currentPopup = new PopupWindow(ctx);

        LinearLayout menuLayout = new LinearLayout(ctx);
        menuLayout.setOrientation(LinearLayout.HORIZONTAL);
        menuLayout.setGravity(Gravity.CENTER);
        int pad = dp(ctx, 4);
        menuLayout.setPadding(pad, pad, pad, pad);

        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(Color.parseColor("#EE202124"));
        menuBg.setCornerRadius(dp(ctx, 24));
        menuLayout.setBackground(menuBg);

        // Tombol Copy
        TextView copyBtn = createMenuButton(ctx, "📋 Salin (" + selected.size() + " kata)");
        copyBtn.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("OCR", textToCopy));
            Toast.makeText(ctx, "Disalin " + textToCopy.length() + " karakter", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Copied: " + textToCopy);
            currentPopup.dismiss();
            currentPopup = null;
            clearSelection();
        });
        menuLayout.addView(copyBtn);

        currentPopup.setContentView(menuLayout);
        currentPopup.setWidth(LinearLayout.LayoutParams.WRAP_CONTENT);
        currentPopup.setHeight(LinearLayout.LayoutParams.WRAP_CONTENT);
        currentPopup.setFocusable(true);
        currentPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        currentPopup.setOutsideTouchable(true);
        currentPopup.setOnDismissListener(() -> {
            clearSelection();
            currentPopup = null;
        });

        // Posisi: di atas area selection
        int popupX = selectionRect.left;
        int popupY = selectionRect.top - dp(ctx, 60);
        if (popupY < 0) {
            popupY = selectionRect.bottom + dp(ctx, 10);
        }

        currentPopup.showAtLocation(rootLayout, Gravity.NO_GRAVITY, popupX, popupY);
    }

    private TextView createMenuButton(Context ctx, String text) {
        TextView btn = new TextView(ctx);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(60, 255, 255, 255));
        bg.setCornerRadius(dp(ctx, 20));
        btn.setBackground(bg);

        return btn;
    }

    /**
     * View transparan untuk menggambar kotak selection saat drag.
     */
    private static class SelectionOverlayView extends View {
        float startX, startY, currentX, currentY;
        private final Paint fillPaint;
        private final Paint strokePaint;

        public SelectionOverlayView(Context context) {
            super(context);
            setWillNotDraw(false);

            fillPaint = new Paint();
            fillPaint.setColor(Color.argb(40, 66, 133, 244));
            fillPaint.setStyle(Paint.Style.FILL);
            fillPaint.setAntiAlias(true);

            strokePaint = new Paint();
            strokePaint.setColor(Color.argb(200, 66, 133, 244));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(3f);
            strokePaint.setAntiAlias(true);
        }

        public Rect getSelectionRect() {
            return new Rect(
                (int) Math.min(startX, currentX),
                (int) Math.min(startY, currentY),
                (int) Math.max(startX, currentX),
                (int) Math.max(startY, currentY)
            );
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (startX != currentX || startY != currentY) {
                Rect r = getSelectionRect();
                canvas.drawRect(r, fillPaint);
                canvas.drawRect(r, strokePaint);
            }
        }
    }
}
