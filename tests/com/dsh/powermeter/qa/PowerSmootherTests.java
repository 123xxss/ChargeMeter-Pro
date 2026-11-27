package com.dsh.powermeter.qa;

import com.dsh.powermeter.core.PowerSmoother;

/**
 * PRD 要求：展示功率平滑，插拔瞬间不闪烁跳变，首帧不出现从 0 缓慢爬升。
 */
final class PowerSmootherTests {

    private PowerSmootherTests() {
    }

    static void run() {
        firstFrameShowsFullValue();
        singleSpikeDoesNotJump();
        twoSampleSpikeDoesNotJump();
        invalidInputIgnored();
        currentSignPreserved();
        voltageIgnoresNonPositive();
        followsRealStepSmoothly();
        resetClearsState();
    }

    /** 首帧直接显示真实值，不爬升。 */
    private static void firstFrameShowsFullValue() {
        PowerSmoother s = new PowerSmoother();
        double out = s.addPower(45.0);
        TestKit.eqDouble("首帧 addPower(45) 立即显示 45（不从 0 爬升）", 45.0, out, 1e-9);
        TestKit.eqDouble("首帧后 power()=45", 45.0, s.power(), 1e-9);
        TestKit.isTrue("首帧后 hasPower=true", s.hasPower());

        PowerSmoother s2 = new PowerSmoother();
        double first = s2.addPower(66.0);
        TestKit.eqDouble("首帧 addPower(66) 立即显示 66", 66.0, first, 1e-9);
    }

    /** 稳态 45W 中插拔瞬间出现 1 个 0 → 展示值不应跳变。 */
    private static void singleSpikeDoesNotJump() {
        PowerSmoother s = new PowerSmoother();
        for (int i = 0; i < 5; i++) s.addPower(45.0);
        TestKit.eqDouble("稳态 5 帧 → 45W", 45.0, s.power(), 1e-9);

        double afterZero = s.addPower(0.0);
        TestKit.eqDouble("稳态中单点 0W（拔插瞬间）→ 展示值不跳变", 45.0, afterZero, 0.001);

        double afterSpike = s.addPower(200.0);
        TestKit.eqDouble("稳态中单点 200W 尖峰 → 展示值不跳变", 45.0, afterSpike, 0.001);
    }

    /** 连续两次异常也不应立刻跳变（5 窗口中位数仍为稳态值）。 */
    private static void twoSampleSpikeDoesNotJump() {
        PowerSmoother s = new PowerSmoother();
        for (int i = 0; i < 5; i++) s.addPower(45.0);

        s.addPower(0.0);
        double out = s.addPower(0.0);
        TestKit.eqDouble("连续 2 个 0W → 展示值仍为 45W", 45.0, out, 0.001);

        PowerSmoother s2 = new PowerSmoother();
        for (int i = 0; i < 5; i++) s2.addPower(45.0);
        s2.addPower(200.0);
        double out2 = s2.addPower(200.0);
        TestKit.eqDouble("连续 2 个 200W 尖峰 → 展示值仍为 45W", 45.0, out2, 0.001);
    }

    /** NaN / 负数输入必须被忽略，不污染窗口。 */
    private static void invalidInputIgnored() {
        PowerSmoother s = new PowerSmoother();
        s.addPower(50.0);

        TestKit.eqDouble("负功率输入被忽略 → 保持 50", 50.0, s.addPower(-5.0), 1e-9);
        TestKit.eqDouble("NaN 输入被忽略 → 保持 50", 50.0, s.addPower(Double.NaN), 1e-9);
        TestKit.eqDouble("Infinity 输入被忽略 → 保持 50",
                50.0, s.addPower(Double.POSITIVE_INFINITY), 1e-9);

        PowerSmoother c = new PowerSmoother();
        c.addCurrent(2.0);
        TestKit.eqDouble("电流 NaN 输入被忽略 → 保持 2.0", 2.0, c.addCurrent(Double.NaN), 1e-9);
    }

    /** 电流平滑保留符号（放电为负），否则界面会显示成正功率。 */
    private static void currentSignPreserved() {
        PowerSmoother s = new PowerSmoother();
        double out = s.addCurrent(-2.0);
        TestKit.eqDouble("首帧 addCurrent(-2.0) → -2.0（符号保留）", -2.0, out, 1e-9);
        TestKit.eqDouble("再输入 -2.0 → 仍为 -2.0", -2.0, s.addCurrent(-2.0), 1e-9);

        PowerSmoother p = new PowerSmoother();
        p.addCurrent(3.0);
        TestKit.eqDouble("正电流平滑 → +3.0", 3.0, p.current(), 1e-9);
    }

    /** 电压 <= 0 的输入必须忽略。 */
    private static void voltageIgnoresNonPositive() {
        PowerSmoother s = new PowerSmoother();
        TestKit.eqDouble("首帧 addVoltage(4.3) → 4.3", 4.3, s.addVoltage(4.3), 1e-9);
        TestKit.eqDouble("addVoltage(0) 被忽略 → 保持 4.3", 4.3, s.addVoltage(0.0), 1e-9);
        TestKit.eqDouble("addVoltage(-1) 被忽略 → 保持 4.3", 4.3, s.addVoltage(-1.0), 1e-9);
        TestKit.eqDouble("电压输出无 0 值污染", 4.3, s.voltage(), 1e-9);
    }

    /** 真实功率阶跃（45→60）→ 平滑跟随且不过冲。 */
    private static void followsRealStepSmoothly() {
        PowerSmoother s = new PowerSmoother();
        for (int i = 0; i < 5; i++) s.addPower(45.0);
        for (int i = 0; i < 20; i++) s.addPower(60.0);
        TestKit.range("真实阶跃 45→60 → 20 帧后收敛到 60", 59.9, 60.0, s.power());

        PowerSmoother t = new PowerSmoother();
        for (int i = 0; i < 5; i++) t.addPower(60.0);
        double afterOne = t.addPower(45.0);
        TestKit.eqDouble("真实下降 60→45 → 第 1 帧不会立刻砸到 45", 60.0, afterOne, 1e-9);
        t.addPower(45.0);
        double afterThree = t.addPower(45.0);
        TestKit.eqDouble("真实下降 60→45 → 第 3 帧开始跟随（60→52.5）",
                52.5, afterThree, 1e-6);
        TestKit.lt("真实下降 60→45 → 已开始跟随下降（< 60）", afterThree, 60.0);
    }

    /** reset 后重新起步，不残留历史窗口。 */
    private static void resetClearsState() {
        PowerSmoother s = new PowerSmoother();
        for (int i = 0; i < 5; i++) s.addPower(45.0);
        s.addVoltage(4.3);
        s.addCurrent(2.0);

        s.reset();
        TestKit.isFalse("reset 后 hasPower=false", s.hasPower());
        TestKit.eqDouble("reset 后 power()=0", 0.0, s.power(), 1e-9);
        TestKit.eqDouble("reset 后 voltage()=0", 0.0, s.voltage(), 1e-9);
        TestKit.eqDouble("reset 后 current()=0", 0.0, s.current(), 1e-9);
        TestKit.eqDouble("reset 后首帧仍直接显示 20", 20.0, s.addPower(20.0), 1e-9);
    }
}
