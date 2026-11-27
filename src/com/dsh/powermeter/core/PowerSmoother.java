package com.dsh.powermeter.core;

/**
 * 展示值平滑：短窗口中位数去尖峰 + 指数平滑去抖。
 * 插拔瞬间不会闪烁跳变；首帧直接显示，不出现从 0 缓慢爬升的问题。
 */
public final class PowerSmoother {

    private final double[] powerWindow = new double[5];
    private final double[] currentWindow = new double[5];
    private final double[] voltageWindow = new double[3];
    private int powerCount;
    private int currentCount;
    private int voltageCount;

    private double powerOut;
    private double currentOut;
    private double voltageOut;
    private boolean powerInit;
    private boolean currentInit;
    private boolean voltageInit;

    public double addPower(double watts) {
        if (!Double.isFinite(watts) || watts < 0) return powerOut;
        powerCount = push(powerWindow, powerCount, watts);
        double median = median(powerWindow, powerCount);
        if (!powerInit) {
            powerOut = median;
            powerInit = true;
        } else {
            powerOut += 0.5 * (median - powerOut);
        }
        return powerOut;
    }

    public double addCurrent(double amps) {
        if (!Double.isFinite(amps)) return currentOut;
        currentCount = push(currentWindow, currentCount, amps);
        double median = median(currentWindow, currentCount);
        if (!currentInit) {
            currentOut = median;
            currentInit = true;
        } else {
            currentOut += 0.5 * (median - currentOut);
        }
        return currentOut;
    }

    public double addVoltage(double volts) {
        if (!Double.isFinite(volts) || volts <= 0) return voltageOut;
        voltageCount = push(voltageWindow, voltageCount, volts);
        double median = median(voltageWindow, voltageCount);
        if (!voltageInit) {
            voltageOut = median;
            voltageInit = true;
        } else {
            voltageOut += 0.4 * (median - voltageOut);
        }
        return voltageOut;
    }

    public double power() {
        return powerOut;
    }

    public double current() {
        return currentOut;
    }

    public double voltage() {
        return voltageOut;
    }

    public boolean hasPower() {
        return powerInit;
    }

    public void reset() {
        java.util.Arrays.fill(powerWindow, 0);
        java.util.Arrays.fill(currentWindow, 0);
        java.util.Arrays.fill(voltageWindow, 0);
        powerCount = currentCount = voltageCount = 0;
        powerOut = currentOut = voltageOut = 0;
        powerInit = currentInit = voltageInit = false;
    }

    private static int push(double[] window, int count, double value) {
        if (count < window.length) {
            window[count++] = value;
        } else {
            System.arraycopy(window, 1, window, 0, window.length - 1);
            window[window.length - 1] = value;
        }
        return count;
    }

    private static double median(double[] window, int count) {
        if (count <= 0) return 0;
        if (count == 1) return window[0];
        double[] copy = new double[count];
        System.arraycopy(window, 0, copy, 0, count);
        java.util.Arrays.sort(copy);
        if ((count & 1) == 1) return copy[count / 2];
        return (copy[count / 2 - 1] + copy[count / 2]) / 2.0;
    }
}
