package com.example.battleship.service.dto;

import com.example.battleship.game.FireResult;

public record FireResponse(
    FireResult playerShot,
    int computerShotX,
    int computerShotY,
    FireResult computerShot,
    GameStateResponse state
) {
}

