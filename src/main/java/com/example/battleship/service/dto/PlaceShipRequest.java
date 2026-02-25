package com.example.battleship.service.dto;

import com.example.battleship.game.Orientation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class PlaceShipRequest {
  @Min(0) @Max(9)
  public int x;

  @Min(0) @Max(9)
  public int y;

  @Min(1) @Max(4)
  public int length;

  @NotNull
  public Orientation orientation;
}

