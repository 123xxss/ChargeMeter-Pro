package com.dsh.powermeter;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.powermeter.ui.MotionKit;
import com.dsh.powermeter.ui.UiKit;

/** 离线帮助：只放软件真实页面截图 + 文字说明，不再堆重复插画。 */
public final class HelpActivity extends Activity {

    private static final String[][] SECTIONS = {
        {"快速开始", "连接充电器 → 打开瓦镜 Pro → 下拉通知查看实时功率。无需登录，无网络权限。"},
        {"功率口径", "主数值是进入电池的估算功率，不是充电器输入功率。线材、发热和屏幕耗电都会造成差值。"},
        {"双芯 / 单芯", "启动时自动识别电芯数。双芯显示总压/单芯电压，单芯只显示一个电压；设置里可手动覆盖。"},
        {"电压实时", "优先每秒读标准 voltage_now；ROM 阻挡时可用增强读取或 Shizuku，最后才回退系统广播。"},
        {"后台状态栏", "前台服务每秒刷新，服务 watchdog 超时补采样；如果被 ROM 杀后台，需要加电池/自启动白名单。"},
        {"充电历史", "单次充电 ≥30 秒结束后写入本地 SQLite，保存平均/峰值、时长和电量变化，最多 500 条。"},
        {"应用排行", "最近 15 分钟前台应用估算；普通安装无法读取每应用精确能耗。精准模式需要 Shizuku，约 30 秒刷新。"},
        {"权限与隐私", "没有网络权限；使用情况访问和 Shizuku 都是可选项；充电历史只在本机，不导出、不上传。"},
        {"品牌适配", "同一个 APK 自动识别 OPPO/一加/realme、vivo/iQOO、小米/Redmi/POCO，功能一致。"},
        {"状态栏图标", "状态栏小图标只能是单色剪影；下拉展开后的通知大图标才是彩色 App 图标。"},
        {"常见问题", "功率不动：检查增强读取/后台；通知不刷新：检查状态栏开关/白名单；历史为空：需充电 ≥30 秒。"},
        {"动画与界面", "主界面、详情、历史使用同一套节奏；设置 → 动画风格可切换标准/增强/关闭/vivo 原子/Apple 流畅。"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        UiKit.styleSystemBars(this, getColor(R.color.bg), !night);
        buildShell();
    }

    private void buildShell() {
        LinearLayout root = UiKit.column(this);
        root.setBackgroundColor(getColor(R.color.bg));
        LinearLayout header = UiKit.row(this);
        header.setPadding(UiKit.dp(this, 8), UiKit.dp(this, 6),
                UiKit.dp(this, 16), UiKit.dp(this, 6));
        header.addView(UiKit.backButton(this, v -> finish()));
        TextView title = UiKit.text(this, "使用帮助", 20f, R.color.text_primary, true);
        header.addView(title, UiKit.left(this, UiKit.weighted(1f), 2));
        root.addView(header, UiKit.matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(getColor(R.color.bg));
        LinearLayout content = UiKit.column(this);
        content.setPadding(UiKit.dp(this, 20), UiKit.dp(this, 2),
                UiKit.dp(this, 20), UiKit.dp(this, 28));
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        UiKit.applyEdgeToEdgeInsets(this, root);

        int motion = Prefs.get(this).motionStyle();
        TextView intro = UiKit.text(this,
                "只展示软件真实页面和关键说明；图片全部来自瓦镜 Pro 界面。",
                13f, R.color.text_secondary, false);
        intro.setLineSpacing(UiKit.dp(this, 2), 1f);
        content.addView(intro, UiKit.top(this, UiKit.matchWrap(), 2));
        if (motion != 2) MotionKit.fadeUp(intro, 0, UiKit.dp(this, 6), 220, motion);

        addGallery(content, motion);

        for (int i = 0; i < SECTIONS.length; i++) {
            String[] section = SECTIONS[i];
            TextView heading = UiKit.text(this, section[0], 15.5f, R.color.text_primary, true);
            TextView body = UiKit.text(this, section[1], 12.5f, R.color.text_secondary, false);
            body.setLineSpacing(UiKit.dp(this, 2), 1f);
            LinearLayout card = UiKit.card(this);
            card.setPadding(UiKit.dp(this, 15), UiKit.dp(this, 13),
                    UiKit.dp(this, 15), UiKit.dp(this, 13));
            card.addView(heading, UiKit.matchWrap());
            card.addView(body, UiKit.top(this, UiKit.matchWrap(), 5));
            content.addView(card, UiKit.top(this, UiKit.matchWrap(), 9));
            if (motion != 2) MotionKit.fadeUp(card, i * 28, UiKit.dp(this, 8), 230, motion);
        }
    }

    private void addGallery(LinearLayout parent, int motion) {
        TextView title = UiKit.text(this, "软件页面", 15.5f, R.color.text_primary, true);
        parent.addView(title, UiKit.top(this, UiKit.matchWrap(), 14));

        int[] images = {R.drawable.help_ui_home, R.drawable.help_ui_detail, R.drawable.help_ui_usage};
        String[] captions = {"主页：功率、实时数据、曲线", "详情：电压/电流/电芯判定", "应用排行：最近 15 分钟"};
        for (int i = 0; i < images.length; i++) {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.card));
            bg.setCornerRadius(UiKit.dp(this, 18));
            image.setBackground(bg);
            image.setClipToOutline(true);
            setSampledImage(image, images[i], UiKit.dp(this, 360));
            parent.addView(image, UiKit.top(this, UiKit.matchWrap(), 8));
            TextView caption = UiKit.text(this, captions[i], 11.5f, R.color.text_muted, false);
            parent.addView(caption, UiKit.top(this, UiKit.matchWrap(), 4));
            if (motion != 2) MotionKit.fadeUp(image, i * 60, UiKit.dp(this, 8), 240, motion);
        }
    }

    /** 真实截图按显示宽度降采样，避免解码 790×1640 原图占用过多内存。 */
    private void setSampledImage(ImageView view, int resId, int targetPx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeResource(getResources(), resId, bounds);
            int sample = 1;
            int w = Math.max(1, bounds.outWidth);
            while (w / (sample * 2) >= targetPx) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), resId, opts);
            if (bmp != null) view.setImageBitmap(bmp);
        } catch (Throwable ignored) {
        }
    }
}
