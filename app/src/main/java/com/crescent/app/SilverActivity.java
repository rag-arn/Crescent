package com.crescent.app;

/** Step 2: silver. Same screen as gold, with silver fineness instead of karat. */
public class SilverActivity extends MetalActivity {
    @Override protected String metalKey() { return "silver"; }
    @Override protected String metalTitle() { return "Silver"; }
    @Override protected String apiSymbol() { return "XAG"; }
    @Override protected int stepNumber() { return 2; }
    @Override protected String priceBasisText() { return "pure silver"; }
    @Override protected String[] purityNames() { return new String[]{"Pure (999)", "Sterling (925)", "900", "835", "800"}; }
    @Override protected double[] purityFractions() { return new double[]{1.0, 0.925, 0.900, 0.835, 0.800}; }
    @Override protected Class<?> nextActivity() { return CashActivity.class; }
}