package com.example.battleship.service.dto;

import com.example.battleship.game.FireResult;
import com.example.battleship.game.Coord;
import java.util.List;

public record FireResponse(
    FireResult playerShot,
    List<ComputerShot> computerShots,
    GameStateResponse state
) {
    public record ComputerShot(int x, int y, FireResult result) {
    }
}

