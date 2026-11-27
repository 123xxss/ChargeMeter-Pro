package com.dsh.powermeter;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.dsh.powermeter.ui.UiKit;

/** 完全离线的图文使用手册；图片按屏幕宽度降采样，避免大图 OOM。 */
public final class HelpActivity extends Activity {

    private static final class Section {
        final int image;
        final String title;
        final String body;
        Section(int image, String title, String body) {
            this.image = image;
            this.title = title;
            this.body = body;
        }
    }

    private static final Section[] SECTIONS = {
        new Section(R.drawable.help_01, "快速开始",
                "连接充电器 → 打开瓦镜 Pro → 下拉通知查看实时功率。无需登录，无网络权限。"),
        new Section(R.drawable.help_02, "功率口径",
                "主数值是进入电池的估算功率，不是充电器输入功率。线材、机身发热和屏幕耗电都会让入电池功率低于充电器标称。"),
        new Section(R.drawable.help_03, "双芯 / 单芯",
                "启动时自动识别电芯数。双芯显示总压/单芯电压，单芯只显示一个电压；手动设置始终优先。"),
        new Section(R.drawable.help_04, "电压实时",
                "电压优先每秒读标准 voltage_now；ROM 阻挡时可用增强读取或 Shizuku 兜底，都不可读才回退系统广播。"),
        new Section(R.drawable.help_05, "状态栏常驻",
                "前台服务每秒刷新通知；服务 watchdog 在快照超时后补采样。切到别的应用通知应继续更新。"),
        new Section(R.drawable.help_06, "充电历史",
                "每次充电 ≥30 秒结束后写入本地 SQLite，保存平均/峰值功率、时长和电量变化，最多 500 条。"),
        new Section(R.drawable.help_07, "品牌适配",
                "同一个 APK 自动识别 OPPO/一加/realme、vivo/iQOO、小米/Redmi/POCO；功能一致。"),
        new Section(R.drawable.help_08, "权限与隐私",
                "没有网络权限；使用情况访问和 Shizuku 都是可选项；数据只在本机处理。"),
        new Section(R.drawable.help_09, "应用耗电排行",
                "最近 15 分钟前台应用估算；普通安装无法读取系统每应用精确能耗。精准模式需要 Shizuku，约 30 秒刷新。"),
        new Section(R.drawable.help_10, "常见问题",
                "功率不动：检查增强读取/电压节点；通知不刷新：检查状态栏开关和电池白名单；历史为空：单次充电需 ≥30 秒。"),
        new Section(R.drawable.help_11, "功率曲线",
                "最近 2 分钟、每秒一个点；未充电时记录放电功率曲线，打开后会持续滚动。"),
        new Section(R.drawable.help_12, "功率校准",
                "如果长期与功率计有固定偏差，可在设置按倍率校准；不同充电器/线材不要混用同一倍率。"),
        new Section(R.drawable.help_13, "温度 / 系统状态",
                "电池温度来自系统；CPU/GPU/内存/存储优先标准接口，部分机型需要增强读取或 Shizuku。"),
        new Section(R.drawable.help_14, "通知权限",
                "Android 13+ 需要允许通知。通知被关闭时前台服务无法常驻，状态栏开关也会停止。"),
        new Section(R.drawable.help_15, "状态栏开关",
                "顶部铃铛可关闭/恢复状态栏常驻；关闭会停止前台服务，重新打开需从可见界面恢复。"),
        new Section(R.drawable.help_16, "应用排行详情",
                "点击主页应用排行卡片进入详情，可看当前/最近前台应用和估算占比。"),
        new Section(R.drawable.help_17, "精准模式",
                "Shizuku 授权后只读固定 dumpsys batterystats，解析系统估算 mAh；约 30 秒刷新，不是实时。"),
        new Section(R.drawable.help_18, "使用情况权限",
                "应用排行需要“使用情况访问”特殊权限；拒绝不影响功率、电压和历史功能。"),
        new Section(R.drawable.help_19, "Shizuku 增强",
                "只读系统电池节点和部分系统状态；未授权、断连或失败自动回退标准接口，不执行任意 shell。"),
        new Section(R.drawable.help_20, "后台白名单",
                "OPPO/vivo/小米的省电策略可能杀后台：设置 → 后台运行，按品牌把应用设为无限制并允许自启动。"),
        new Section(R.drawable.help_21, "手动电芯数",
                "自动识别不对时，设置 → 电芯数手动覆盖为 1/2/3/4；单芯异常机型不要用双芯默认。"),
        new Section(R.drawable.help_22, "数据隐私",
                "充电历史和设置都在本机；应用排行不读取聊天内容；无网络上传。"),
        new Section(R.drawable.help_23, "历史为空？",
                "历史只在充电结束且时长 ≥30 秒时写入；如果刚插上就拔掉，不会产生记录。"),
        new Section(R.drawable.help_24, "故障排查",
                "先确认版本、通知权限、状态栏开关、电芯数和后台白名单；仍然异常时截图功率/电压/状态。"),
        new Section(R.drawable.help_25, "充电器 / 线材",
                "标称 120W 不代表手机一定跑到 120W；握手协议、线材、温度和电量都会限速。"),
        new Section(R.drawable.help_26, "电池健康",
                "避免长期高温满电；瓦镜只读展示，不修改充电策略，也不承诺电池寿命。"),
        new Section(R.drawable.help_27, "系统状态",
                "本机 CPU、GPU、内存、存储为只读采样；部分厂商节点不可读时显示占位，不伪造。"),
        new Section(R.drawable.help_28, "主题与界面",
                "设置 → 界面风格可选 5 套；动画风格独立；状态栏、详情、历史都跟随主题。"),
        new Section(R.drawable.help_29, "充电速度",
                "功率受协议、线材、温度、电量影响；曲线上看峰值和持续时间，比单看标称更真实。"),
        new Section(R.drawable.help_30, "线材与接口",
                "接触不良会明显掉功率；换线/换充电器对比曲线，可快速判断配件问题。"),
        new Section(R.drawable.help_31, "安全提示",
                "温度异常、功率长期偏低、接口发热时停止充电；应用只提示，不自动断电。"),
        new Section(R.drawable.help_32, "反馈与排障",
                "反馈请带上品牌型号、系统版本、功率/电压截图和设置页；隐私数据请先自行打码。"),
        new Section(R.drawable.help_33, "状态栏图标",
                "状态栏小图标必须是单色剪影，不能显示彩色 App 图标；下拉展开后的通知大图标才是彩色。"),
        new Section(R.drawable.help_34, "历史与备份",
                "充电历史只在本机；彻底卸载会删除历史，覆盖安装会保留。暂不提供云备份。"),
        new Section(R.drawable.help_35, "机型建议",
                "不同 ROM 的 voltage_max/电流单位不同；自动识别不对时按实际电芯数手动覆盖。"),
        new Section(R.drawable.help_36, "版本与更新",
                "覆盖安装会保留设置、历史和签名；跨大版本升级前建议先看发布说明。"),
        new Section(R.drawable.help_37, "隐私边界",
                "不读取聊天内容，不申请网络权限；应用排行只读取本机使用事件的时间与包名。"),
        new Section(R.drawable.help_38, "问题反馈",
                "先确认版本、通知权限、状态栏开关和后台白名单；仍异常时带上截图反馈。"),
        new Section(R.drawable.help_39, "常见术语",
                "净功耗是电压×电流的实时值；入电池功率是进入电池的估算；放电功耗是未充电时电池输出。"),
        new Section(R.drawable.help_40, "安全使用",
                "异常发热、接口发烫或功率长期偏低时停止充电；应用只提示，不自动断电。")
    };

    private Typeface font;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        UiKit.styleSystemBars(this, getColor(R.color.bg), !night);
        try {
            font = getResources().getFont(R.font.wattlens_sans);
        } catch (Throwable ignored) {
            font = null;
        }
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
        if (font != null) title.setTypeface(font, Typeface.BOLD);
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

        TextView intro = UiKit.text(this,
                "完全离线的图文手册。没有网络权限，图片和文字都随安装包提供。",
                13f, R.color.text_secondary, false);
        intro.setLineSpacing(UiKit.dp(this, 2), 1f);
        content.addView(intro, UiKit.top(this, UiKit.matchWrap(), 2));

        for (Section s : SECTIONS) addSection(content, s);
        addThemeGallery(content);
    }

    private void addSection(LinearLayout parent, Section s) {
        TextView heading = UiKit.text(this, s.title, 16f, R.color.text_primary, true);
        if (font != null) heading.setTypeface(font, Typeface.BOLD);
        parent.addView(heading, UiKit.top(this, UiKit.matchWrap(), 14));

        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.card));
        bg.setCornerRadius(UiKit.dp(this, 18));
        image.setBackground(bg);
        image.setClipToOutline(true);
        int target = Math.max(480, getResources().getDisplayMetrics().widthPixels / 2);
        setSampledImage(image, s.image, target);
        LinearLayout.LayoutParams lp = UiKit.top(this, UiKit.matchWrap(), 7);
        parent.addView(image, lp);

        TextView body = UiKit.text(this, s.body, 12.5f, R.color.text_secondary, false);
        if (font != null) body.setTypeface(font, Typeface.NORMAL);
        body.setLineSpacing(UiKit.dp(this, 2), 1f);
        parent.addView(body, UiKit.top(this, UiKit.matchWrap(), 7));
    }

    private void addThemeGallery(LinearLayout parent) {
        TextView heading = UiKit.text(this, "5 套界面风格", 16f, R.color.text_primary, true);
        if (font != null) heading.setTypeface(font, Typeface.BOLD);
        parent.addView(heading, UiKit.top(this, UiKit.matchWrap(), 16));
        TextView sub = UiKit.text(this,
                "实际主题配色以设置 → 界面风格为准；下面是背景预览。",
                12f, R.color.text_secondary, false);
        parent.addView(sub, UiKit.top(this, UiKit.matchWrap(), 3));
        int[] bgs = {R.drawable.theme_bg_0, R.drawable.theme_bg_1, R.drawable.theme_bg_2,
                R.drawable.theme_bg_3, R.drawable.theme_bg_4};
        String[] names = {"Apple 极简", "经典渐变", "深色霓虹", "薄荷", "紫色星云"};
        for (int i = 0; i < bgs.length; i++) {
            LinearLayout row = UiKit.row(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView thumb = new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(getColor(R.color.card));
            bg.setCornerRadius(UiKit.dp(this, 12));
            thumb.setBackground(bg);
            thumb.setClipToOutline(true);
            int px = UiKit.dp(this, 54);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(px, px);
            row.addView(thumb, tlp);
            setSampledImage(thumb, bgs[i], px);
            TextView name = UiKit.text(this, names[i], 13f, R.color.text_primary, true);
            row.addView(name, UiKit.left(this, UiKit.wrapWrap(), 10));
            parent.addView(row, UiKit.top(this, UiKit.matchWrap(), 8));
        }
    }

    /** 大图按目标宽度降采样，避免一次性解码 2400×1500 原图。 */
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
