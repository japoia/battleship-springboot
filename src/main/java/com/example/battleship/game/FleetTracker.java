package com.example.battleship.game;

import java.util.HashMap;
import java.util.Map;

public final class FleetTracker {
  private final Map<Integer, Integer> placedByLength = new HashMap<>();

  public int placedCount(int length) {
    return placedByLength.getOrDefault(length, 0);
  }

  public Map<Integer, Integer> placedCounts() {
    return Map.copyOf(placedByLength);
  }

  public boolean canPlaceLength(int length) {
    Integer req = FleetRules.requiredCountsByLength().get(length);
    if (req == null) return false;
    return placedCount(length) < req;
  }

  public void onPlaced(int length) {
    placedByLength.put(length, placedCount(length) + 1);
  }

  public boolean isComplete() {
    for (var e : FleetRules.requiredCountsByLength().entrySet()) {
      if (placedCount(e.getKey()) != e.getValue()) return false;
    }
    return true;
  }
}

