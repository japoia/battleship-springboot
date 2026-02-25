package com.example.battleship.game;

public record FireResult(
    Outcome outcome,
    boolean shipSunk,
    boolean allShipsSunk
) {
  public enum Outcome {
    INVALID,
    ALREADY_SHOT,
    MISS,
    HIT
  }
}

