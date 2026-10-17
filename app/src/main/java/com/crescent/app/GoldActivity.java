package com.crescent.app;

/** Step 1: gold. All behaviour lives in MetalActivity. */
public class GoldActivity extends MetalActivity {
    @Override protected String metalKey() { return "gold"; }
    @Override protected String metalTitle() { return "Gold"; }
    @Override protected String apiSymbol() { return "XAU"; }
    @Override protected int stepNumber() { return 1; }
    @Override protected String priceBasisText() { return "24K gold"; }
    @Override protected String[] purityNames() { return new String[]{"24K", "22K", "21K", "18K", "14K", "10K"}; }
    @Override protected double[] purityFractions() { return new double[]{24 / 24.0, 22 / 24.0, 21 / 24.0, 18 / 24.0, 14 / 24.0, 10 / 24.0}; }
    @Override protected Class<?> nextActivity() { return SilverActivity.class; }
}