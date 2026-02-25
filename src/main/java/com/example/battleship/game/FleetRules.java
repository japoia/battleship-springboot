package com.example.battleship.game;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FleetRules {
  private FleetRules() {}

  /**
   * Standard fleet for this task:
   * 1x length-4, 2x length-3, 3x length-2, 4x length-1.
   */
  public static Map<Integer, Integer> requiredCountsByLength() {
    Map<Integer, Integer> m = new LinkedHashMap<>();
    m.put(4, 1);
    m.put(3, 2);
    m.put(2, 3);
    m.put(1, 4);
    return m;
  }

  public static boolean isAllowedLength(int length) {
    return requiredCountsByLength().containsKey(length);
  }
}

