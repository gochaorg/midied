package xyz.cofe.mitrenier.music;

import xyz.cofe.mitrenier.math.IntervalManager;

public record ClickIntervalManager(int channel, int note, IntervalManager<ClickInterval> iman) {}
