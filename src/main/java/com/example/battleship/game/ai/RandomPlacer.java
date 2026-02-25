package com.example.battleship.game.ai;

import com.example.battleship.game.Board;
import com.example.battleship.game.FleetRules;
import com.example.battleship.game.Orientation;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public final class RandomPlacer {
  private final SecureRandom rnd = new SecureRandom();

  public boolean tryPlaceFullFleet(Board board) {
    // We can only place on an empty board reliably.
    if (board.shipCount() != 0) return false;

    for (var e : FleetRules.requiredCountsByLength().entrySet()) {
      int length = e.getKey();
      int count = e.getValue();
      for (int i = 0; i < count; i++) {
        if (!tryPlaceOne(board, length, 4000)) return false;
      }
    }
    return true;
  }

  public boolean tryPlaceOne(Board board, int length, int maxAttempts) {
    List<Orientation> os = new ArrayList<>(List.of(Orientation.H, Orientation.V));
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      Orientation o = os.get(rnd.nextInt(os.size()));
      int x = rnd.nextInt(Board.SIZE);
      int y = rnd.nextInt(Board.SIZE);
      if (board.placeShip(x, y, length, o)) return true;
    }
    return false;
  }
}

