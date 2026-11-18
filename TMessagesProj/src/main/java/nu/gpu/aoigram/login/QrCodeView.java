package nu.gpu.aoigram.login;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.view.View;

import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.TelegramQRCodeWriter;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;

import java.util.HashMap;

public class QrCodeView extends View {

    private static final int MODULES = 37;
    private static final int PADDING = 16;
    private static final float RADIUS_FACTOR = 0.75f;
    private static final int SWEEP_HEIGHT = 140;

    private final int multiple = dp(260) / MODULES;
    private final int size = multiple * MODULES + PADDING * 2;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint oldMask = createMask(0, Color.WHITE);
    private final Paint newMask = createMask(Color.WHITE, 0);
    private final float[] radii = new float[8];
    private final AnimatedFloat progress = new AnimatedFloat(1f, this, 0, 2000, CubicBezierInterpolator.EASE_OUT_QUINT);

    private String link;
    private Bitmap bitmap, oldBitmap;
    private RLottieDrawable loading;
    private Bitmap logo;

    public QrCodeView(Context context) {
        super(context);
    }

    public void setLink(String link) {
        this.link = link;
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap encoded = encode(link);
            AndroidUtilities.runOnUIThread(() -> {
                if (link.equals(this.link)) {
                    setBitmap(encoded);
                } else if (encoded != null) {
                    encoded.recycle();
                }
            });
        });
    }

    public void clear() {
        link = null;
        recycle(oldBitmap);
        recycle(bitmap);
        bitmap = oldBitmap = null;
        progress.set(1f, true);
        invalidate();
    }

    private Bitmap encode(String link) {
        HashMap<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 0);
        try {
            return new TelegramQRCodeWriter().encode(link, size, size, hints, null, RADIUS_FACTOR, Color.TRANSPARENT, Color.BLACK);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void setBitmap(Bitmap value) {
        recycle(oldBitmap);
        oldBitmap = bitmap;
        bitmap = value;
        progress.set(0f, true);
        invalidate();
    }

    private static void recycle(Bitmap b) {
        if (b != null) {
            b.recycle();
        }
    }

    private static Paint createMask(int from, int to) {
        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setShader(new LinearGradient(0, 0, 0, dp(SWEEP_HEIGHT), from, to, Shader.TileMode.CLAMP));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        return mask;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int side = Math.min(dp(260), MeasureSpec.getSize(widthMeasureSpec));
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), dp(12), dp(12), paint);
        float p = progress.set(1f);
        if (p < 1f) {
            drawContent(canvas, oldBitmap, p, oldMask);
        }
        if (p > 0f) {
            drawContent(canvas, bitmap, p, newMask);
        }
    }

    private void drawContent(Canvas canvas, Bitmap content, float p, Paint mask) {
        boolean sweeping = p > 0f && p < 1f;
        if (sweeping) {
            canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        }
        canvas.save();
        canvas.scale(getWidth() / (float) size, getHeight() / (float) size);
        if (content != null) {
            AndroidUtilities.rectTmp.set(0, 0, size, size);
            canvas.drawBitmap(content, null, AndroidUtilities.rectTmp, paint);
        } else {
            drawLoading(canvas);
        }
        canvas.restore();
        if (sweeping) {
            float h = dp(SWEEP_HEIGHT);
            canvas.translate(0, -h + (getHeight() + h) * (1f - p));
            canvas.drawRect(0, -h - getHeight(), getWidth(), getHeight() + h, mask);
            canvas.restore();
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private void drawLoading(Canvas canvas) {
        if (loading == null) {
            loading = new RLottieDrawable(R.raw.qr_matrix, dp(200), dp(200));
            loading.setMasterParent(this);
            loading.setAutoRepeat(1);
            loading.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY));
            loading.start();
        }
        loading.setBounds(PADDING, PADDING, size - PADDING, size - PADDING);
        loading.draw(canvas);

        paint.setColor(Color.BLACK);
        TelegramQRCodeWriter.drawSideQuads(canvas, 0, 0, paint, 7, multiple, PADDING, size, RADIUS_FACTOR, radii, true);

        if (logo == null) {
            int logoBlocks = Math.round((size - PADDING * 2) / 4.65f / multiple);
            if (logoBlocks % 2 != MODULES % 2) {
                logoBlocks++;
            }
            int logoSize = logoBlocks * multiple - 24;
            logo = SvgHelper.getBitmap(AndroidUtilities.readRes(R.raw.qr_logo), logoSize, logoSize, false);
        }
        int logoOffset = (size - logo.getWidth()) / 2;
        canvas.drawBitmap(logo, logoOffset, logoOffset, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (loading != null) {
            loading.recycle(false);
            loading = null;
        }
        recycle(logo);
        logo = null;
    }
}
