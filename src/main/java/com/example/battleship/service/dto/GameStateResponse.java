package com.example.battleship.service.dto;

import com.example.battleship.game.Phase;
import java.util.Map;

public record GameStateResponse(
    Phase phase,
    String winner,
    String[][] playerBoard,
    String[][] computerBoard,
    String[][] computerBoardFull, // Full view of computer board (all ships)
    int[][] playerShotTurns, // Turn number for each cell on player board
    int[][] computerShotTurns, // Turn number for each cell on computer board
    int playerTurnNumber, // Current player turn number
    int computerTurnNumber, // Current computer turn number
    Map<Integer, Integer> requiredFleet,
    Map<Integer, Integer> playerPlacedFleet,
    boolean playerFleetComplete
) {
}

