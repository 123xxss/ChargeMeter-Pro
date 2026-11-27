package com.dsh.powermeter.qa;

/**
 * 测试入口。纯 main + 断言，不依赖 JUnit / Android 运行时。
 *
 * 输出约定（tests/run_tests.sh 依赖）：
 *   PASS <用例名>
 *   FAIL <用例名> → <期望 / 实际>
 * 末尾打印单元测试汇总，任一失败则进程退出码非 0。
 */
public final class Runner {

    private Runner() {
    }

    public static void main(String[] args) {
        System.out.println("PowerMeter 核心算法测试（纯 JVM：main + 断言，无 JUnit / 无 Android 运行时）");
        System.out.println("运行环境：java " + System.getProperty("java.version")
                + "，classpath 仅含 core + tests 编译产物");

        run("ChargeNormalizer 电流符号归一化（vivo 负电流）", SignNormalizationTests::run);
        run("ChargeNormalizer 双电芯功率修正（67W / 18W）", DualCellTests::run);
        run("ChargeNormalizer 电流单位识别（µA / mA）", UnitDetectionTests::run);
        run("ChargeNormalizer 越界与异常读数过滤", BoundsTests::run);
        run("PowerSmoother 展示值平滑", PowerSmootherTests::run);
        run("PeakTracker 平滑峰值", PeakTrackerTests::run);
        run("ChargeTimeEstimator 充满预估", ChargeTimeEstimatorTests::run);
        run("SessionTracker 会话统计", SessionTrackerTests::run);
        run("AbnormalDetector 异常检测", AbnormalDetectorTests::run);

        System.out.println();
        System.out.println("==== 单元测试结果：通过 " + TestKit.passed()
                + "，失败 " + TestKit.failedCount() + " ====");
        if (!TestKit.failures().isEmpty()) {
            System.out.println("---- 失败明细 ----");
            for (String f : TestKit.failures()) {
                System.out.println("  " + f);
            }
        }
        System.exit(TestKit.failedCount() == 0 ? 0 : 1);
    }

    private static void run(String label, Runnable body) {
        TestKit.section(label);
        try {
            body.run();
        } catch (Throwable t) {
            TestKit.fail(label + "：用例执行未抛异常",
                    t.getClass().getSimpleName() + ": " + t.getMessage());
            t.printStackTrace(System.out);
        }
    }
}
