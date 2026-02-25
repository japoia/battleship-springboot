package com.example.battleship.service.dto;

import com.example.battleship.game.Phase;
import java.util.Map;

public record GameStateResponse(
    Phase phase,
    String winner,
    String[][] playerBoard,
    String[][] computerBoard,
    String[][] computerBoardFull, // Full view of computer board (all ships)
    Map<Integer, Integer> requiredFleet,
    Map<Integer, Integer> playerPlacedFleet,
    boolean playerFleetComplete
) {
}

